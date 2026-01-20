package com.eliteessentials.listeners;

import com.eliteessentials.config.ConfigManager;
import com.eliteessentials.config.PluginConfig;
import com.eliteessentials.storage.MotdStorage;
import com.eliteessentials.util.MessageFormatter;
import com.hypixel.hytale.event.EventRegistry;
import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.event.events.player.PlayerReadyEvent;
import com.hypixel.hytale.server.core.event.events.player.AddPlayerToWorldEvent;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.Universe;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;

import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.logging.Logger;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;

/**
 * Handles player join messages.
 * - Join messages
 * - First join messages (broadcast to everyone)
 * - MOTD display on join
 * - Suppression of default Hytale join messages
 */
public class JoinQuitListener {
    
    private static final Logger logger = Logger.getLogger("EliteEssentials");
    private static final Gson gson = new GsonBuilder().setPrettyPrinting().create();
    
    private final ConfigManager configManager;
    private final MotdStorage motdStorage;
    private final File firstJoinFile;
    private final Set<UUID> firstJoinPlayers;
    private final Object fileLock = new Object();
    private final ScheduledExecutorService scheduler;
    
    public JoinQuitListener(ConfigManager configManager, MotdStorage motdStorage, File dataFolder) {
        this.configManager = configManager;
        this.motdStorage = motdStorage;
        this.firstJoinFile = new File(dataFolder, "first_join.json");
        this.firstJoinPlayers = new HashSet<>();
        this.scheduler = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "EliteEssentials-JoinQuit");
            t.setDaemon(true);
            return t;
        });
        load();
    }
    
    /**
     * Register event listeners.
     */
    public void registerEvents(EventRegistry eventRegistry) {
        PluginConfig config = configManager.getConfig();
        
        // Use PlayerReadyEvent for join - fires when player is fully loaded
        eventRegistry.registerGlobal(PlayerReadyEvent.class, event -> {
            onPlayerJoin(event);
        });
        
        // Suppress default join messages if configured
        // Uses AddPlayerToWorldEvent.setBroadcastJoinMessage(false) to prevent
        // the built-in "player has joined default" message
        if (config.joinMsg.suppressDefaultMessages) {
            eventRegistry.registerGlobal(AddPlayerToWorldEvent.class, event -> {
                event.setBroadcastJoinMessage(false);
            });
        }
    }
    
    /**
     * Handle player join event.
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
            PluginConfig config = configManager.getConfig();
            
            // Check if first join
            boolean isFirstJoin = !firstJoinPlayers.contains(playerId);
            
            if (isFirstJoin) {
                // Add to first join set
                firstJoinPlayers.add(playerId);
                save();
                
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
            
            // Show MOTD
            if (config.motd.enabled && config.motd.showOnJoin) {
                String worldName = world.getName();
                int delay = config.motd.delaySeconds;
                if (delay > 0) {
                    // Schedule MOTD display after delay
                    scheduleMotd(playerRef, delay, worldName);
                } else {
                    // Show immediately
                    showMotd(playerRef, worldName);
                }
            }
        });
    }
    
    /**
     * Show MOTD to player.
     */
    private void showMotd(PlayerRef playerRef, String worldName) {
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
     * Schedule MOTD display after delay using executor service for proper thread management.
     */
    private void scheduleMotd(PlayerRef playerRef, int delaySeconds, String worldName) {
        scheduler.schedule(() -> showMotd(playerRef, worldName), delaySeconds, TimeUnit.SECONDS);
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
     * Load first join data from file.
     */
    private void load() {
        if (!firstJoinFile.exists()) {
            return;
        }
        
        synchronized (fileLock) {
            try (FileReader reader = new FileReader(firstJoinFile, StandardCharsets.UTF_8)) {
                Set<UUID> loaded = gson.fromJson(reader, new TypeToken<Set<UUID>>(){}.getType());
                if (loaded != null) {
                    firstJoinPlayers.addAll(loaded);
                }
            } catch (IOException e) {
                logger.warning("Could not load first_join.json: " + e.getMessage());
            }
        }
    }
    
    /**
     * Save first join data to file.
     */
    private void save() {
        synchronized (fileLock) {
            try (FileWriter writer = new FileWriter(firstJoinFile, StandardCharsets.UTF_8)) {
                gson.toJson(firstJoinPlayers, writer);
            } catch (IOException e) {
                logger.severe("Could not save first_join.json: " + e.getMessage());
            }
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
}
