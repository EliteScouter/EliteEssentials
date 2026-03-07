package com.eliteessentials.util;

import java.util.List;
import java.util.regex.Pattern;

/**
 * Shared utility for checking whether a world name matches a blacklist.
 * Supports wildcard patterns using * (e.g. "arena*" matches "Arena_1vs1-standard_1771781348556_3").
 * All comparisons are case-insensitive.
 */
public final class WorldBlacklistUtil {

    private WorldBlacklistUtil() {}

    /**
     * Check if a world name matches any pattern in the blacklist.
     *
     * @param worldName        the current world name
     * @param blacklistedWorlds list of patterns (exact names or wildcard with *)
     * @return true if the world is blacklisted
     */
    public static boolean isWorldBlacklisted(String worldName, List<String> blacklistedWorlds) {
        if (blacklistedWorlds == null || blacklistedWorlds.isEmpty() || worldName == null) {
            return false;
        }
        for (String pattern : blacklistedWorlds) {
            if (worldMatchesPattern(worldName, pattern)) {
                return true;
            }
        }
        return false;
    }

    /**
     * Check if a world name matches a single pattern.
     * Without *, does a case-insensitive exact match.
     * With *, splits on * and builds a regex with .* between quoted literal segments.
     */
    public static boolean worldMatchesPattern(String worldName, String pattern) {
        if (pattern == null || pattern.isEmpty()) return false;

        if (!pattern.contains("*")) {
            return pattern.equalsIgnoreCase(worldName);
        }

        String[] parts = pattern.split("\\*", -1);
        StringBuilder sb = new StringBuilder("(?i)");
        for (int i = 0; i < parts.length; i++) {
            if (i > 0) sb.append(".*");
            sb.append(Pattern.quote(parts[i]));
        }
        return worldName.matches(sb.toString());
    }
}
