package com.eliteessentials.services;

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
 * Manages player warnings with persistence.
 * Persists to warns.json keyed by UUID string -> List of WarnEntry.
 */
public class WarnService {

    private static final Logger logger = Logger.getLogger("EliteEssentials");
    private static final Gson gson = new GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create();
    private static final Type WARN_MAP_TYPE = new TypeToken<Map<String, List<WarnEntry>>>(){}.getType();

    private final File warnFile;
    private final Object fileLock = new Object();
    private final Map<String, List<WarnEntry>> warns = new ConcurrentHashMap<>();

    public WarnService(File dataFolder) {
        this.warnFile = new File(dataFolder, "warns.json");
        load();
    }

    public void load() {
        if (!warnFile.exists()) {
            return;
        }
        synchronized (fileLock) {
            try (Reader reader = new InputStreamReader(new FileInputStream(warnFile), StandardCharsets.UTF_8)) {
                Map<String, List<WarnEntry>> loaded = gson.fromJson(reader, WARN_MAP_TYPE);
                warns.clear();
                if (loaded != null) {
                    warns.putAll(loaded);
                }
                logger.info("[WarnService] Loaded warnings for " + warns.size() + " players.");
            } catch (IOException e) {
                logger.severe("Could not load warns.json: " + e.getMessage());
            }
        }
    }

    private void save() {
        synchronized (fileLock) {
            try (Writer writer = new OutputStreamWriter(new FileOutputStream(warnFile), StandardCharsets.UTF_8)) {
                gson.toJson(warns, WARN_MAP_TYPE, writer);
            } catch (IOException e) {
                logger.severe("Could not save warns.json: " + e.getMessage());
            }
        }
    }

    /**
     * Add a warning to a player.
     * @return the new total warning count for this player
     */
    public int warn(UUID playerId, String playerName, String warnedBy, String reason) {
        String key = playerId.toString();
        WarnEntry entry = new WarnEntry();
        entry.playerName = playerName;
        entry.warnedBy = warnedBy;
        entry.reason = reason;
        entry.warnedAt = System.currentTimeMillis();

        warns.computeIfAbsent(key, k -> new ArrayList<>()).add(entry);
        save();
        return warns.get(key).size();
    }

    /**
     * Get all warnings for a player.
     * @return list of warnings, or empty list if none
     */
    public List<WarnEntry> getWarnings(UUID playerId) {
        List<WarnEntry> list = warns.get(playerId.toString());
        return list != null ? Collections.unmodifiableList(list) : Collections.emptyList();
    }

    /**
     * Get the warning count for a player.
     */
    public int getWarningCount(UUID playerId) {
        List<WarnEntry> list = warns.get(playerId.toString());
        return list != null ? list.size() : 0;
    }

    /**
     * Remove a single warning by index (0-based).
     * @return true if the warning was removed
     */
    public boolean removeWarning(UUID playerId, int index) {
        String key = playerId.toString();
        List<WarnEntry> list = warns.get(key);
        if (list == null || index < 0 || index >= list.size()) {
            return false;
        }
        list.remove(index);
        if (list.isEmpty()) {
            warns.remove(key);
        }
        save();
        return true;
    }

    /**
     * Clear all warnings for a player.
     * @return the number of warnings that were cleared
     */
    public int clearWarnings(UUID playerId) {
        List<WarnEntry> removed = warns.remove(playerId.toString());
        if (removed != null) {
            save();
            return removed.size();
        }
        return 0;
    }

    public void reload() {
        load();
    }

    public static class WarnEntry {
        public String playerName;
        public String warnedBy;
        public String reason;
        public long warnedAt;
    }
}
