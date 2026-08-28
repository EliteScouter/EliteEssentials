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
 * Manages player mutes, permanent and temporary.
 * Persists to mutes.json keyed by UUID string.
 *
 * A temp mute carries a muteEndTimestamp; a permanent mute leaves it at 0.
 * Expired temp mutes are dropped on load and lazily on read, so a mute that ran
 * out while the server was down does not come back.
 */
public class MuteService {

    private static final Logger logger = Logger.getLogger("EliteEssentials");
    private static final Gson gson = new GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create();
    private static final Type MUTE_MAP_TYPE = new TypeToken<Map<String, MuteEntry>>(){}.getType();

    private final File muteFile;
    private final Object fileLock = new Object();
    private final Map<String, MuteEntry> mutes = new ConcurrentHashMap<>();

    public MuteService(File dataFolder) {
        this.muteFile = new File(dataFolder, "mutes.json");
        load();
    }

    public void load() {
        if (!muteFile.exists()) {
            return;
        }
        synchronized (fileLock) {
            try (Reader reader = new InputStreamReader(new FileInputStream(muteFile), StandardCharsets.UTF_8)) {
                Map<String, MuteEntry> loaded = gson.fromJson(reader, MUTE_MAP_TYPE);
                mutes.clear();
                if (loaded != null) {
                    // Drop expired temp mutes on load. Entries written before temp mutes
                    // existed have no muteEndTimestamp, so Gson leaves it at 0 = permanent.
                    long now = System.currentTimeMillis();
                    for (Map.Entry<String, MuteEntry> entry : loaded.entrySet()) {
                        MuteEntry mute = entry.getValue();
                        if (mute.muteEndTimestamp <= 0 || mute.muteEndTimestamp > now) {
                            mutes.put(entry.getKey(), mute);
                        }
                    }
                    int expired = loaded.size() - mutes.size();
                    if (expired > 0) {
                        logger.info("[MuteService] Cleaned up " + expired + " expired temp mutes.");
                        save();
                    }
                }
                logger.info("[MuteService] Loaded " + mutes.size() + " muted players.");
            } catch (IOException e) {
                logger.severe("Could not load mutes.json: " + e.getMessage());
            }
        }
    }

    private void save() {
        synchronized (fileLock) {
            try (Writer writer = new OutputStreamWriter(new FileOutputStream(muteFile), StandardCharsets.UTF_8)) {
                gson.toJson(mutes, MUTE_MAP_TYPE, writer);
            } catch (IOException e) {
                logger.severe("Could not save mutes.json: " + e.getMessage());
            }
        }
    }

    public boolean mute(UUID playerId, String playerName, String mutedBy, String reason) {
        return addMute(playerId, playerName, mutedBy, reason, 0L);
    }

    /**
     * Temporarily mute a player.
     *
     * @param durationMs duration in milliseconds from now
     * @return false if the player already has an active mute
     */
    public boolean tempMute(UUID playerId, String playerName, String mutedBy, String reason, long durationMs) {
        if (durationMs <= 0) {
            return false;
        }
        return addMute(playerId, playerName, mutedBy, reason, System.currentTimeMillis() + durationMs);
    }

    /**
     * @param muteEndTimestamp epoch millis when the mute expires, or 0 for permanent
     */
    private boolean addMute(UUID playerId, String playerName, String mutedBy, String reason, long muteEndTimestamp) {
        String key = playerId.toString();
        // An expired temp mute must not block a new one
        if (getActiveEntry(playerId) != null) {
            return false;
        }
        MuteEntry entry = new MuteEntry();
        entry.playerName = playerName;
        entry.mutedBy = mutedBy;
        entry.reason = reason;
        entry.mutedAt = System.currentTimeMillis();
        entry.muteEndTimestamp = muteEndTimestamp;
        mutes.put(key, entry);
        save();
        return true;
    }

