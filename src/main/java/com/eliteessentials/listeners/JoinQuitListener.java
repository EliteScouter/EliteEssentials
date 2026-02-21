package com.eliteessentials.listeners;

import com.eliteessentials.EliteEssentials;
import com.eliteessentials.config.ConfigManager;
import com.eliteessentials.config.PluginConfig;
import com.eliteessentials.integration.PAPIIntegration;
import com.eliteessentials.services.AfkService;
import com.eliteessentials.services.MailService;
import com.eliteessentials.services.PlayerService;
import com.eliteessentials.services.PlayTimeRewardService;
import com.eliteessentials.storage.MotdStorage;
import com.eliteessentials.storage.PlayerFileStorage;
import com.eliteessentials.storage.SpawnStorage;
import com.eliteessentials.util.MessageFormatter;
import com.eliteessentials.util.TeleportGuard;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.hypixel.hytale.component.Holder;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.event.EventRegistry;
import com.hypixel.hytale.server.core.event.events.player.PlayerReadyEvent;
import com.hypixel.hytale.server.core.event.events.player.PlayerDisconnectEvent;
import com.hypixel.hytale.server.core.event.events.player.AddPlayerToWorldEvent;
import com.hypixel.hytale.server.core.event.events.player.DrainPlayerFromWorldEvent;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.Universe;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import org.jetbrains.annotations.Nullable;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.List;
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
 * - First join spawn teleport (teleport new players to /setspawn location)
 * - Quit messages (server disconnect only, not world changes)
 * - World change messages (optional)
 * - MOTD display on join (server join only, not world changes)
 * - Per-world MOTD (optional, can show always or once per session)
 * - Suppression of default Hytale join/leave messages
 * - Player cache updates (via PlayerService)
 */
public class JoinQuitListener {

    private static final Logger logger = Logger.getLogger("EliteEssentials");

    private final ConfigManager configManager;
    private final MotdStorage motdStorage;
    private final PlayerService playerService;
    private final ScheduledExecutorService scheduler;
    private PlayerFileStorage playerFileStorage;
    private SpawnStorage spawnStorage;
    private com.eliteessentials.services.VanishService vanishService;
    private MailService mailService;

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
     * Set the spawn storage (called after initialization).
     */
    public void setSpawnStorage(SpawnStorage storage) {
        this.spawnStorage = storage;
    }

    /**
     * Set the vanish service (called after initialization).
     */
    public void setVanishService(com.eliteessentials.services.VanishService service) {
        this.vanishService = service;
    }

