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
 * Manages permanent player bans.
 * Persists to bans.json keyed by UUID string.
 */
public class BanService {

    private static final Logger logger = Logger.getLogger("EliteEssentials");
    private static final Gson gson = new GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create();
    private static final Type BAN_MAP_TYPE = new TypeToken<Map<String, BanEntry>>(){}.getType();

    private final File banFile;
    private final Object fileLock = new Object();
    private final Map<String, BanEntry> bans = new ConcurrentHashMap<>();

    public BanService(File dataFolder) {
        this.banFile = new File(dataFolder, "bans.json");
        load();
    }

    public void load() {
        if (!banFile.exists()) {
            return;
        }
        synchronized (fileLock) {
            try (Reader reader = new InputStreamReader(new FileInputStream(banFile), StandardCharsets.UTF_8)) {
                Map<String, BanEntry> loaded = gson.fromJson(reader, BAN_MAP_TYPE);
                bans.clear();
                if (loaded != null) {
                    bans.putAll(loaded);
                }
                logger.info("[BanService] Loaded " + bans.size() + " banned players.");
            } catch (IOException e) {
                logger.severe("Could not load bans.json: " + e.getMessage());
            }
        }
    }

    private void save() {
        synchronized (fileLock) {
            try (Writer writer = new OutputStreamWriter(new FileOutputStream(banFile), StandardCharsets.UTF_8)) {
                gson.toJson(bans, BAN_MAP_TYPE, writer);
            } catch (IOException e) {
                logger.severe("Could not save bans.json: " + e.getMessage());
            }
        }
    }

    public boolean ban(UUID playerId, String playerName, String bannedBy, String reason) {
        String key = playerId.toString();
        if (bans.containsKey(key)) {
            return false;
        }
        BanEntry entry = new BanEntry();
        entry.playerName = playerName;
        entry.bannedBy = bannedBy;
        entry.reason = reason;
        entry.bannedAt = System.currentTimeMillis();
        bans.put(key, entry);
        save();
        return true;
    }

    public boolean unban(UUID playerId) {
        if (bans.remove(playerId.toString()) != null) {
            save();
            return true;
        }
        return false;
    }

    /**
     * Unban by player name (for offline players where UUID may not be known).
     * @return the UUID that was unbanned, or null if not found
     */
    public UUID unbanByName(String playerName) {
        for (Map.Entry<String, BanEntry> entry : bans.entrySet()) {
            if (entry.getValue().playerName != null && entry.getValue().playerName.equalsIgnoreCase(playerName)) {
                UUID uuid = UUID.fromString(entry.getKey());
                bans.remove(entry.getKey());
                save();
                return uuid;
            }
        }
        return null;
    }

    public boolean isBanned(UUID playerId) {
        return bans.containsKey(playerId.toString());
    }

    public BanEntry getBanEntry(UUID playerId) {
        return bans.get(playerId.toString());
    }

    public void reload() {
        load();
    }

    public static class BanEntry {
        public String playerName;
        public String bannedBy;
        public String reason;
        public long bannedAt;
    }
}
