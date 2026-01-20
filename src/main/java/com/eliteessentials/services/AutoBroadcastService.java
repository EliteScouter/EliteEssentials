package com.eliteessentials.services;

import com.eliteessentials.model.AutoBroadcast;
import com.eliteessentials.util.MessageFormatter;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;
import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.Universe;

import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.lang.reflect.Type;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.logging.Logger;

/**
 * Service for managing automatic broadcasts at configurable intervals.
 * Supports multiple broadcast groups with different intervals and messages.
 */
public class AutoBroadcastService {
    
    private static final Logger logger = Logger.getLogger("EliteEssentials");
    private static final Gson gson = new GsonBuilder().setPrettyPrinting().create();
    
    private final File dataFile;
    private final Object fileLock = new Object();
    private final Random random = new Random();
    
    private List<AutoBroadcast> broadcasts;
    private final Map<String, Integer> messageIndices = new ConcurrentHashMap<>();
    private final Map<String, ScheduledFuture<?>> scheduledTasks = new ConcurrentHashMap<>();
    private ScheduledExecutorService scheduler;
    
    public AutoBroadcastService(File dataFolder) {
        this.dataFile = new File(dataFolder, "autobroadcast.json");
        this.broadcasts = new ArrayList<>();
        load();
    }
    
    /**
     * Load broadcasts from file or create defaults.
     */
    public void load() {
        if (!dataFile.exists()) {
            createDefaults();
            save();
            return;
        }
        
        synchronized (fileLock) {
            try (FileReader reader = new FileReader(dataFile, StandardCharsets.UTF_8)) {
                Type type = new TypeToken<AutoBroadcastData>(){}.getType();
                AutoBroadcastData data = gson.fromJson(reader, type);
                if (data != null && data.broadcasts != null) {
                    broadcasts = data.broadcasts;
                } else {
                    createDefaults();
                }
            } catch (IOException e) {
                logger.warning("Could not load autobroadcast.json: " + e.getMessage());
                createDefaults();
            }
        }
    }
    
    /**
     * Save broadcasts to file.
     */
    public void save() {
        synchronized (fileLock) {
            try (FileWriter writer = new FileWriter(dataFile, StandardCharsets.UTF_8)) {
                AutoBroadcastData data = new AutoBroadcastData();
                data.broadcasts = broadcasts;
                gson.toJson(data, writer);
            } catch (IOException e) {
                logger.severe("Could not save autobroadcast.json: " + e.getMessage());
            }
        }
    }

    /**
     * Start all enabled broadcast schedules.
     */
    public void start() {
        if (scheduler != null && !scheduler.isShutdown()) {
            shutdown();
        }
        
        scheduler = Executors.newScheduledThreadPool(1, r -> {
            Thread t = new Thread(r, "EliteEssentials-AutoBroadcast");
            t.setDaemon(true);
            return t;
        });
        
        for (AutoBroadcast broadcast : broadcasts) {
            if (broadcast.isEnabled() && broadcast.getIntervalSeconds() > 0) {
                scheduleBroadcast(broadcast);
            }
        }
        
        int enabledCount = (int) broadcasts.stream().filter(AutoBroadcast::isEnabled).count();
        logger.info("AutoBroadcast started with " + enabledCount + " enabled broadcast(s).");
    }
    
    /**
     * Schedule a single broadcast to run at its interval.
     */
    private void scheduleBroadcast(AutoBroadcast broadcast) {
        if (scheduler == null || scheduler.isShutdown()) return;
        
        int interval = broadcast.getIntervalSeconds();
        ScheduledFuture<?> task = scheduler.scheduleAtFixedRate(
            () -> sendBroadcast(broadcast),
            interval,
            interval,
            TimeUnit.SECONDS
        );
        
        scheduledTasks.put(broadcast.getId(), task);
    }
    
