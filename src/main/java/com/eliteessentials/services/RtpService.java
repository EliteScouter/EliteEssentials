package com.eliteessentials.services;

import com.eliteessentials.config.ConfigManager;
import com.eliteessentials.config.PluginConfig;
import com.eliteessentials.integration.HyperPermsIntegration;
import com.eliteessentials.integration.LuckPermsIntegration;
import com.eliteessentials.model.Location;
import com.eliteessentials.permissions.PermissionService;
import com.eliteessentials.permissions.Permissions;

import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Logger;

/**
 * Service for random teleportation functionality.
 * Handles cooldowns, per-player range resolution, and random location generation.
 */
public class RtpService {

    private static final Logger logger = Logger.getLogger("EliteEssentials");
    private static final Random random = new Random();

    private final ConfigManager configManager;
    
    // UUID -> Last RTP timestamp
    private final Map<UUID, Long> cooldowns = new ConcurrentHashMap<>();

    public RtpService(ConfigManager configManager) {
        this.configManager = configManager;
    }

    /**
     * Result of an RTP operation.
     */
    public enum Result {
        SUCCESS,
        ON_COOLDOWN,
        NO_SAFE_LOCATION,
        WORLD_NOT_FOUND
    }

    /**
     * Check if a player is on cooldown.
     * Uses permission-based cooldown overrides if available.
     * 
     * @param playerId Player UUID
     * @return Remaining cooldown in seconds, or 0 if not on cooldown
     */
    public int getCooldownRemaining(UUID playerId) {
        Long lastUse = cooldowns.get(playerId);
        if (lastUse == null) return 0;
        
        long elapsed = (System.currentTimeMillis() - lastUse) / 1000;
        
        // Get effective cooldown (checks permission overrides)
        int cooldown = getEffectiveCooldown(playerId);
        
        return Math.max(0, cooldown - (int) elapsed);
    }
    
    /**
     * Get the effective cooldown for a player based on permissions.
     * Checks for permission-based cooldown overrides like eliteessentials.command.tp.cooldown.rtp.300
     * 
     * @param playerId Player UUID
     * @return Effective cooldown in seconds
     */
    public int getEffectiveCooldown(UUID playerId) {
        int defaultCooldown = configManager.getRtpCooldown();
        return PermissionService.get().getTpCommandCooldown(playerId, "rtp", defaultCooldown);
    }

    /**
     * Check if a player can use RTP (not on cooldown).
     */
    public boolean canUseRtp(UUID playerId) {
        return getCooldownRemaining(playerId) == 0;
    }

    /**
     * Set the cooldown for a player (called after successful RTP).
     */
    public void setCooldown(UUID playerId) {
        cooldowns.put(playerId, System.currentTimeMillis());
    }

    // ==================== RANGE RESOLUTION ====================

    /**
     * Get the effective RTP range for a player in a world.
     *
     * Resolution order (first match wins):
     * 1. Named permission range - rtp.permissionRanges entry whose
     *    eliteessentials.command.tp.rtp.range.&lt;name&gt; node the player holds
     * 2. Group range - rtp.groupRanges entry matching one of the player's
     *    LuckPerms / HyperPerms groups
     * 3. Per-world range - rtp.worldRanges entry for this world
     * 4. Global rtp.minRange / rtp.maxRange
     *
     * Steps 1 and 2 only apply in advanced permissions mode. When a player matches
     * several entries the one with the largest maxRange wins, tie-broken by the
     * smaller minRange.
     *
     * @param playerId Player being teleported, or null to skip permission resolution
     * @param worldName World the player is being teleported within
     * @return The effective range, never null and always valid (maxRange &gt;= minRange &gt;= 0)
     */
    public PluginConfig.WorldRtpRange getRangeFor(UUID playerId, String worldName) {
        PluginConfig.RtpConfig rtpConfig = configManager.getConfig().rtp;

        if (playerId != null && configManager.getConfig().advancedPermissions) {
            PluginConfig.WorldRtpRange permissionRange = resolvePermissionRange(playerId, rtpConfig);
            if (permissionRange != null) {
                return sanitize(permissionRange, "permission");
            }

            PluginConfig.WorldRtpRange groupRange = resolveGroupRange(playerId, rtpConfig);
            if (groupRange != null) {
                return sanitize(groupRange, "group");
            }
        }

        return sanitize(rtpConfig.getRangeForWorld(worldName), "world");
    }

    /**
     * Find the best range from rtp.permissionRanges the player has the node for.
     * @return the matching range, or null if the player holds none
     */
    private PluginConfig.WorldRtpRange resolvePermissionRange(UUID playerId, PluginConfig.RtpConfig rtpConfig) {
        if (rtpConfig.permissionRanges == null || rtpConfig.permissionRanges.isEmpty()) {
            return null;
        }

        PluginConfig.WorldRtpRange best = null;
        String bestName = null;

        for (Map.Entry<String, PluginConfig.WorldRtpRange> entry : rtpConfig.permissionRanges.entrySet()) {
            if (entry.getKey() == null || entry.getValue() == null) {
                continue;
            }
            if (PermissionService.get().hasPermission(playerId, Permissions.rtpRange(entry.getKey()))) {
                if (isBetter(entry.getValue(), best)) {
                    best = entry.getValue();
                    bestName = entry.getKey();
                }
            }
        }

        if (best != null && configManager.isDebugEnabled()) {
            logger.info("[RTP] Using permission range '" + bestName + "': min=" + best.minRange
                + ", max=" + best.maxRange);
        }
        return best;
    }

