package com.eliteessentials.listeners;

import com.eliteessentials.EliteEssentials;
import com.eliteessentials.config.ConfigManager;
import com.eliteessentials.config.PluginConfig;
import com.eliteessentials.services.PlayerService;
import com.eliteessentials.services.PlayTimeRewardService;
import com.eliteessentials.storage.MotdStorage;
import com.eliteessentials.storage.PlayerFileStorage;
import com.eliteessentials.util.MessageFormatter;
import com.hypixel.hytale.component.Holder;
import com.hypixel.hytale.event.EventRegistry;
import com.hypixel.hytale.protocol.UpdateType;
import com.hypixel.hytale.protocol.packets.assets.UpdateTranslations;
import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.event.events.player.PlayerReadyEvent;
import com.hypixel.hytale.server.core.event.events.player.PlayerDisconnectEvent;
import com.hypixel.hytale.server.core.event.events.player.AddPlayerToWorldEvent;
import com.hypixel.hytale.server.core.event.events.player.DrainPlayerFromWorldEvent;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.Universe;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;

import java.io.File;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.logging.Logger;

/**
 * Handles player join/quit events.
 * - Join messages (server join only, not world changes)
 * - First join messages (broadcast to everyone)
 * - Quit messages (server disconnect only, not world changes)
 * - World change messages (optional)
 * - MOTD display on join (server join only, not world changes)
 * - Per-world MOTD (optional, can show always or once per session)
 * - Suppression of default Hytale join/leave messages
 * - Player cache updates (via PlayerService)
 */
public class JoinQuitListener {
    
    private static final Logger logger = Logger.getLogger("EliteEssentials");
    
    // Translation keys for default Hytale join/leave messages
    // By overriding these, we can replace the default messages with our own
    private static final String TRANSLATION_KEY_JOINED_WORLD = "server.general.playerJoinedWorld";
    private static final String TRANSLATION_KEY_LEFT_WORLD = "server.general.playerLeftWorld";
    
    private final ConfigManager configManager;
    private final MotdStorage motdStorage;
    private final PlayerService playerService;
    private final ScheduledExecutorService scheduler;
    private PlayerFileStorage playerFileStorage;
    private com.eliteessentials.services.VanishService vanishService;
    
    // Track players currently on the server to differentiate world changes from joins/quits
    private final Set<UUID> onlinePlayers = ConcurrentHashMap.newKeySet();
    // Track players who are changing worlds (drain then add in quick succession)
    private final Set<UUID> worldChangingPlayers = ConcurrentHashMap.newKeySet();
    // Track which worlds each player has seen MOTD for this session (for showAlways=false)
    private final ConcurrentHashMap<UUID, Set<String>> seenWorldMotds = new ConcurrentHashMap<>();
    // Track the last world each player was in (to detect world changes when DrainEvent doesn't fire)
    private final ConcurrentHashMap<UUID, String> playerLastWorld = new ConcurrentHashMap<>();
    
