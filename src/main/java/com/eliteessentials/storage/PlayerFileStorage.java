package com.eliteessentials.storage;

import com.eliteessentials.model.PlayerFile;
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
 * Handles per-player data storage in individual JSON files.
 * 
 * Structure:
 * - data/players/{uuid}.json - individual player files
 * - data/player_index.json - name -> uuid lookup for offline players
 * 
 * Features:
 * - Lazy loading: only loads player data when needed
 * - Caching: keeps online players' data in memory
 * - Auto-save: saves individual player files on changes
 * - Index: maintains name->uuid mapping for commands like /seen
 */
public class PlayerFileStorage {
    
    private static final Logger logger = Logger.getLogger("EliteEssentials");
    private static final Gson gson = new GsonBuilder()
            .setPrettyPrinting()
            .disableHtmlEscaping()
            .create();
    private static final Type INDEX_TYPE = new TypeToken<Map<String, UUID>>(){}.getType();
    
    private final File dataFolder;
    private final File playersFolder;
    private final File indexFile;
    
    // In-memory cache of loaded players (typically online players)
    private final Map<UUID, PlayerFile> cache = new ConcurrentHashMap<>();
    
    // Name -> UUID index for lookups (lowercase name -> UUID)
    private final Map<String, UUID> nameIndex = new ConcurrentHashMap<>();
    
    // Lock for index file writes
    private final Object indexLock = new Object();
    
    // Track dirty players that need saving
    private final Set<UUID> dirtyPlayers = ConcurrentHashMap.newKeySet();
    
    public PlayerFileStorage(File dataFolder) {
        this.dataFolder = dataFolder;
        this.playersFolder = new File(dataFolder, "players");
        this.indexFile = new File(dataFolder, "player_index.json");
        
        // Ensure players folder exists
        if (!playersFolder.exists()) {
            playersFolder.mkdirs();
        }
        
        // Load the name index
        loadIndex();
    }
    
    // ==================== Index Management ====================
    
    /**
     * Load the name -> UUID index from file.
     */
    private void loadIndex() {
        if (!indexFile.exists()) {
            logger.info("[PlayerFileStorage] No player_index.json found, starting fresh.");
            return;
        }
        
        try (Reader reader = new InputStreamReader(new FileInputStream(indexFile), StandardCharsets.UTF_8)) {
            Map<String, UUID> loaded = gson.fromJson(reader, INDEX_TYPE);
            if (loaded != null) {
                nameIndex.clear();
                nameIndex.putAll(loaded);
                logger.info("[PlayerFileStorage] Loaded player index with " + nameIndex.size() + " entries.");
            }
        } catch (Exception e) {
            logger.severe("[PlayerFileStorage] Failed to load player_index.json: " + e.getMessage());
        }
    }
    
    /**
     * Save the name -> UUID index to file.
     */
    private void saveIndex() {
        synchronized (indexLock) {
            try (Writer writer = new OutputStreamWriter(new FileOutputStream(indexFile), StandardCharsets.UTF_8)) {
                gson.toJson(nameIndex, INDEX_TYPE, writer);
            } catch (Exception e) {
                logger.severe("[PlayerFileStorage] Failed to save player_index.json: " + e.getMessage());
            }
        }
    }
    
    /**
     * Update the index with a player's name.
     */
    private void updateIndex(UUID uuid, String name) {
        String lowerName = name.toLowerCase();
        UUID existing = nameIndex.get(lowerName);
        
        // Only update if name is new or changed
        if (existing == null || !existing.equals(uuid)) {
            // Remove old name if this UUID had a different name
            nameIndex.entrySet().removeIf(e -> e.getValue().equals(uuid) && !e.getKey().equals(lowerName));
            nameIndex.put(lowerName, uuid);
            saveIndex();
        }
    }
    
    // ==================== Player File Operations ====================
    
    /**
     * Get a player's data file path.
     */
    private File getPlayerFile(UUID uuid) {
        return new File(playersFolder, uuid.toString() + ".json");
    }
    