    /**
     * Send the next message for a broadcast.
     */
    private void sendBroadcast(AutoBroadcast broadcast) {
        try {
            // Check if players are required and none are online
            if (broadcast.isRequirePlayers()) {
                Universe universe = Universe.get();
                if (universe == null || universe.getPlayers().isEmpty()) {
                    return; // Skip broadcast - no players online
                }
            }
            
            List<String> messages = broadcast.getMessages();
            if (messages == null || messages.isEmpty()) return;
            
            String message;
            if (broadcast.isRandom()) {
                // Random message selection
                message = messages.get(random.nextInt(messages.size()));
            } else {
                // Sequential message selection
                int index = messageIndices.getOrDefault(broadcast.getId(), 0);
                message = messages.get(index);
                messageIndices.put(broadcast.getId(), (index + 1) % messages.size());
            }
            
            // Prepend prefix if set
            String prefix = broadcast.getPrefix();
            if (prefix != null && !prefix.isEmpty()) {
                message = prefix + " " + message;
            }
            
            broadcastToAll(message);
        } catch (Exception e) {
            logger.warning("Error sending auto-broadcast '" + broadcast.getId() + "': " + e.getMessage());
        }
    }
    
    /**
     * Broadcast a message to all online players with color code support.
     * Supports \n for multi-line messages.
     */
    private void broadcastToAll(String text) {
        try {
            Universe universe = Universe.get();
            if (universe == null) return;
            
            var players = universe.getPlayers();
            if (players.isEmpty()) return;
            
            // Split by \n for multi-line support
            String[] lines = text.split("\\\\n|\\n");
            
            for (String line : lines) {
                if (line.isEmpty()) continue;
                Message message = MessageFormatter.format(line);
                for (PlayerRef player : players) {
                    player.sendMessage(message);
                }
            }
        } catch (Exception e) {
            logger.warning("Could not broadcast message: " + e.getMessage());
        }
    }
    
    /**
     * Shutdown all broadcast schedules.
     */
    public void shutdown() {
        // Cancel all scheduled tasks
        for (ScheduledFuture<?> task : scheduledTasks.values()) {
            task.cancel(false);
        }
        scheduledTasks.clear();
        
        // Shutdown scheduler
        if (scheduler != null && !scheduler.isShutdown()) {
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
    
    /**
     * Reload broadcasts from file and restart schedules.
     */
    public void reload() {
        shutdown();
        load();
        start();
    }
    
    /**
     * Get all broadcasts.
     */
    public List<AutoBroadcast> getBroadcasts() {
        return new ArrayList<>(broadcasts);
    }
    
    /**
     * Get a broadcast by ID.
     */
    public AutoBroadcast getBroadcast(String id) {
        return broadcasts.stream()
            .filter(b -> b.getId().equalsIgnoreCase(id))
            .findFirst()
            .orElse(null);
    }
    
    /**
     * Create default broadcast configuration.
     */
    private void createDefaults() {
        broadcasts = new ArrayList<>();
        
        // Example broadcast - showcasing multiple rotating messages
        AutoBroadcast example = new AutoBroadcast();
        example.setId("example");
        example.setEnabled(false); // Disabled by default - customize and enable when ready
        example.setIntervalSeconds(600);
        example.setPrefix("");
        example.setRandom(false);
        example.setRequirePlayers(true);
        List<String> exampleMessages = new ArrayList<>();
        exampleMessages.add("&5&l[Discord]&7 - &fJoin our community!\n&7Chat, updates, events & support.\n&bhttps://discord.gg/CEP7XuH2D2");
        exampleMessages.add("&6&l[Tip]&7 - &fNeed help getting started?\n&7Check out our commands with &e/help\n&7Set your home with &e/sethome");
        exampleMessages.add("&a&l[Rules]&7 - &fRemember to follow the rules!\n&7Type &e/rules &7to view them.\n&7Be respectful and have fun!");
        example.setMessages(exampleMessages);
        broadcasts.add(example);
    }
    
    /**
     * POJO for JSON serialization.
     */
    private static class AutoBroadcastData {
        public List<AutoBroadcast> broadcasts;
    }
}
