package com.eliteessentials.services;

import com.eliteessentials.config.ConfigManager;
import com.eliteessentials.model.Location;

import java.util.Map;
import java.util.Random;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Service for random teleportation functionality.
 * Handles cooldowns and random location generation.
 */
public class RtpService {

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
     * 
     * @param playerId Player UUID
     * @return Remaining cooldown in seconds, or 0 if not on cooldown
     */
    public int getCooldownRemaining(UUID playerId) {
        Long lastUse = cooldowns.get(playerId);
        if (lastUse == null) return 0;
        
        long elapsed = (System.currentTimeMillis() - lastUse) / 1000;
        int cooldown = configManager.getRtpCooldown();
        
        return Math.max(0, cooldown - (int) elapsed);
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

    /**
     * Generate a random location within the configured range.
     * 
     * @param centerX Center X coordinate (e.g., world spawn or player location)
     * @param centerZ Center Z coordinate
     * @param world World name
     * @return A random location (Y will need to be calculated for safe ground)
     */
    public Location generateRandomLocation(double centerX, double centerZ, String world) {
        // Get world-specific range or default
        var rtpConfig = configManager.getConfig().rtp;
        var worldRange = rtpConfig.getRangeForWorld(world);
        
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
            locations[i] = generateRandomLocation(centerX, centerZ, world);
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
