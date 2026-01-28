package com.eliteessentials.storage;

import com.eliteessentials.model.PlayerData;
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
 * Handles persistent storage of player data cache.
 * Data is stored in players.json keyed by player UUID.
 */
public class PlayerStorage {

    private static final Logger logger = Logger.getLogger("EliteEssentials");
    private static final Gson gson = new GsonBuilder()
            .setPrettyPrinting()
            .disableHtmlEscaping()
            .create();
    private static final Type DATA_TYPE = new TypeToken<Map<UUID, PlayerData>>() {}.getType();

    private final File playersFile;
    
    // UUID -> PlayerData
    private final Map<UUID, PlayerData> playerCache = new ConcurrentHashMap<>();
    
    // Lock for file I/O operations
    private final Object fileLock = new Object();

    public PlayerStorage(File dataFolder) {
        this.playersFile = new File(dataFolder, "players.json");
    }

    public void load() {
        if (!playersFile.exists()) {
            logger.info("No players.json found, starting fresh.");
            return;
        }

        try (Reader reader = new InputStreamReader(new FileInputStream(playersFile), StandardCharsets.UTF_8)) {
            Map<UUID, PlayerData> loaded = gson.fromJson(reader, DATA_TYPE);
            if (loaded != null) {
                playerCache.clear();
                playerCache.putAll(loaded);
                logger.info("Loaded " + playerCache.size() + " player records from cache.");
            }
        } catch (Exception e) {
            logger.severe("Failed to load players.json: " + e.getMessage());
        }
    }

    public void save() {
        synchronized (fileLock) {
            try (Writer writer = new OutputStreamWriter(new FileOutputStream(playersFile), StandardCharsets.UTF_8)) {
                gson.toJson(playerCache, DATA_TYPE, writer);
            } catch (Exception e) {
                logger.severe("Failed to save players.json: " + e.getMessage());
            }
        }
    }

    /**
     * Get player data by UUID.
     */
    public Optional<PlayerData> getPlayer(UUID playerId) {
        return Optional.ofNullable(playerCache.get(playerId));
    }

    /**
     * Get player data by name (case-insensitive).
     */
    public Optional<PlayerData> getPlayerByName(String name) {
        return playerCache.values().stream()
                .filter(p -> p.getName().equalsIgnoreCase(name))
                .findFirst();
    }

    /**
     * Check if a player exists in the cache.
     */
    public boolean hasPlayer(UUID playerId) {
        return playerCache.containsKey(playerId);
    }

    /**
     * Create or update player data.
     */
    public PlayerData getOrCreatePlayer(UUID playerId, String name) {
        return playerCache.computeIfAbsent(playerId, id -> new PlayerData(id, name));
    }

    /**
     * Update existing player data.
     */
    public void updatePlayer(PlayerData data) {
        playerCache.put(data.getUuid(), data);
    }

    /**
     * Get all cached players.
     */
    public Collection<PlayerData> getAllPlayers() {
        return Collections.unmodifiableCollection(playerCache.values());
    }

    /**
     * Get total number of unique players.
     */
    public int getPlayerCount() {
        return playerCache.size();
    }

    /**
     * Get players sorted by first join (oldest first).
     */
    public List<PlayerData> getPlayersByFirstJoin() {
        List<PlayerData> sorted = new ArrayList<>(playerCache.values());
        sorted.sort(Comparator.comparingLong(PlayerData::getFirstJoin));
        return sorted;
    }

    /**
     * Get players sorted by last seen (most recent first).
     */
    public List<PlayerData> getPlayersByLastSeen() {
        List<PlayerData> sorted = new ArrayList<>(playerCache.values());
        sorted.sort(Comparator.comparingLong(PlayerData::getLastSeen).reversed());
        return sorted;
    }

    /**
     * Get players sorted by play time (highest first).
     */
    public List<PlayerData> getPlayersByPlayTime() {
        List<PlayerData> sorted = new ArrayList<>(playerCache.values());
        sorted.sort(Comparator.comparingLong(PlayerData::getPlayTime).reversed());
        return sorted;
    }

    /**
     * Get players sorted by wallet balance (highest first).
     */
    public List<PlayerData> getPlayersByWallet() {
        List<PlayerData> sorted = new ArrayList<>(playerCache.values());
        sorted.sort(Comparator.comparingDouble(PlayerData::getWallet).reversed());
        return sorted;
    }
}