    /**
     * Load a player's data from disk.
     */
    private PlayerFile loadFromDisk(UUID uuid) {
        File file = getPlayerFile(uuid);
        if (!file.exists()) {
            return null;
        }
        
        try (Reader reader = new InputStreamReader(new FileInputStream(file), StandardCharsets.UTF_8)) {
            PlayerFile data = gson.fromJson(reader, PlayerFile.class);
            if (data != null) {
                // Ensure UUID is set (in case file was manually created)
                if (data.getUuid() == null) {
                    data.setUuid(uuid);
                }
            }
            return data;
        } catch (Exception e) {
            logger.severe("[PlayerFileStorage] Failed to load player file " + uuid + ": " + e.getMessage());
            return null;
        }
    }
    
    /**
     * Save a player's data to disk.
     */
    public void savePlayer(UUID uuid) {
        PlayerFile data = cache.get(uuid);
        if (data == null) {
            return;
        }
        
        File file = getPlayerFile(uuid);
        try (Writer writer = new OutputStreamWriter(new FileOutputStream(file), StandardCharsets.UTF_8)) {
            gson.toJson(data, writer);
            dirtyPlayers.remove(uuid);
        } catch (Exception e) {
            logger.severe("[PlayerFileStorage] Failed to save player file " + uuid + ": " + e.getMessage());
        }
    }
    
    /**
     * Save a player's data to disk (direct, for migration).
     */
    public void savePlayerDirect(PlayerFile data) {
        if (data == null || data.getUuid() == null) return;
        
        File file = getPlayerFile(data.getUuid());
        try (Writer writer = new OutputStreamWriter(new FileOutputStream(file), StandardCharsets.UTF_8)) {
            gson.toJson(data, writer);
        } catch (Exception e) {
            logger.severe("[PlayerFileStorage] Failed to save player file " + data.getUuid() + ": " + e.getMessage());
        }
        
        // Update index
        if (data.getName() != null) {
            updateIndex(data.getUuid(), data.getName());
        }
    }
    
    // ==================== Public API ====================
    
    /**
     * Get a player's data, loading from disk if necessary.
     * Creates a new PlayerFile if the player doesn't exist.
     */
    public PlayerFile getPlayer(UUID uuid, String name) {
        // Check cache first
        PlayerFile data = cache.get(uuid);
        if (data != null) {
            // Update name if changed
            if (name != null && !name.equals(data.getName())) {
                data.setName(name);
                markDirty(uuid);
            }
            return data;
        }
        
        // Try to load from disk
        data = loadFromDisk(uuid);
        if (data != null) {
            // Update name if changed
            if (name != null && !name.equals(data.getName())) {
                data.setName(name);
                markDirty(uuid);
            }
            cache.put(uuid, data);
            updateIndex(uuid, data.getName());
            return data;
        }
        
        // Create new player
        data = new PlayerFile(uuid, name);
        cache.put(uuid, data);
        updateIndex(uuid, name);
        markDirty(uuid);
        return data;
    }
    
    /**
     * Get a player's data by UUID only (for offline lookups).
     * Returns null if player doesn't exist.
     */
    public PlayerFile getPlayer(UUID uuid) {
        // Check cache first
        PlayerFile data = cache.get(uuid);
        if (data != null) {
            return data;
        }
        
        // Try to load from disk
        data = loadFromDisk(uuid);
        if (data != null) {
            cache.put(uuid, data);
            return data;
        }
        
        return null;
    }
    
    /**
     * Get a player's UUID by name (case-insensitive).
     */
    public Optional<UUID> getUuidByName(String name) {
        return Optional.ofNullable(nameIndex.get(name.toLowerCase()));
    }
    
    /**
     * Get a player's data by name (case-insensitive).
     * Returns null if player doesn't exist.
     */
    public PlayerFile getPlayerByName(String name) {
        UUID uuid = nameIndex.get(name.toLowerCase());
        if (uuid == null) {
            return null;
        }
        return getPlayer(uuid);
    }
    
    /**
     * Check if a player exists (has a file on disk or in cache).
     */
    public boolean hasPlayer(UUID uuid) {
        return cache.containsKey(uuid) || getPlayerFile(uuid).exists();
    }
    
