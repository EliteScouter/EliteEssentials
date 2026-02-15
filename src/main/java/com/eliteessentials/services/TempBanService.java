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
 * Manages temporary player bans with expiration.
 * Persists to tempbans.json keyed by UUID string.
 */
public class TempBanService {

    private static final Logger logger = Logger.getLogger("EliteEssentials");
    private static final Gson gson = new GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create();
    private static final Type TEMPBAN_MAP_TYPE = new TypeToken<Map<String, TempBanEntry>>(){}.getType();

    private final File tempBanFile;
    private final Object fileLock = new Object();
    private final Map<String, TempBanEntry> tempBans = new ConcurrentHashMap<>();

    public TempBanService(File dataFolder) {
        this.tempBanFile = new File(dataFolder, "tempbans.json");
        load();
    }

    public void load() {
        if (!tempBanFile.exists()) {
            return;
        }
        synchronized (fileLock) {
            try (Reader reader = new InputStreamReader(new FileInputStream(tempBanFile), StandardCharsets.UTF_8)) {
                Map<String, TempBanEntry> loaded = gson.fromJson(reader, TEMPBAN_MAP_TYPE);
                tempBans.clear();
                if (loaded != null) {
                    // Clean up expired bans on load
                    long now = System.currentTimeMillis();
                    for (Map.Entry<String, TempBanEntry> entry : loaded.entrySet()) {
                        if (entry.getValue().banEndTimestamp > now) {
                            tempBans.put(entry.getKey(), entry.getValue());
                        }
                    }
                    int expired = loaded.size() - tempBans.size();
                    if (expired > 0) {
                        logger.info("[TempBanService] Cleaned up " + expired + " expired temp bans.");
                        save();
                    }
                }
                logger.info("[TempBanService] Loaded " + tempBans.size() + " temp banned players.");
            } catch (IOException e) {
                logger.severe("Could not load tempbans.json: " + e.getMessage());
            }
        }
    }

    private void save() {
        synchronized (fileLock) {
            try (Writer writer = new OutputStreamWriter(new FileOutputStream(tempBanFile), StandardCharsets.UTF_8)) {
                gson.toJson(tempBans, TEMPBAN_MAP_TYPE, writer);
            } catch (IOException e) {
                logger.severe("Could not save tempbans.json: " + e.getMessage());
            }
        }
    }

    /**
     * Temp ban a player.
     * @param durationMs duration in milliseconds from now
     */
    public boolean tempBan(UUID playerId, String playerName, String bannedBy, String reason, long durationMs) {
        String key = playerId.toString();
        if (tempBans.containsKey(key)) {
            return false;
        }
        TempBanEntry entry = new TempBanEntry();
        entry.playerName = playerName;
        entry.bannedBy = bannedBy;
        entry.reason = reason;
        entry.bannedAt = System.currentTimeMillis();
        entry.banEndTimestamp = System.currentTimeMillis() + durationMs;
        tempBans.put(key, entry);
        save();
        return true;
    }

    public boolean unban(UUID playerId) {
        if (tempBans.remove(playerId.toString()) != null) {
            save();
            return true;
        }
        return false;
    }

    /**
     * Check if a player is currently temp banned (not expired).
     */
    public boolean isTempBanned(UUID playerId) {
        TempBanEntry entry = tempBans.get(playerId.toString());
        if (entry == null) return false;
        if (entry.isExpired()) {
            // Auto-remove expired ban
            tempBans.remove(playerId.toString());
            save();
            return false;
        }
        return true;
    }

    public TempBanEntry getTempBanEntry(UUID playerId) {
        return tempBans.get(playerId.toString());
    }

    /**
     * Get remaining ban time in milliseconds, or 0 if not banned/expired.
     */
    public long getRemainingTime(UUID playerId) {
        TempBanEntry entry = tempBans.get(playerId.toString());
        if (entry == null) return 0;
        long remaining = entry.banEndTimestamp - System.currentTimeMillis();
        return Math.max(0, remaining);
    }

    public void reload() {
        load();
    }

    /**
     * Format a duration in milliseconds to a human-readable string.
     */
    public static String formatDuration(long millis) {
        long seconds = millis / 1000;
        long minutes = seconds / 60;
        long hours = minutes / 60;
        long days = hours / 24;

        if (days > 0) {
            long remainingHours = hours % 24;
            if (remainingHours > 0) {
                return days + " day" + (days != 1 ? "s" : "") + " " + remainingHours + " hour" + (remainingHours != 1 ? "s" : "");
            }
            return days + " day" + (days != 1 ? "s" : "");
        }
        if (hours > 0) {
            long remainingMinutes = minutes % 60;
            if (remainingMinutes > 0) {
                return hours + " hour" + (hours != 1 ? "s" : "") + " " + remainingMinutes + " minute" + (remainingMinutes != 1 ? "s" : "");
            }
            return hours + " hour" + (hours != 1 ? "s" : "");
        }
        if (minutes > 0) {
            return minutes + " minute" + (minutes != 1 ? "s" : "");
        }
        return seconds + " second" + (seconds != 1 ? "s" : "");
    }

    /**
     * Parse a time string like "1d", "2h", "30m", "1d12h" into milliseconds.
     * Supports: d(ays), h(ours), m(inutes), s(econds)
     */
    public static long parseTime(String input) {
        if (input == null || input.isEmpty()) return -1;
        long total = 0;
        StringBuilder num = new StringBuilder();
        for (char c : input.toLowerCase().toCharArray()) {
            if (Character.isDigit(c)) {
                num.append(c);
            } else {
                if (num.length() == 0) return -1;
                long value = Long.parseLong(num.toString());
                switch (c) {
                    case 'd': total += value * 86400000L; break;
                    case 'h': total += value * 3600000L; break;
                    case 'm': total += value * 60000L; break;
                    case 's': total += value * 1000L; break;
                    default: return -1;
                }
                num.setLength(0);
            }
        }
        // If there are trailing digits with no unit, treat as minutes
        if (num.length() > 0) {
            total += Long.parseLong(num.toString()) * 60000L;
        }
        return total > 0 ? total : -1;
    }

    public static class TempBanEntry {
        public String playerName;
        public String bannedBy;
        public String reason;
        public long bannedAt;
        public long banEndTimestamp;

        public boolean isExpired() {
            return System.currentTimeMillis() >= banEndTimestamp;
        }

        public long getRemainingTime() {
            return Math.max(0, banEndTimestamp - System.currentTimeMillis());
        }
    }
}