    /**
     * Find the best range from rtp.groupRanges matching one of the player's groups.
     * @return the matching range, or null if no group matches
     */
    private PluginConfig.WorldRtpRange resolveGroupRange(UUID playerId, PluginConfig.RtpConfig rtpConfig) {
        if (rtpConfig.groupRanges == null || rtpConfig.groupRanges.isEmpty()) {
            return null;
        }

        List<String> groups;
        if (LuckPermsIntegration.isAvailable()) {
            groups = LuckPermsIntegration.getGroups(playerId);
        } else if (HyperPermsIntegration.isAvailable()) {
            groups = HyperPermsIntegration.getGroups(playerId);
        } else {
            return null;
        }
        if (groups == null || groups.isEmpty()) {
            return null;
        }

        PluginConfig.WorldRtpRange best = null;
        String bestGroup = null;

        for (String group : groups) {
            if (group == null) {
                continue;
            }
            for (Map.Entry<String, PluginConfig.WorldRtpRange> entry : rtpConfig.groupRanges.entrySet()) {
                if (entry.getKey() == null || entry.getValue() == null) {
                    continue;
                }
                if (entry.getKey().equalsIgnoreCase(group) && isBetter(entry.getValue(), best)) {
                    best = entry.getValue();
                    bestGroup = entry.getKey();
                }
            }
        }

        if (best != null && configManager.isDebugEnabled()) {
            logger.info("[RTP] Using group range '" + bestGroup + "': min=" + best.minRange
                + ", max=" + best.maxRange);
        }
        return best;
    }

    /**
     * A candidate beats the current best if it reaches further out, or reaches the
     * same distance but starts closer in.
     */
    private boolean isBetter(PluginConfig.WorldRtpRange candidate, PluginConfig.WorldRtpRange best) {
        if (best == null) {
            return true;
        }
        if (candidate.maxRange != best.maxRange) {
            return candidate.maxRange > best.maxRange;
        }
        return candidate.minRange < best.minRange;
    }

    /**
     * Return a valid copy of a configured range so a bad config value cannot break
     * the location search (a negative range, or maxRange below minRange, would make
     * the random distance calculation throw).
     */
    private PluginConfig.WorldRtpRange sanitize(PluginConfig.WorldRtpRange range, String source) {
        if (range == null) {
            var rtpConfig = configManager.getConfig().rtp;
            return new PluginConfig.WorldRtpRange(Math.max(0, rtpConfig.minRange),
                Math.max(Math.max(0, rtpConfig.minRange), rtpConfig.maxRange));
        }

        int min = Math.max(0, range.minRange);
        int max = Math.max(min, range.maxRange);
        if (min != range.minRange || max != range.maxRange) {
            logger.warning("[RTP] Invalid " + source + " range (min=" + range.minRange
                + ", max=" + range.maxRange + ") - using min=" + min + ", max=" + max
                + ". Check your rtp config.");
        }
        return new PluginConfig.WorldRtpRange(min, max);
    }

    /**
     * Generate a random location within the configured range.
     * 
     * @param centerX Center X coordinate (e.g., world spawn or player location)
     * @param centerZ Center Z coordinate
     * @param world World name
     * @return A random location (Y will need to be calculated for safe ground)
     */
    public Location generateRandomLocation(double centerX, double centerZ, String world) {
        return generateRandomLocation(null, centerX, centerZ, world);
    }

    /**
     * Generate a random location within the range that applies to a specific player.
     *
     * @param playerId Player being teleported, or null to ignore permission ranges
     * @param centerX Center X coordinate (e.g., world spawn or player location)
     * @param centerZ Center Z coordinate
     * @param world World name
     * @return A random location (Y will need to be calculated for safe ground)
     */
    public Location generateRandomLocation(UUID playerId, double centerX, double centerZ, String world) {
        var worldRange = getRangeFor(playerId, world);
        
        int minRange = worldRange.minRange;
        int maxRange = worldRange.maxRange;
        
        // Generate random distance and angle
        int distance = minRange + random.nextInt(maxRange - minRange + 1);
        double angle = random.nextDouble() * 2 * Math.PI;
        
        // Calculate coordinates
        double x = centerX + (distance * Math.cos(angle));
        double z = centerZ + (distance * Math.sin(angle));
        
        // Y will be set by the caller after finding safe ground
        return new Location(world, x, 0, z);
    }

    /**
     * Generate multiple random location candidates.
     * 
     * @param centerX Center X
     * @param centerZ Center Z
     * @param world World name
     * @param count Number of locations to generate
     * @return Array of random locations
     */
    public Location[] generateRandomLocations(double centerX, double centerZ, String world, int count) {
        Location[] locations = new Location[count];
        for (int i = 0; i < count; i++) {
            locations[i] = generateRandomLocation(null, centerX, centerZ, world);
        }
        return locations;
    }

    /**
     * Get the maximum number of attempts for finding a safe location.
     */
    public int getMaxAttempts() {
        return configManager.getRtpMaxAttempts();
    }

    /**
     * Clear a player's cooldown (admin command).
     */
    public void clearCooldown(UUID playerId) {
        cooldowns.remove(playerId);
    }

    /**
     * Clear all cooldowns.
     */
    public void clearAllCooldowns() {
        cooldowns.clear();
    }
}