    /**
     * Mark a player's data as dirty (needs saving).
     */
    public void markDirty(UUID uuid) {
        dirtyPlayers.add(uuid);
    }
    
    /**
     * Save a player and mark as dirty.
     * Call this after modifying player data.
     */
    public void saveAndMarkDirty(UUID uuid) {
        markDirty(uuid);
        savePlayer(uuid);
    }
    
    /**
     * Unload a player from cache (call on disconnect).
     * Saves the player first if dirty.
     */
    public void unloadPlayer(UUID uuid) {
        if (dirtyPlayers.contains(uuid)) {
            savePlayer(uuid);
        }
        cache.remove(uuid);
    }
    
    /**
     * Save all dirty players.
     */
    public void saveAllDirty() {
        for (UUID uuid : new HashSet<>(dirtyPlayers)) {
            savePlayer(uuid);
        }
    }
    
    /**
     * Save all cached players (for shutdown).
     */
    public void saveAll() {
        for (UUID uuid : cache.keySet()) {
            savePlayer(uuid);
        }
        saveIndex();
    }
    
    /**
     * Get all cached players (online players).
     */
    public Collection<PlayerFile> getCachedPlayers() {
        return Collections.unmodifiableCollection(cache.values());
    }
    
    /**
     * Get all player UUIDs (from index).
     */
    public Collection<UUID> getAllPlayerUuids() {
        return Collections.unmodifiableCollection(nameIndex.values());
    }
    
    /**
     * Get all players sorted by a comparator.
     * WARNING: This loads ALL player files - use sparingly!
     */
    public List<PlayerFile> getAllPlayersSorted(Comparator<PlayerFile> comparator) {
        List<PlayerFile> all = new ArrayList<>();
        
        // Get all UUIDs from index
        Set<UUID> allUuids = new HashSet<>(nameIndex.values());
        
        // Also scan the players folder for any files not in index
        File[] files = playersFolder.listFiles((dir, name) -> name.endsWith(".json"));
        if (files != null) {
            for (File file : files) {
                try {
                    String uuidStr = file.getName().replace(".json", "");
                    UUID uuid = UUID.fromString(uuidStr);
                    allUuids.add(uuid);
                } catch (IllegalArgumentException e) {
                    // Invalid UUID filename, skip
                }
            }
        }
        
        // Load all players
        for (UUID uuid : allUuids) {
            PlayerFile player = getPlayer(uuid);
            if (player != null) {
                all.add(player);
            }
        }
        
        all.sort(comparator);
        return all;
    }
    
    /**
     * Get players sorted by wallet (highest first).
     */
    public List<PlayerFile> getPlayersByWallet() {
        return getAllPlayersSorted(Comparator.comparingDouble(PlayerFile::getWallet).reversed());
    }
    
    /**
     * Get players sorted by play time (highest first).
     */
    public List<PlayerFile> getPlayersByPlayTime() {
        return getAllPlayersSorted(Comparator.comparingLong(PlayerFile::getPlayTime).reversed());
    }
    
    /**
     * Get players sorted by last seen (most recent first).
     */
    public List<PlayerFile> getPlayersByLastSeen() {
        return getAllPlayersSorted(Comparator.comparingLong(PlayerFile::getLastSeen).reversed());
    }
    
    /**
     * Get total number of players (from index + files).
     */
    public int getPlayerCount() {
        Set<UUID> allUuids = new HashSet<>(nameIndex.values());
        
        File[] files = playersFolder.listFiles((dir, name) -> name.endsWith(".json"));
        if (files != null) {
            for (File file : files) {
                try {
                    String uuidStr = file.getName().replace(".json", "");
                    UUID uuid = UUID.fromString(uuidStr);
                    allUuids.add(uuid);
                } catch (IllegalArgumentException e) {
                    // Invalid UUID filename, skip
                }
            }
        }
        
        return allUuids.size();
    }
    
    /**
     * Reload the index (for /ee reload).
     */
    public void reload() {
        loadIndex();
    }
    
    /**
     * Get the players folder (for migration).
     */
    public File getPlayersFolder() {
        return playersFolder;
    }
}
