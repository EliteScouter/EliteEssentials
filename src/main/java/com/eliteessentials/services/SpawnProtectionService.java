package com.eliteessentials.services;

import com.eliteessentials.config.ConfigManager;
import com.eliteessentials.permissions.PermissionService;
import com.eliteessentials.permissions.Permissions;
import com.hypixel.hytale.math.vector.Vector3d;
import com.hypixel.hytale.math.vector.Vector3i;

import java.util.UUID;

/**
 * Service for managing spawn protection.
 * Protects blocks within a configurable radius of spawn from being modified.
 * Spawn location is set via /setspawn command.
 */
public class SpawnProtectionService {

    private final ConfigManager configManager;
    
    // Spawn coordinates (set via /setspawn)
    private double spawnX = 0;
    private double spawnY = 64;
    private double spawnZ = 0;
    private boolean spawnSet = false;

    public SpawnProtectionService(ConfigManager configManager) {
        this.configManager = configManager;
    }

    /**
     * Set the spawn location for protection calculations.
     */
    public void setSpawnLocation(double x, double y, double z) {
        this.spawnX = x;
        this.spawnY = y;
        this.spawnZ = z;
        this.spawnSet = true;
    }

    /**
     * Check if spawn protection is enabled and spawn is set.
     */
    public boolean isEnabled() {
        return configManager.getConfig().spawnProtection.enabled && spawnSet;
    }

    /**
     * Check if PvP protection is enabled in spawn area.
     */
    public boolean isPvpProtectionEnabled() {
        return configManager.getConfig().spawnProtection.disablePvp;
    }

    /**
     * Get the protection radius.
     */
    public int getRadius() {
        return configManager.getConfig().spawnProtection.radius;
    }

    /**
     * Check if a block position is within the protected spawn area.
     */
    public boolean isInProtectedArea(Vector3i blockPos) {
        if (!isEnabled()) return false;
        
        int radius = getRadius();
        double dx = Math.abs(blockPos.getX() - spawnX);
        double dz = Math.abs(blockPos.getZ() - spawnZ);

        // Square radius check (X/Z only)
        if (dx > radius || dz > radius) {
            return false;
        }

        // Check Y range if configured
        return isInYRange(blockPos.getY());
    }

    /**
     * Check if an entity position is within the protected spawn area.
     */
    public boolean isInProtectedArea(Vector3d entityPos) {
        if (!isEnabled()) return false;
        
        int radius = getRadius();
        double dx = Math.abs(entityPos.getX() - spawnX);
        double dz = Math.abs(entityPos.getZ() - spawnZ);

        if (dx > radius || dz > radius) {
            return false;
        }

        return isInYRange((int) entityPos.getY());
    }

    /**
     * Check if a Y coordinate is within the configured Y range.
     */
    private boolean isInYRange(int y) {
        int minY = configManager.getConfig().spawnProtection.minY;
        int maxY = configManager.getConfig().spawnProtection.maxY;

        // If both are -1, Y range is disabled (protect all Y levels)
        if (minY == -1 && maxY == -1) {
            return true;
        }

        if (minY != -1 && y < minY) return false;
        if (maxY != -1 && y > maxY) return false;

        return true;
    }

    /**
     * Check if a player can bypass spawn protection.
     */
    public boolean canBypass(UUID playerId) {
        return PermissionService.get().hasPermission(playerId, Permissions.SPAWN_PROTECTION_BYPASS);
    }

    public double getSpawnX() { return spawnX; }
    public double getSpawnY() { return spawnY; }
    public double getSpawnZ() { return spawnZ; }
    public boolean isSpawnSet() { return spawnSet; }
}
