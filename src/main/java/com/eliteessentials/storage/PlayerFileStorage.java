package com.eliteessentials.storage;

import com.eliteessentials.model.PlayerFile;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;

import java.io.*;
import java.lang.reflect.Type;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
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
public class PlayerFileStorage implements PlayerStorageProvider {
    
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
    
    // Per-player write locks. Player files are written from at least three threads
    // (the world thread via commands, the EliteEssentials-PeriodicPlayTimeSave daemon,
    // and the EliteEssentials-JoinQuit scheduler). Without this, two threads could open
    // their own stream to the same {uuid}.json and interleave their output.
    // Never removed: one Object per player seen is negligible, and dropping a lock while
    // another thread held it would reopen the interleaving window.
    private final Map<UUID, Object> writeLocks = new ConcurrentHashMap<>();
    
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
            writeIndexLocked();
        }
    }
    
    /**
     * Write the index using the same serialize-then-atomically-rename approach as player
     * files. Losing this file does not lose player data, but a truncated index breaks every
     * offline name lookup (/seen, /home of another player, ban by name) until it is rebuilt.
     *
     * <p>Caller must hold {@link #indexLock}.
     */
    private void writeIndexLocked() {
        String json;
        try {
            json = gson.toJson(nameIndex, INDEX_TYPE);
        } catch (Exception e) {
            logger.severe("[PlayerFileStorage] Could not serialize player_index.json: " + e
                    + " — the previous index on disk was left intact.");
            return;
        }
        
        Path target = indexFile.toPath();
        Path tmp = new File(dataFolder, "player_index.json.tmp").toPath();
        try {
            Files.writeString(tmp, json, StandardCharsets.UTF_8);
            try {
                Files.move(tmp, target,
                        StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
            } catch (AtomicMoveNotSupportedException e) {
                Files.move(tmp, target, StandardCopyOption.REPLACE_EXISTING);
            }
        } catch (Exception e) {
            logger.severe("[PlayerFileStorage] Failed to save player_index.json: " + e
                    + " — the previous index on disk was left intact.");
            try {
                Files.deleteIfExists(tmp);
            } catch (IOException ignored) {
                // Leftover .tmp is harmless.
            }
        }
    }
    
    /**
     * Update the index with a player's name.
     *
     * <p>The read, the mutation, and the file write all happen under {@link #indexLock}.
     * Previously only the write was locked, so two threads updating different players could
     * interleave their {@code removeIf}/{@code put} and then each persist a different view
     * of the map.
     */
    private void updateIndex(UUID uuid, String name) {
        if (name == null) {
            // Reachable from getPlayer(uuid, name) when a stored file has no name recorded.
            return;
        }
        String lowerName = name.toLowerCase();
        
        synchronized (indexLock) {
            UUID existing = nameIndex.get(lowerName);
            
            // Only update if name is new or changed
            if (existing == null || !existing.equals(uuid)) {
                // Remove old name if this UUID had a different name
                nameIndex.entrySet().removeIf(e -> e.getValue().equals(uuid) && !e.getKey().equals(lowerName));
                nameIndex.put(lowerName, uuid);
                writeIndexLocked();
            }
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
     *
     * <p>Returns null when the player genuinely has no file yet. If a file exists but
     * cannot be read as a player record, the file is quarantined first (see
     * {@link #quarantineUnreadableFile}) so that the caller creating a fresh empty
     * record cannot overwrite recoverable bytes.
     */
    private PlayerFile loadFromDisk(UUID uuid) {
        File file = getPlayerFile(uuid);
        if (!file.exists()) {
            return null;
        }
        
        try (Reader reader = new InputStreamReader(new FileInputStream(file), StandardCharsets.UTF_8)) {
            PlayerFile data = gson.fromJson(reader, PlayerFile.class);
            if (data == null) {
                // The file exists but produced no object: it is zero-length or holds a bare
                // "null". Gson does not throw for this, so it used to look identical to a
                // brand new player and the record was silently replaced with an empty one.
                quarantineUnreadableFile(uuid, file, "file is empty or contains no player object");
                return null;
            }
            // Ensure UUID is set (in case file was manually created)
            if (data.getUuid() == null) {
                data.setUuid(uuid);
            }
            return data;
        } catch (Exception e) {
            quarantineUnreadableFile(uuid, file, String.valueOf(e));
            return null;
        }
    }
    
    /**
     * Move a player file that cannot be parsed into {@code players/corrupted/} and log loudly.
     *
     * <p>Callers treat a null load as "new player" and will write a fresh empty record over
     * the same path, which previously turned an unreadable file into permanent loss of that
     * player's homes, wallet, and playtime. Moving the file aside keeps the original bytes
     * for manual recovery and makes the event visible in the log instead of silent.
     *
     * <p>This cannot reconstruct the data automatically; corrupt JSON is not recoverable
     * programmatically. It preserves the evidence and stops the overwrite.
     */
    private void quarantineUnreadableFile(UUID uuid, File file, String reason) {
        File corruptedFolder = new File(playersFolder, "corrupted");
        if (!corruptedFolder.exists() && !corruptedFolder.mkdirs()) {
            logger.severe("[PlayerFileStorage] Player file " + uuid + " is unreadable (" + reason
                    + ") and the players/corrupted/ folder could not be created. "
                    + "Leaving the file in place; back it up manually before this player reconnects, "
                    + "because a fresh empty record will otherwise replace it.");
            return;
        }
        
        File destination = new File(corruptedFolder, uuid + ".json." + System.currentTimeMillis());
        try {
            Files.move(file.toPath(), destination.toPath(), StandardCopyOption.REPLACE_EXISTING);
            logger.severe("[PlayerFileStorage] Player file " + uuid + " is unreadable (" + reason
                    + "). It has been moved to " + destination.getPath()
                    + " and this player will be treated as new (homes, balance, and playtime will "
                    + "start empty). Inspect that file to recover the data manually.");
        } catch (Exception e) {
            logger.severe("[PlayerFileStorage] Player file " + uuid + " is unreadable (" + reason
                    + ") and could not be moved to players/corrupted/: " + e
                    + ". Back the file up manually before this player reconnects.");
        }
    }
    
    /**
     * Save a player's data to disk.
     */
    public void savePlayer(UUID uuid) {
        trySavePlayer(uuid);
    }
    
    /**
     * Save a player's data to disk, reporting whether the data actually reached disk.
     *
     * @return true if the file was written, false if the save was skipped or failed.
     *         A false return means the in-memory copy is still the only copy of the
     *         data, so the caller must not discard it.
     */
    public boolean trySavePlayer(UUID uuid) {
        PlayerFile data = cache.get(uuid);
        if (data == null) {
            // Historically this silently returned, which hid bugs where callers marked a
            // player dirty without the file ever being loaded into cache (e.g. KitService
            // writes for a player whose PlayerReadyEvent never fired). Log a warning so
            // such cases are visible; callers should ensure the PlayerFile is cached first.
            logger.warning("[PlayerFileStorage] savePlayer called for uncached UUID " + uuid
                + " — save skipped. A caller tried to persist a player that was never loaded; "
                + "this usually means KitService (or similar) ran before the PlayerFile existed.");
            return false;
        }
        
        if (!writeAtomically(uuid, data)) {
            return false;
        }
        dirtyPlayers.remove(uuid);
        return true;
    }
    
    /**
     * Serialize and write a player file without any window in which the on-disk copy
     * is incomplete.
     *
     * <p>Two properties matter here, and the previous implementation had neither:
     *
     * <ol>
     *   <li><b>Serialize before touching the file.</b> {@code PlayerFile} holds plain
     *       {@code LinkedHashMap}/{@code ArrayList}/{@code HashSet} collections that other
     *       threads mutate without synchronization, so Gson can throw
     *       {@code ConcurrentModificationException} partway through writing. Streaming Gson
     *       straight into a {@code FileOutputStream} meant such a failure left a truncated
     *       file on disk. Building the JSON in memory first means a failed serialize leaves
     *       the previous good file completely untouched.</li>
     *   <li><b>Write to a temp file and rename.</b> {@code new FileOutputStream(file)}
     *       truncates the target immediately, so a crash or a full disk mid-write destroyed
     *       the only copy of the player's homes, wallet, and playtime. Writing
     *       {@code {uuid}.json.tmp} and then atomically renaming it over the target means a
     *       reader either sees the complete old file or the complete new one.</li>
     * </ol>
     *
     * @return true if the data is durably on disk under its final name.
     */
    private boolean writeAtomically(UUID uuid, PlayerFile data) {
        synchronized (writeLockFor(uuid)) {
            String json = serializeQuietly(uuid, data);
            if (json == null) {
                return false;
            }
            
            Path target = getPlayerFile(uuid).toPath();
            Path tmp = new File(playersFolder, uuid + ".json.tmp").toPath();
            try {
                Files.writeString(tmp, json, StandardCharsets.UTF_8);
                try {
                    Files.move(tmp, target,
                            StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
                } catch (AtomicMoveNotSupportedException e) {
                    // Some filesystems (notably certain network mounts) cannot do this
                    // atomically. A plain replace is still far better than truncate-in-place.
                    Files.move(tmp, target, StandardCopyOption.REPLACE_EXISTING);
                }
                return true;
            } catch (Exception e) {
                logger.severe("[PlayerFileStorage] Failed to save player file " + uuid + ": " + e
                        + " — the previous file on disk was left intact.");
                try {
                    Files.deleteIfExists(tmp);
                } catch (IOException ignored) {
                    // Leftover .tmp is harmless: it is never read and never matches the
                    // ".json" filter used when scanning the players folder.
                }
                return false;
            }
        }
    }
    
    /**
     * Serialize a player to JSON, retrying once on {@link ConcurrentModificationException}.
     * A CME here means another thread mutated one of the player's collections mid-write;
     * that is transient, so a single retry usually succeeds.
     */
    private String serializeQuietly(UUID uuid, PlayerFile data) {
        for (int attempt = 1; attempt <= 2; attempt++) {
            try {
                return gson.toJson(data);
            } catch (ConcurrentModificationException e) {
                if (attempt == 2) {
                    logger.severe("[PlayerFileStorage] Could not serialize player " + uuid
                            + ": data was being modified on another thread during both attempts. "
                            + "Save skipped; the previous file on disk was left intact.");
                    return null;
                }
            } catch (Exception e) {
                logger.severe("[PlayerFileStorage] Could not serialize player " + uuid + ": " + e
                        + " — save skipped, the previous file on disk was left intact.");
                return null;
            }
        }
        return null;
    }
    
    private Object writeLockFor(UUID uuid) {
        return writeLocks.computeIfAbsent(uuid, k -> new Object());
    }
    
    /**
     * Save a player's data to disk (direct, for migration).
     */
    public void savePlayerDirect(PlayerFile data) {
        if (data == null || data.getUuid() == null) return;
        
        writeAtomically(data.getUuid(), data);
        
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
        String lower = name.toLowerCase();
        // Exact match first
        UUID exact = nameIndex.get(lower);
        if (exact != null) {
            return Optional.of(exact);
        }
        // Fallback: starts-with partial match (matches online NameMatching.DEFAULT behavior)
        for (Map.Entry<String, UUID> entry : nameIndex.entrySet()) {
            if (entry.getKey().startsWith(lower)) {
                return Optional.of(entry.getValue());
            }
        }
        return Optional.empty();
    }
    
    /**
     * Get a player's data by name (case-insensitive).
     * Returns null if player doesn't exist.
     */
    public PlayerFile getPlayerByName(String name) {
        UUID uuid = getUuidByName(name).orElse(null);
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
     *
     * <p>If that save fails, the entry is deliberately kept in the cache and left dirty.
     * Evicting it would discard the only copy of the session's data, so instead it stays
     * eligible for retry by {@link #saveAllDirty()} and the shutdown {@link #saveAll()}.
     */
    public void unloadPlayer(UUID uuid) {
        if (dirtyPlayers.contains(uuid)) {
            if (!trySavePlayer(uuid)) {
                logger.severe("[PlayerFileStorage] Could not persist player " + uuid
                        + " on unload; keeping the data in memory so it is not lost. "
                        + "It will be retried on the next save and at shutdown.");
                return;
            }
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