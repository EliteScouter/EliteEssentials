package com.eliteessentials.storage;

import com.eliteessentials.model.Home;
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
 * Handles persistent storage of player homes.
 * Data is stored in homes.json keyed by player UUID.
 */
public class HomeStorage {

    private static final Logger logger = Logger.getLogger("EliteEssentials");
    private static final Gson gson = new GsonBuilder().setPrettyPrinting().create();
    private static final Type DATA_TYPE = new TypeToken<Map<UUID, Map<String, Home>>>() {}.getType();

    private final File dataFolder;
    private final File homesFile;
    
    // UUID -> (HomeName -> Home)
    private final Map<UUID, Map<String, Home>> playerHomes = new ConcurrentHashMap<>();
    
    // Lock for file I/O operations to prevent concurrent writes
    private final Object fileLock = new Object();

    public HomeStorage(File dataFolder) {
        this.dataFolder = dataFolder;
        this.homesFile = new File(dataFolder, "homes.json");
    }

    public void load() {
        if (!homesFile.exists()) {
            logger.info("No homes.json found, starting fresh.");
            return;
        }

        try (Reader reader = new InputStreamReader(new FileInputStream(homesFile), StandardCharsets.UTF_8)) {
            Map<UUID, Map<String, Home>> loaded = gson.fromJson(reader, DATA_TYPE);
            if (loaded != null) {
                playerHomes.clear();
                playerHomes.putAll(loaded);
                logger.info("Loaded " + playerHomes.size() + " player home records.");
            }
        } catch (Exception e) {
            logger.severe("Failed to load homes.json: " + e.getMessage());
        }
    }

    public void save() {
        synchronized (fileLock) {
            try (Writer writer = new OutputStreamWriter(new FileOutputStream(homesFile), StandardCharsets.UTF_8)) {
                gson.toJson(playerHomes, DATA_TYPE, writer);
                logger.info("Saved homes data.");
            } catch (Exception e) {
                logger.severe("Failed to save homes.json: " + e.getMessage());
            }
        }
    }

    /**
     * Get all homes for a player.
     */
    public Map<String, Home> getHomes(UUID playerId) {
        return playerHomes.getOrDefault(playerId, Collections.emptyMap());
    }

    /**
     * Get a specific home for a player.
     */
    public Optional<Home> getHome(UUID playerId, String name) {
        Map<String, Home> homes = playerHomes.get(playerId);
        if (homes == null) return Optional.empty();
        return Optional.ofNullable(homes.get(name.toLowerCase()));
    }

    /**
     * Set a home for a player.
     */
    public void setHome(UUID playerId, Home home) {
        playerHomes.computeIfAbsent(playerId, k -> new ConcurrentHashMap<>())
                   .put(home.getName().toLowerCase(), home);
    }

    /**
     * Delete a home for a player.
     */
    public boolean deleteHome(UUID playerId, String name) {
        Map<String, Home> homes = playerHomes.get(playerId);
        if (homes == null) return false;
        return homes.remove(name.toLowerCase()) != null;
    }

    /**
     * Get the number of homes a player has.
     */
    public int getHomeCount(UUID playerId) {
        Map<String, Home> homes = playerHomes.get(playerId);
        return homes == null ? 0 : homes.size();
    }

    /**
     * Check if a player has a home with the given name.
     */
    public boolean hasHome(UUID playerId, String name) {
        Map<String, Home> homes = playerHomes.get(playerId);
        return homes != null && homes.containsKey(name.toLowerCase());
    }

    /**
     * Get all home names for a player.
     */
    public Set<String> getHomeNames(UUID playerId) {
        Map<String, Home> homes = playerHomes.get(playerId);
        return homes == null ? Collections.emptySet() : new HashSet<>(homes.keySet());
    }
}