    /**
     * Set the mail service (called after initialization).
     */
    public void setMailService(MailService service) {
        this.mailService = service;
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
        
        // Register packet filter to suppress default leave messages
        // This prevents the "player has left world" message from being sent to clients
        if (config.joinMsg.suppressDefaultMessages) {
            LeaveMessagePacketFilter.register();
        }
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
        
        // Broadcast world leave message if enabled (but not for vanished players)
        PluginConfig config = configManager.getConfig();
        boolean isVanished = vanishService != null && vanishService.isVanished(playerId);
        if (config.joinMsg.worldChangeEnabled && onlinePlayers.contains(playerId) && !isVanished) {
            String message = configManager.getMessage("worldLeaveMessage", "player", playerName, "world", worldName);
            if (message != null && !message.isEmpty()) {
                broadcastMessage(message, "#AAAAAA", playerRef);
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
            // Broadcast world join message if enabled (but not for vanished players)
            boolean isVanished = vanishService != null && vanishService.isVanished(playerId);
            if (config.joinMsg.worldChangeEnabled && !isVanished) {
                String message = configManager.getMessage("worldJoinMessage", "player", playerName, "world", worldName);
                if (message != null && !message.isEmpty()) {
                    broadcastMessage(message, "#AAAAAA", playerRef);
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

            // Hide vanished players from this joining player and check if they were vanished
            boolean playerIsVanished = false;
            if (vanishService != null) {
                playerIsVanished = vanishService.onPlayerJoin(playerRef);
                // Also set up map filter to hide vanished players from the map
                vanishService.onPlayerReady(store, ref);

                // Send vanish reminder if player reconnected while vanished
                if (playerIsVanished && config.vanish.showReminderOnJoin) {
                    // Delay the reminder so it appears after the MOTD
                    scheduler.schedule(() -> {
                        vanishService.sendVanishReminder(playerRef);
                    }, 2000, TimeUnit.MILLISECONDS);
                }
            }

            // Notify playtime reward service
            PlayTimeRewardService rewardService = EliteEssentials.getInstance().getPlayTimeRewardService();
            if (rewardService != null) {
                rewardService.onPlayerJoin(playerId);
            }
            
            // Notify AFK service (track player position for inactivity detection)
            AfkService afkService = EliteEssentials.getInstance().getAfkService();
            if (afkService != null) {
                afkService.onPlayerJoin(playerId, store, ref);
            }

            // Notify tab list service (applies LuckPerms prefix if enabled)
            com.eliteessentials.services.TabListService tabListService = EliteEssentials.getInstance().getTabListService();
            if (tabListService != null) {
                tabListService.onPlayerJoin(playerId);
            }

            // Check if first join by checking if player file existed on disk before this session
            // We check the file directly because playerService.onPlayerJoin() just created it
            boolean isFirstJoin = false;
            if (playerFileStorage != null) {
                var playerData = playerFileStorage.getPlayer(playerId);
                if (playerData != null) {
                    long firstJoinTime = playerData.getFirstJoin();
                    long now = System.currentTimeMillis();
                    // If firstJoin was set within the last 10 seconds, this is a new player
                    // Using 10 seconds to account for any delays in event processing
                    isFirstJoin = (now - firstJoinTime) < 10000;

                    if (configManager.isDebugEnabled()) {
                        logger.info("[FirstJoin] Player " + playerName + " firstJoinTime=" + firstJoinTime +
                                ", now=" + now + ", diff=" + (now - firstJoinTime) + "ms, isFirstJoin=" + isFirstJoin);
                    }
                }
            }

            if (isFirstJoin) {
                // Broadcast first join message (unless player is vanished)
                if (config.joinMsg.firstJoinEnabled && !playerIsVanished) {
                    String message = configManager.getMessage("firstJoinMessage", "player", playerName);
                    broadcastMessage(message, "#FFFF55", playerRef);
                }

                // First-join spawn is handled by Hytale's native ISpawnProvider.
                // New players with no saved TransformComponent land at the
                // default spawn location automatically.

            } else {
                // Regular join message (unless player is vanished)
                if (config.joinMsg.joinEnabled && !playerIsVanished) {
                    // Also check if suppressJoinQuitMessages is enabled for vanished players
                    boolean shouldSuppress = playerIsVanished && config.vanish.suppressJoinQuitMessages;
                    if (!shouldSuppress) {
                        String message = configManager.getMessage("joinMessage", "player", playerName);
                        broadcastMessage(message, "#55FF55", playerRef);
                    }
                }

                // Every-login spawn is handled by rewriting the player's save file
                // on disconnect (onPlayerQuit). The file rewrite sets Transform.Position
                // and LastPosition to spawn coords, so the player loads directly at spawn.
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

            // Notify about unread mail
            if (config.mail.enabled && config.mail.notifyOnLogin && mailService != null) {
                int unreadCount = mailService.getUnreadCount(playerId);
                if (unreadCount > 0) {
                    int mailDelay = config.mail.notifyDelaySeconds;
                    final int finalUnreadCount = unreadCount;
                    scheduler.schedule(() -> {
                        String mailMsg = configManager.getMessage("mailNotifyLogin",
                                "count", String.valueOf(finalUnreadCount));
                        playerRef.sendMessage(MessageFormatter.format(mailMsg));
                    }, mailDelay, TimeUnit.SECONDS);
                }
            }
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

        // Guard against duplicate disconnect events - only process if player was actually online
        // This prevents duplicate quit messages when the server fires multiple disconnect events
        boolean wasOnline = onlinePlayers.remove(playerId);
        if (!wasOnline) {
            // Already processed this disconnect, skip
            return;
        }

        PluginConfig config = configManager.getConfig();

        // Rewrite the player's save file on disk after Hytale saves it.
        // We delay briefly to let Hytale's PlayerRemovedSystem + PlayerStorage.save()
        // finish writing, then overwrite the Transform and LastPosition fields
        // with spawn coordinates. This is the only reliable approach because:
        // - Modifying TransformComponent on disconnect gets overwritten by PlayerRemovedSystem
        // - PlayerRemovedSystem sets lastPosition but nobody calls saveConfig() after removal
        // - The periodic TickingSystem (10s) won't run again after the entity is removed
        if (config.spawn.enabled && config.spawn.teleportOnEveryLogin && spawnStorage != null) {
            String targetWorldName = config.spawn.perWorld ?
                (playerLastWorld.get(playerId) != null ? playerLastWorld.get(playerId) : config.spawn.mainWorld) :
                config.spawn.mainWorld;
            SpawnStorage.SpawnData spawn = spawnStorage.getSpawn(targetWorldName);
            if (spawn != null) {
                final String pName = playerName;
                final UUID pId = playerId;
                final String lastWorld = playerLastWorld.get(playerId);
                scheduler.schedule(() -> {
                    rewritePlayerSaveFile(pId, pName, spawn, targetWorldName, lastWorld);
                }, 2, TimeUnit.SECONDS);
            }
        }

        // Clean up tracking data
        worldChangingPlayers.remove(playerId);
        // Clear seen world MOTDs when player disconnects
        seenWorldMotds.remove(playerId);
        // Clear last world tracking
        playerLastWorld.remove(playerId);
        // Clear teleport guard so stale entries don't block future sessions
        TeleportGuard.get().clear(playerId);

        // Notify playtime reward service before updating player cache
        PlayTimeRewardService rewardService = EliteEssentials.getInstance().getPlayTimeRewardService();
        if (rewardService != null) {
            rewardService.onPlayerQuit(playerId);
        }
        
        // Clean up AFK state
        AfkService afkService = EliteEssentials.getInstance().getAfkService();
        if (afkService != null) {
            afkService.onPlayerQuit(playerId);
        }

        // Update player cache (last seen, play time)
        playerService.onPlayerQuit(playerId);

        // Check if player was vanished before cleaning up vanish state
        boolean wasVanished = false;
        if (vanishService != null) {
            wasVanished = vanishService.onPlayerLeave(playerId);
        }

        // Broadcast quit message if enabled (unless player was vanished)
        if (config.joinMsg.quitEnabled) {
            // Suppress quit message if player was vanished and suppressJoinQuitMessages is enabled
            boolean shouldSuppress = wasVanished && config.vanish.suppressJoinQuitMessages;
            if (!shouldSuppress) {
                String message = configManager.getMessage("quitMessage", "player", playerName);
                broadcastMessage(message, "#FF5555", playerRef);
            }
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
        int playerCount = getVisiblePlayerCount();
        
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

            if (configManager.getConfig().chatFormat.placeholderapi) {
                processedLine = PAPIIntegration.setPlaceholders(playerRef, processedLine);
            }

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
        int playerCount = getVisiblePlayerCount();
        
        for (String line : worldMotd.lines) {
            if (line.trim().isEmpty()) {
                continue;
            }

            String processedLine = line
                    .replace("{player}", playerName)
                    .replace("{server}", serverName)
                    .replace("{world}", worldName)
                    .replace("{playercount}", String.valueOf(playerCount));

            if (configManager.getConfig().chatFormat.placeholderapi) {
                processedLine = PAPIIntegration.setPlaceholders(playerRef, processedLine);
            }

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
     * Get the visible player count (total players minus vanished players).
     * Used for {playercount} placeholder in MOTD.
     */
    private int getVisiblePlayerCount() {
        int totalPlayers = Universe.get().getPlayers().size();
        if (vanishService != null) {
            return totalPlayers - vanishService.getVanishedCount();
        }
        return totalPlayers;
    }

    /**
     * Broadcast message to all online players.
     */
    private void broadcastMessage(String text, String color, @Nullable PlayerRef sender) {
        // Get all online players and broadcast
        try {
            Universe universe = Universe.get();
            if (universe != null) {
                var players = universe.getPlayers();
                for (PlayerRef player : players) {
                    String processedText = text;
                    if (configManager.getConfig().chatFormat.placeholderapi && sender != null) {
                        processedText = PAPIIntegration.setRelationalPlaceholders(sender, player, PAPIIntegration.setPlaceholders(sender, processedText));
                    }

                    // Use MessageFormatter to process color codes in the text
                    player.sendMessage(MessageFormatter.format(processedText));
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
     * Update packet filters for all online players.
     * Called on reload to apply new suppressDefaultMessages setting.
     */
    public void updatePacketFiltersForAll() {
        PluginConfig config = configManager.getConfig();
        
        try {
            if (config.joinMsg.suppressDefaultMessages) {
                // Register the global filter
                LeaveMessagePacketFilter.register();
            } else {
                // Deregister the filter if it was previously registered
                LeaveMessagePacketFilter.deregister();
            }
        } catch (Exception e) {
            logger.warning("Failed to update packet filters: " + e.getMessage());
        }
    }

    /**
     * Rewrite the player's Hytale save file on disk to set spawn coordinates.
     * Called on a scheduler thread after a delay to let Hytale finish saving.
     * 
     * Modifies both Components.Transform.Position and 
     * Components.Player.PlayerData.PerWorldData.{world}.LastPosition
     * so the player loads at spawn on next login.
     */
    private void rewritePlayerSaveFile(UUID playerId, String playerName, 
            SpawnStorage.SpawnData spawn, String targetWorldName, String lastWorld) {
        try {
            // Player save files are at universe/players/{uuid}.json
            Path universePath = Path.of("universe", "players", playerId.toString() + ".json");
            File saveFile = universePath.toFile();
            
            if (!saveFile.exists()) {
                logger.warning("[SpawnOnLogout] Save file not found for " + playerName + ": " + saveFile.getAbsolutePath());
                return;
            }

            // Read the JSON
            JsonObject root;
            try (Reader reader = new InputStreamReader(new FileInputStream(saveFile), StandardCharsets.UTF_8)) {
                root = JsonParser.parseReader(reader).getAsJsonObject();
            }

            JsonObject components = root.getAsJsonObject("Components");
            if (components == null) {
                logger.warning("[SpawnOnLogout] No Components in save file for " + playerName);
                return;
            }

            // Update Transform.Position (where the entity spawns)
            JsonObject transform = components.getAsJsonObject("Transform");
            if (transform != null) {
                JsonObject position = transform.getAsJsonObject("Position");
                if (position != null) {
                    position.addProperty("X", spawn.x);
                    position.addProperty("Y", spawn.y);
                    position.addProperty("Z", spawn.z);
                }
                JsonObject rotation = transform.getAsJsonObject("Rotation");
                if (rotation != null) {
                    rotation.addProperty("Pitch", 0.0);
                    rotation.addProperty("Yaw", spawn.yaw);
                    rotation.addProperty("Roll", 0.0);
                }
            }

            // Update HeadRotation to match
            JsonObject headRotation = components.getAsJsonObject("HeadRotation");
            if (headRotation != null) {
                JsonObject rotation = headRotation.getAsJsonObject("Rotation");
                if (rotation != null) {
                    rotation.addProperty("Pitch", 0.0);
                    rotation.addProperty("Yaw", spawn.yaw);
                    rotation.addProperty("Roll", 0.0);
                }
            }

            // Update Player.PlayerData.PerWorldData.{world}.LastPosition
            JsonObject player = components.getAsJsonObject("Player");
            if (player != null) {
                JsonObject playerData = player.getAsJsonObject("PlayerData");
                if (playerData != null) {
                    // Update World field if cross-world
                    String currentWorld = lastWorld != null ? lastWorld : targetWorldName;
                    if (!currentWorld.equalsIgnoreCase(targetWorldName)) {
                        playerData.addProperty("World", targetWorldName);
                    }

                    JsonObject perWorldData = playerData.getAsJsonObject("PerWorldData");
                    if (perWorldData != null) {
                        // Update LastPosition in the world the player was in
                        String worldKey = currentWorld;
                        JsonObject worldData = perWorldData.getAsJsonObject(worldKey);
                        if (worldData != null) {
                            updateLastPosition(worldData, spawn);
                        }
                        // Also update target world if different
                        if (!currentWorld.equalsIgnoreCase(targetWorldName)) {
                            JsonObject targetData = perWorldData.getAsJsonObject(targetWorldName);
                            if (targetData != null) {
                                updateLastPosition(targetData, spawn);
                            }
                        }
                    }
                }
            }

            // Write back
            Gson gson = new GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create();
            try (Writer writer = new OutputStreamWriter(new FileOutputStream(saveFile), StandardCharsets.UTF_8)) {
                gson.toJson(root, writer);
            }

            logger.info("[SpawnOnLogout] Rewrote " + playerName + "'s save file with spawn at " +
                String.format("%.1f, %.1f, %.1f", spawn.x, spawn.y, spawn.z));
        } catch (Exception e) {
            logger.warning("[SpawnOnLogout] Failed to rewrite save file for " + playerName + ": " + e.getMessage());
        }
    }

    /**
     * Update the LastPosition object in a PerWorldData entry.
     */
    private void updateLastPosition(JsonObject worldData, SpawnStorage.SpawnData spawn) {
        JsonObject lastPos = worldData.getAsJsonObject("LastPosition");
        if (lastPos != null) {
            lastPos.addProperty("X", spawn.x);
            lastPos.addProperty("Y", spawn.y);
            lastPos.addProperty("Z", spawn.z);
            lastPos.addProperty("Pitch", 0.0);
            lastPos.addProperty("Yaw", spawn.yaw);
            lastPos.addProperty("Roll", 0.0);
        }
    }

}
