package com.eliteessentials.storage;

import com.eliteessentials.model.Location;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;

import java.io.*;
import java.lang.reflect.Type;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Logger;

/**
 * Handles persistent storage of player back locations.
 * Stores the last N locations per player in back_locations.json.
 * This ensures /back works even after server restarts.
 */
public class BackStorage {

    private static final Logger logger = Logger.getLogger("EliteEssentials");
    private static final Gson gson = new GsonBuilder().setPrettyPrinting().create();
    private static final Type DATA_TYPE = new TypeToken<Map<UUID, List<Location>>>() {}.getType();

    private final File dataFolder;
    private final File backFile;
    
    // UUID -> Location history (most recent first)
    private final Map<UUID, List<Location>> playerLocations = new ConcurrentHashMap<>();
    
    // Lock for file I/O operations to prevent concurrent writes
    private final Object fileLock = new Object();
    
    private int maxHistory = 5;

    public BackStorage(File dataFolder) {
        this.dataFolder = dataFolder;
        this.backFile = new File(dataFolder, "back_locations.json");
    }

    public void setMaxHistory(int maxHistory) {
        this.maxHistory = maxHistory;
    }

    public void load() {
        if (!backFile.exists()) {
            logger.info("[BackStorage] No back_locations.json found, starting fresh.");
            return;
        }

        try (Reader reader = new InputStreamReader(new FileInputStream(backFile), StandardCharsets.UTF_8)) {
            Map<UUID, List<Location>> loaded = gson.fromJson(reader, DATA_TYPE);
            if (loaded != null) {
                playerLocations.clear();
                // Convert to concurrent lists
                for (Map.Entry<UUID, List<Location>> entry : loaded.entrySet()) {
                    playerLocations.put(entry.getKey(), new ArrayList<>(entry.getValue()));
                }
                logger.info("[BackStorage] Loaded back locations for " + playerLocations.size() + " players.");
            }
        } catch (Exception e) {
            logger.severe("[BackStorage] Failed to load back_locations.json: " + e.getMessage());
        }
    }

    public void save() {
        synchronized (fileLock) {
            // Ensure folder exists
            if (!dataFolder.exists()) {
                dataFolder.mkdirs();
            }
            
            try (Writer writer = new OutputStreamWriter(new FileOutputStream(backFile), StandardCharsets.UTF_8)) {
                gson.toJson(playerLocations, DATA_TYPE, writer);
                logger.fine("[BackStorage] Saved back locations data.");
            } catch (Exception e) {
                logger.severe("[BackStorage] Failed to save back_locations.json: " + e.getMessage());
            }
        }
    }

    /**
     * Push a location to the front of a player's history.
     * Automatically trims to maxHistory and saves.
     */
    public synchronized void pushLocation(UUID playerId, Location location) {
        if (playerId == null || location == null) return;
        
        List<Location> history = playerLocations.computeIfAbsent(playerId, k -> new ArrayList<>());
        
        // Add to front
        history.add(0, location.clone());
        
        // Trim to max size
        while (history.size() > maxHistory) {
            history.remove(history.size() - 1);
        }
        
        // Auto-save after each push to ensure persistence
        save();
        
        logger.fine("[BackStorage] Saved location for " + playerId + ": " + 
            String.format("%.1f, %.1f, %.1f", location.getX(), location.getY(), location.getZ()));
    }

    /**
     * Peek at the most recent location without removing it.
     */
    public Optional<Location> peekLocation(UUID playerId) {
        List<Location> history = playerLocations.get(playerId);
        if (history == null || history.isEmpty()) {
            return Optional.empty();
        }
        return Optional.of(history.get(0).clone());
    }

    /**
     * Pop and return the most recent location.
     */
    public synchronized Optional<Location> popLocation(UUID playerId) {
        List<Location> history = playerLocations.get(playerId);
        if (history == null || history.isEmpty()) {
            return Optional.empty();
        }
        
        Location location = history.remove(0);
        save(); // Persist the change
        
        return Optional.of(location);
    }

    /**
     * Get the number of stored locations for a player.
     */
    public int getHistorySize(UUID playerId) {
        List<Location> history = playerLocations.get(playerId);
        return history == null ? 0 : history.size();
    }

    /**
     * Clear all locations for a player.
     */
    public synchronized void clearHistory(UUID playerId) {
        playerLocations.remove(playerId);
        save();
    }

    /**
     * Get all locations for a player (for debugging).
     */
    public List<Location> getHistory(UUID playerId) {
        List<Location> history = playerLocations.get(playerId);
        if (history == null) return Collections.emptyList();
        return new ArrayList<>(history);
    }
}