    public boolean unmute(UUID playerId) {
        // Purge first so unmuting an already-expired temp mute reports "not muted"
        purgeExpired();
        if (mutes.remove(playerId.toString()) != null) {
            save();
            return true;
        }
        return false;
    }
    /**
     * Unmute by player name (for offline players where UUID may not be known).
     * @return the UUID that was unmuted, or null if not found
     */
    public UUID unmuteByName(String playerName) {
        purgeExpired();
        for (Map.Entry<String, MuteEntry> entry : mutes.entrySet()) {
            if (entry.getValue().playerName != null && entry.getValue().playerName.equalsIgnoreCase(playerName)) {
                UUID uuid = UUID.fromString(entry.getKey());
                mutes.remove(entry.getKey());
                save();
                return uuid;
            }
        }
        return null;
    }

    public boolean isMuted(UUID playerId) {
        return getActiveEntry(playerId) != null;
    }

    public MuteEntry getMuteEntry(UUID playerId) {
        return getActiveEntry(playerId);
    }

    /**
     * Get the mute entry for a player, dropping it first if it is an expired temp mute.
     * @return the active entry, or null if the player is not muted
     */
    private MuteEntry getActiveEntry(UUID playerId) {
        String key = playerId.toString();
        MuteEntry entry = mutes.get(key);
        if (entry == null) {
            return null;
        }
        if (entry.isExpired()) {
            mutes.remove(key);
            save();
            return null;
        }
        return entry;
    }

    /** Whether the player's active mute is a temp mute (as opposed to permanent). */
    public boolean isTempMuted(UUID playerId) {
        MuteEntry entry = getActiveEntry(playerId);
        return entry != null && !entry.isPermanent();
    }

    /**
     * Get remaining mute time in milliseconds.
     * @return 0 if not muted or permanently muted
     */
    public long getRemainingTime(UUID playerId) {
        MuteEntry entry = getActiveEntry(playerId);
        if (entry == null || entry.isPermanent()) {
            return 0;
        }
        return entry.getRemainingTime();
    }

    /**
     * Get the configured "you are muted" message for a player, including the
     * remaining time when the mute is temporary.
     * Returns the permanent variant if the player is not muted, so callers that
     * already checked {@link #isMuted(UUID)} never get an empty string.
     */
    public String getMuteBlockedMessage(UUID playerId) {
        var plugin = com.eliteessentials.EliteEssentials.getInstance();
        var configManager = plugin != null ? plugin.getConfigManager() : null;
        if (configManager == null) {
            return "You are muted and cannot send messages.";
        }
        MuteEntry entry = getActiveEntry(playerId);
        if (entry != null && !entry.isPermanent()) {
            return configManager.getMessage("tempmutedBlocked",
                "time", TempBanService.formatDuration(entry.getRemainingTime()));
        }
        return configManager.getMessage("mutedBlocked");
    }

    /** Get the number of active mutes. */
    public int getMuteCount() {
        purgeExpired();
        return mutes.size();
    }

    /** Get all mute entries (unmodifiable view). */
    public Map<String, MuteEntry> getAllMutes() {
        purgeExpired();
        return Collections.unmodifiableMap(mutes);
    }

    /** Drop every expired temp mute. Saves only if something was removed. */
    private void purgeExpired() {
        boolean removed = mutes.entrySet().removeIf(e -> e.getValue().isExpired());
        if (removed) {
            save();
        }
    }

    public void reload() {
        load();
    }

    public static class MuteEntry {
        public String playerName;
        public String mutedBy;
        public String reason;
        public long mutedAt;
        /** Epoch millis when a temp mute expires. 0 means the mute is permanent. */
        public long muteEndTimestamp;

        public boolean isPermanent() {
            return muteEndTimestamp <= 0;
        }

        public boolean isExpired() {
            return !isPermanent() && System.currentTimeMillis() >= muteEndTimestamp;
        }

        /** Remaining time in milliseconds. Always 0 for a permanent mute. */
        public long getRemainingTime() {
            if (isPermanent()) {
                return 0;
            }
            return Math.max(0, muteEndTimestamp - System.currentTimeMillis());
        }
    }
}