    public JoinQuitListener(ConfigManager configManager, MotdStorage motdStorage, PlayerService playerService) {
        this.configManager = configManager;
        this.motdStorage = motdStorage;
        this.playerService = playerService;
        this.scheduler = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "EliteEssentials-JoinQuit");
            t.setDaemon(true);
            return t;
        });
    }
    
    /**
     * Set the player file storage (called after initialization).
     */
    public void setPlayerFileStorage(PlayerFileStorage storage) {
        this.playerFileStorage = storage;
    }
    
    /**
     * Set the vanish service (called after initialization).
     */
    public void setVanishService(com.eliteessentials.services.VanishService service) {
        this.vanishService = service;
    }
    
    /**
     * Register event listeners.
     */
    public void registerEvents(EventRegistry eventRegistry) {
        PluginConfig config = configManager.getConfig();
        
        // Use PlayerReadyEvent for initial server join - fires when player is fully loaded
        eventRegistry.registerGlobal(PlayerReadyEvent.class, event -> {
            onPlayerJoin(event);
        });
        
        // Register quit event for player cache and quit messages
        eventRegistry.registerGlobal(PlayerDisconnectEvent.class, event -> {
            onPlayerQuit(event);
        });
        
        // Handle world changes - DrainPlayerFromWorldEvent fires when leaving a world
        eventRegistry.registerGlobal(DrainPlayerFromWorldEvent.class, event -> {
            onPlayerDrainFromWorld(event);
        });
        
        // Suppress default join messages and handle world change joins
        // Uses AddPlayerToWorldEvent.setBroadcastJoinMessage(false) to prevent
        // the built-in "player has joined default" message
        eventRegistry.registerGlobal(AddPlayerToWorldEvent.class, event -> {
            onPlayerAddToWorld(event);
        });
    }
    
    /**
     * Handle player being drained from a world (leaving world).
     * This fires for both world changes AND disconnects.
     * We mark the player as "world changing" - if they don't disconnect shortly after,
     * they're just changing worlds.
     */
    private void onPlayerDrainFromWorld(DrainPlayerFromWorldEvent event) {
        Holder<EntityStore> holder = event.getHolder();
        if (holder == null) {
            return;
        }
        
        PlayerRef playerRef = holder.getComponent(PlayerRef.getComponentType());
        if (playerRef == null) {
            return;
        }
        
        UUID playerId = playerRef.getUuid();
        String playerName = playerRef.getUsername();
        String worldName = event.getWorld() != null ? event.getWorld().getName() : "unknown";
        
        // Mark as potentially changing worlds
        worldChangingPlayers.add(playerId);
        
        // Broadcast world leave message if enabled
        PluginConfig config = configManager.getConfig();
        if (config.joinMsg.worldChangeEnabled && onlinePlayers.contains(playerId)) {
            String message = configManager.getMessage("worldLeaveMessage", "player", playerName, "world", worldName);
            if (message != null && !message.isEmpty()) {
                broadcastMessage(message, "#AAAAAA");
            }
        }
    }
    
    /**
     * Handle player being added to a world.
     * This fires for both initial joins AND world changes.
     */
    private void onPlayerAddToWorld(AddPlayerToWorldEvent event) {
        PluginConfig config = configManager.getConfig();
        
        // Always suppress default Hytale join messages if configured
        if (config.joinMsg.suppressDefaultMessages) {
            event.setBroadcastJoinMessage(false);
        }
        
        Holder<EntityStore> holder = event.getHolder();
        if (holder == null) {
            return;
        }
        
        PlayerRef playerRef = holder.getComponent(PlayerRef.getComponentType());
        if (playerRef == null) {
            return;
        }
        
        UUID playerId = playerRef.getUuid();
        String playerName = playerRef.getUsername();
        String worldName = event.getWorld() != null ? event.getWorld().getName() : "unknown";
        
        // Check if this is a world change using multiple methods:
        // 1. DrainPlayerFromWorldEvent set the flag (preferred)
        // 2. Player is online and entering a different world than they were in
        boolean isWorldChange = worldChangingPlayers.remove(playerId);
        
        // Fallback: If drain event didn't fire, check if player is online and world changed
        if (!isWorldChange && onlinePlayers.contains(playerId)) {
            String lastWorld = playerLastWorld.get(playerId);
            if (lastWorld != null && !lastWorld.equalsIgnoreCase(worldName)) {
                isWorldChange = true;
            }
        }
        
        // Update last world tracking
        playerLastWorld.put(playerId, worldName);
        
        // If player was draining (world change), show world MOTD and broadcast
        if (isWorldChange) {
            // Broadcast world join message if enabled
            if (config.joinMsg.worldChangeEnabled) {
                String message = configManager.getMessage("worldJoinMessage", "player", playerName, "world", worldName);
                if (message != null && !message.isEmpty()) {
                    broadcastMessage(message, "#AAAAAA");
                }
            }
            
            // Show world-specific MOTD if configured
            showWorldMotd(playerRef, worldName);
        }
    }
    
    /**
     * Handle player join event (initial server join).
     * Uses world.execute() to ensure thread safety when accessing store components.
     */
    private void onPlayerJoin(PlayerReadyEvent event) {
        var ref = event.getPlayerRef();
        if (!ref.isValid()) {
            return;
        }
        
        var store = ref.getStore();
        
        // Get world for thread-safe execution
        EntityStore entityStore = store.getExternalData();
        World world = entityStore.getWorld();
        
        // Execute on world thread to ensure thread safety
        world.execute(() -> {
            if (!ref.isValid()) {
                return;
            }
            
            PlayerRef playerRef = store.getComponent(ref, PlayerRef.getComponentType());
            if (playerRef == null) {
                return;
            }
            
            UUID playerId = playerRef.getUuid();
            String playerName = playerRef.getUsername();
            String worldName = world.getName();
            PluginConfig config = configManager.getConfig();
            
            // Check if already online (prevents duplicate join messages on world change)
            if (onlinePlayers.contains(playerId)) {
                return;
            }
            
            // Override default Hytale join/leave translations with our custom messages
            // This replaces the built-in "player has joined/left world" with our configured messages
            if (config.joinMsg.suppressDefaultMessages) {
                sendTranslationOverrides(playerRef);
            }
            
            // Track as online - this is a real server join
            onlinePlayers.add(playerId);
            // Clear any world change flag
            worldChangingPlayers.remove(playerId);
            // Clear seen world MOTDs for new session
            seenWorldMotds.remove(playerId);
            // Set initial world for world change detection
            playerLastWorld.put(playerId, worldName);
            
            // Update player cache
            playerService.onPlayerJoin(playerId, playerName);
            
            // Hide vanished players from this joining player
            if (vanishService != null) {
                vanishService.onPlayerJoin(playerRef);
                // Also set up map filter to hide vanished players from the map
                vanishService.onPlayerReady(store, ref);
            }
            
            // Notify playtime reward service
            PlayTimeRewardService rewardService = EliteEssentials.getInstance().getPlayTimeRewardService();
            if (rewardService != null) {
                rewardService.onPlayerJoin(playerId);
            }
            
            // Check if first join by checking if player file existed on disk before this session
            // We check the file directly because playerService.onPlayerJoin() just created it
            boolean isFirstJoin = false;
            if (playerFileStorage != null) {
                File playerFile = new File(playerFileStorage.getPlayersFolder(), playerId.toString() + ".json");
                // If file was just created (by onPlayerJoin above), check if it's very new
                // Actually, we need to check BEFORE onPlayerJoin creates it - but that already ran
                // So we check if the player's firstJoin timestamp is within the last few seconds
                var playerData = playerFileStorage.getPlayer(playerId);
                if (playerData != null) {
                    long firstJoinTime = playerData.getFirstJoin();
                    long now = System.currentTimeMillis();
                    // If firstJoin was set within the last 5 seconds, this is a new player
                    isFirstJoin = (now - firstJoinTime) < 5000;
                }
            }
            
            if (isFirstJoin) {
                // Broadcast first join message
                if (config.joinMsg.firstJoinEnabled) {
                    String message = configManager.getMessage("firstJoinMessage", "player", playerName);
                    broadcastMessage(message, "#FFFF55");
                }
            } else {
                // Regular join message
                if (config.joinMsg.joinEnabled) {
                    String message = configManager.getMessage("joinMessage", "player", playerName);
                    broadcastMessage(message, "#55FF55");
                }
            }
            
            // Show global MOTD (only on server join, not world changes)
            if (config.motd.enabled && config.motd.showOnJoin) {
                int delay = config.motd.delaySeconds;
                if (delay > 0) {
                    // Schedule MOTD display after delay
                    scheduleGlobalMotd(playerRef, delay, worldName);
                } else {
                    // Show immediately
                    showGlobalMotd(playerRef, worldName);
                }
            }
            
            // Also show world-specific MOTD for the initial world
            showWorldMotd(playerRef, worldName);
        });
    }
    
    /**
     * Handle player quit event (actual server disconnect).
     * Updates player cache with last seen time and play time.
     */
    private void onPlayerQuit(PlayerDisconnectEvent event) {
        PlayerRef playerRef = event.getPlayerRef();
        if (playerRef == null) {
            return;
        }
        
        UUID playerId = playerRef.getUuid();
        String playerName = playerRef.getUsername();
        
        // Remove from online tracking
        onlinePlayers.remove(playerId);
        worldChangingPlayers.remove(playerId);
        // Clear seen world MOTDs when player disconnects
        seenWorldMotds.remove(playerId);
        // Clear last world tracking
        playerLastWorld.remove(playerId);
        
        // Notify playtime reward service before updating player cache
        PlayTimeRewardService rewardService = EliteEssentials.getInstance().getPlayTimeRewardService();
        if (rewardService != null) {
            rewardService.onPlayerQuit(playerId);
        }
        
        // Update player cache (last seen, play time)
        playerService.onPlayerQuit(playerId);
        
        // Clean up vanish state
        if (vanishService != null) {
            vanishService.onPlayerLeave(playerId);
        }
        
        // Broadcast quit message if enabled
        PluginConfig config = configManager.getConfig();
        if (config.joinMsg.quitEnabled) {
            String message = configManager.getMessage("quitMessage", "player", playerName);
            broadcastMessage(message, "#FF5555");
        }
    }
    
    /**
     * Show global MOTD to player (server join MOTD).
     */
    private void showGlobalMotd(PlayerRef playerRef, String worldName) {
        PluginConfig config = configManager.getConfig();
        
        // Get MOTD lines
        List<String> motdLines = motdStorage.getMotdLines();
        if (motdLines.isEmpty()) {
            return;
        }
        
        // Replace placeholders
        String playerName = playerRef.getUsername();
        String serverName = config.motd.serverName;
        int playerCount = Universe.get().getPlayers().size();
        
        // Send each line with formatting
        for (String line : motdLines) {
            // Skip completely empty lines to avoid excessive spacing
            if (line.trim().isEmpty()) {
                continue;
            }
            
            String processedLine = line
                    .replace("{player}", playerName)
                    .replace("{server}", serverName)
                    .replace("{world}", worldName)
                    .replace("{playercount}", String.valueOf(playerCount));
            
            playerRef.sendMessage(MessageFormatter.format(processedLine));
        }
    }
    
    /**
     * Show world-specific MOTD to player.
     * Respects showAlways setting - if false, only shows once per session.
     */
    private void showWorldMotd(PlayerRef playerRef, String worldName) {
        if (playerRef == null || worldName == null) return;
        
        // Check if world has a configured MOTD
        MotdStorage.WorldMotd worldMotd = motdStorage.getWorldMotd(worldName);
        if (worldMotd == null || !worldMotd.enabled || worldMotd.lines == null || worldMotd.lines.isEmpty()) {
            return;
        }
        
        UUID playerId = playerRef.getUuid();
        
        // Check if we should show this MOTD
        if (!worldMotd.showAlways) {
            // Only show once per session - check if player has already seen it
            Set<String> seenWorlds = seenWorldMotds.computeIfAbsent(playerId, k -> ConcurrentHashMap.newKeySet());
            if (seenWorlds.contains(worldName.toLowerCase())) {
                return; // Already seen this world's MOTD this session
            }
            seenWorlds.add(worldName.toLowerCase());
        }
        
        // Replace placeholders and send
        PluginConfig config = configManager.getConfig();
        String playerName = playerRef.getUsername();
        String serverName = config.motd.serverName;
        int playerCount = Universe.get().getPlayers().size();
        
        for (String line : worldMotd.lines) {
            if (line.trim().isEmpty()) {
                continue;
            }
            
            String processedLine = line
                    .replace("{player}", playerName)
                    .replace("{server}", serverName)
                    .replace("{world}", worldName)
                    .replace("{playercount}", String.valueOf(playerCount));
            
            playerRef.sendMessage(MessageFormatter.format(processedLine));
        }
    }
    
    /**
     * Schedule global MOTD display after delay.
     */
    private void scheduleGlobalMotd(PlayerRef playerRef, int delaySeconds, String worldName) {
        scheduler.schedule(() -> showGlobalMotd(playerRef, worldName), delaySeconds, TimeUnit.SECONDS);
    }
    
    /**
     * Broadcast message to all online players.
     */
    private void broadcastMessage(String text, String color) {
        // Use MessageFormatter to process color codes in the text
        Message message = MessageFormatter.format(text);
        
        // Get all online players and broadcast
        try {
            Universe universe = Universe.get();
            if (universe != null) {
                var players = universe.getPlayers();
                for (PlayerRef player : players) {
                    player.sendMessage(message);
                }
            }
        } catch (Exception e) {
            logger.warning("Could not broadcast message: " + e.getMessage());
        }
    }
    
    /**
     * Shutdown the executor service for clean plugin unload.
     */
    public void shutdown() {
        scheduler.shutdown();
        try {
            if (!scheduler.awaitTermination(5, TimeUnit.SECONDS)) {
                scheduler.shutdownNow();
            }
        } catch (InterruptedException e) {
            scheduler.shutdownNow();
            Thread.currentThread().interrupt();
        }
    }
    
    /**
     * Send translation overrides to a player to blank out default Hytale join/leave messages.
     * This uses the UpdateTranslations packet to override the client's translation strings
     * with empty strings, effectively hiding the built-in messages.
     * 
     * Our custom join/quit messages are sent separately via broadcastMessage() which
     * supports color codes and formatting.
     * 
     * Note: This results in a blank line in chat, but allows us to use colored messages.
     */
    private void sendTranslationOverrides(PlayerRef playerRef) {
        if (playerRef == null) return;
        
        try {
            Map<String, String> overrides = new HashMap<>();
            
            // Blank out the default messages - we send our own colored messages separately
            overrides.put(TRANSLATION_KEY_JOINED_WORLD, "");
            overrides.put(TRANSLATION_KEY_LEFT_WORLD, "");
            
            UpdateTranslations packet = new UpdateTranslations(UpdateType.AddOrUpdate, overrides);
            playerRef.getPacketHandler().write(packet);
            
            if (configManager.isDebugEnabled()) {
                logger.info("Sent translation overrides to " + playerRef.getUsername() 
                        + " - blanked join/leave messages");
            }
        } catch (Exception e) {
            logger.warning("Failed to send translation overrides: " + e.getMessage());
        }
    }
    
    /**
     * Send translation overrides to all online players.
     * Called on reload to update all players with new message formats.
     */
    public void sendTranslationOverridesToAll() {
        PluginConfig config = configManager.getConfig();
        if (!config.joinMsg.suppressDefaultMessages) {
            return;
        }
        
        try {
            Universe universe = Universe.get();
            if (universe != null) {
                for (PlayerRef player : universe.getPlayers()) {
                    sendTranslationOverrides(player);
                }
            }
        } catch (Exception e) {
            logger.warning("Failed to send translation overrides to all players: " + e.getMessage());
        }
    }
}
