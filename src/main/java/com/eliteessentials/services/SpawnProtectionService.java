package com.eliteessentials.services;

import com.eliteessentials.config.ConfigManager;
import com.eliteessentials.permissions.PermissionService;
import com.eliteessentials.permissions.Permissions;
import com.eliteessentials.storage.SpawnStorage;
import com.hypixel.hytale.math.vector.Vector3d;
import com.hypixel.hytale.math.vector.Vector3i;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Service for managing spawn protection.
 * Protects blocks within a configurable radius of spawn from being modified.
 * Supports per-world spawn protection - each world with a /setspawn is protected.
 */
public class SpawnProtectionService {

    private final ConfigManager configManager;
    
    // Per-world spawn coordinates
    private final Map<String, SpawnLocation> worldSpawns = new HashMap<>();

    public SpawnProtectionService(ConfigManager configManager) {
        this.configManager = configManager;
    }

    /**
     * Set the spawn location for a specific world.
     */
    public void setSpawnLocation(String worldName, double x, double y, double z) {
        worldSpawns.put(worldName, new SpawnLocation(x, y, z));
    }
    
    /**
     * Load spawn locations from SpawnStorage.
     */
    public void loadFromStorage(SpawnStorage spawnStorage) {
        worldSpawns.clear();
        for (String worldName : spawnStorage.getWorldsWithSpawn()) {
            SpawnStorage.SpawnData spawn = spawnStorage.getSpawn(worldName);
            if (spawn != null) {
                worldSpawns.put(worldName, new SpawnLocation(spawn.x, spawn.y, spawn.z));
            }
        }
    }

    /**
     * Check if spawn protection is enabled and at least one spawn is set.
     */
    public boolean isEnabled() {
        return configManager.getConfig().spawnProtection.enabled && !worldSpawns.isEmpty();
    }
    
    /**
     * Check if a specific world has spawn protection.
     */
    public boolean hasSpawnInWorld(String worldName) {
        return worldSpawns.containsKey(worldName);
    }

    /**
     * Check if PvP protection is enabled in spawn area.
     */
    public boolean isPvpProtectionEnabled() {
        return configManager.getConfig().spawnProtection.disablePvp;
    }
    
    /**
     * Check if ALL damage protection is enabled in spawn area.
     */
    public boolean isAllDamageProtectionEnabled() {
        return configManager.getConfig().spawnProtection.disableAllDamage;
    }
    
    /**
     * Check if block interactions are disabled in spawn area.
     */
    public boolean isInteractionProtectionEnabled() {
        return configManager.getConfig().spawnProtection.disableInteractions;
    }
    
    /**
     * Check if item pickups are disabled in spawn area.
     * NOTE: May not work properly due to Hytale API limitations.
     */
    public boolean isItemPickupProtectionEnabled() {
        return configManager.getConfig().spawnProtection.disableItemPickup;
    }
    
    /**
     * Check if item drops are disabled in spawn area.
     */
    public boolean isItemDropProtectionEnabled() {
        return configManager.getConfig().spawnProtection.disableItemDrop;
    }

    /**
     * Get the protection radius.
     */
    public int getRadius() {
        return configManager.getConfig().spawnProtection.radius;
    }

    /**
     * Check if X/Z coordinates are within the protected spawn area of a specific world.
     */
    public boolean isInProtectedArea(String worldName, int x, int z) {
        if (!isEnabled()) return false;
        
        SpawnLocation spawn = worldSpawns.get(worldName);
        if (spawn == null) return false;
        
        int radius = getRadius();
        double dx = Math.abs(x - spawn.x);
        double dz = Math.abs(z - spawn.z);

        return dx <= radius && dz <= radius;
    }

    /**
     * Check if a block position is within the protected spawn area of a specific world.
     */
    public boolean isInProtectedArea(String worldName, Vector3i blockPos) {
        if (!isEnabled()) return false;
        
        SpawnLocation spawn = worldSpawns.get(worldName);
        if (spawn == null) return false;
        
        int radius = getRadius();
        double dx = Math.abs(blockPos.getX() - spawn.x);
        double dz = Math.abs(blockPos.getZ() - spawn.z);

        // Square radius check (X/Z only)
        if (dx > radius || dz > radius) {
            return false;
        }

        // Check Y range if configured
        return isInYRange(blockPos.getY());
    }
    
    /**
     * Check if a block position is within ANY protected spawn area.
     * Used when world name is not available.
     */
    public boolean isInProtectedArea(Vector3i blockPos) {
        if (!isEnabled()) return false;
        
        for (SpawnLocation spawn : worldSpawns.values()) {
            int radius = getRadius();
            double dx = Math.abs(blockPos.getX() - spawn.x);
            double dz = Math.abs(blockPos.getZ() - spawn.z);

            if (dx <= radius && dz <= radius && isInYRange(blockPos.getY())) {
                return true;
            }
        }
        return false;
    }

    /**
     * Check if an entity position is within the protected spawn area of a specific world.
     */
    public boolean isInProtectedArea(String worldName, Vector3d entityPos) {
        if (!isEnabled()) return false;
        
        SpawnLocation spawn = worldSpawns.get(worldName);
        if (spawn == null) return false;
        
        int radius = getRadius();
        double dx = Math.abs(entityPos.getX() - spawn.x);
        double dz = Math.abs(entityPos.getZ() - spawn.z);

        if (dx > radius || dz > radius) {
            return false;
        }

        return isInYRange((int) entityPos.getY());
    }
    
    /**
     * Check if an entity position is within ANY protected spawn area.
     * Used when world name is not available.
     */
    public boolean isInProtectedArea(Vector3d entityPos) {
        if (!isEnabled()) return false;
        
        for (SpawnLocation spawn : worldSpawns.values()) {
            int radius = getRadius();
            double dx = Math.abs(entityPos.getX() - spawn.x);
            double dz = Math.abs(entityPos.getZ() - spawn.z);

            if (dx <= radius && dz <= radius && isInYRange((int) entityPos.getY())) {
                return true;
            }
        }
        return false;
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
     * Check if a player can bypass spawn protection (for block breaking/placing).
     * Note: This does NOT bypass damage protection - admins should still be protected from damage.
     */
    public boolean canBypass(UUID playerId) {
        return PermissionService.get().hasPermission(playerId, Permissions.SPAWN_PROTECTION_BYPASS);
    }
    
    /**
     * Check if a player can bypass damage protection.
     * By default, nobody bypasses damage protection (even admins are protected).
     * This is intentional - you want admins to be safe at spawn too!
     */
    public boolean canBypassDamageProtection(UUID playerId) {
        // Nobody bypasses damage protection by default - everyone is protected at spawn
        return false;
    }
    
    /**
     * Get spawn location for a world (for debugging).
     */
    public SpawnLocation getSpawnForWorld(String worldName) {
        return worldSpawns.get(worldName);
    }
    
    /**
     * Get all worlds with spawn protection.
     */
    public java.util.Set<String> getProtectedWorlds() {
        return worldSpawns.keySet();
    }

    /**
     * Simple holder for spawn coordinates.
     */
    public static class SpawnLocation {
        public final double x;
        public final double y;
        public final double z;
        
        public SpawnLocation(double x, double y, double z) {
            this.x = x;
            this.y = y;
            this.z = z;
        }
    }
}
