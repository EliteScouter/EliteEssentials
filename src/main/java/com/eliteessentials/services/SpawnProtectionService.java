package com.eliteessentials.services;

import com.eliteessentials.config.ConfigManager;
import com.eliteessentials.permissions.PermissionService;
import com.eliteessentials.permissions.Permissions;
import com.eliteessentials.storage.SpawnStorage;
import org.joml.Vector3d;
import org.joml.Vector3i;

import java.util.*;

/**
 * Service for managing spawn protection.
 * Protects blocks within a configurable radius of spawn points from being modified.
 * Supports per-world, multi-spawn protection - each spawn point with protection=true
 * gets its own protection zone.
 */
public class SpawnProtectionService {

    private final ConfigManager configManager;
    
    // Per-world spawn coordinates (supports multiple per world)
    private final Map<String, List<SpawnLocation>> worldSpawns = new HashMap<>();

    public SpawnProtectionService(ConfigManager configManager) {
        this.configManager = configManager;
    }

    /**
     * Set a single spawn location for a world (backward compatible).
     * Replaces all spawn locations for that world with just this one.
     */
    public void setSpawnLocation(String worldName, double x, double y, double z) {
        List<SpawnLocation> list = new ArrayList<>();
        list.add(new SpawnLocation(x, y, z));
        worldSpawns.put(worldName, list);
    }
    
    /**
     * Load spawn locations from SpawnStorage.
     * Only loads spawn points that have protection=true.
     */
    public void loadFromStorage(SpawnStorage spawnStorage) {
        worldSpawns.clear();
        Map<String, List<SpawnStorage.SpawnData>> protectedSpawns = spawnStorage.getAllProtectedSpawns();
        for (Map.Entry<String, List<SpawnStorage.SpawnData>> entry : protectedSpawns.entrySet()) {
            List<SpawnLocation> locations = new ArrayList<>();
            for (SpawnStorage.SpawnData spawn : entry.getValue()) {
                locations.add(new SpawnLocation(spawn.x, spawn.y, spawn.z));
            }
            if (!locations.isEmpty()) {
                worldSpawns.put(entry.getKey(), locations);
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
        List<SpawnLocation> list = worldSpawns.get(worldName);
        return list != null && !list.isEmpty();
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
     * Check if X/Z coordinates are within ANY protected spawn area of a specific world.
     */
    public boolean isInProtectedArea(String worldName, int x, int z) {
        if (!isEnabled()) return false;
        
        List<SpawnLocation> spawns = worldSpawns.get(worldName);
        if (spawns == null) return false;
        
        int radius = getRadius();
        for (SpawnLocation spawn : spawns) {
            double dx = Math.abs(x - spawn.x);
            double dz = Math.abs(z - spawn.z);
            if (dx <= radius && dz <= radius) {
                return true;
            }
        }
        return false;
    }

    /**
     * Check if a block position is within ANY protected spawn area of a specific world.
     */
    public boolean isInProtectedArea(String worldName, Vector3i blockPos) {
        if (!isEnabled()) return false;
        
        List<SpawnLocation> spawns = worldSpawns.get(worldName);
        if (spawns == null) return false;
        
        int radius = getRadius();
        for (SpawnLocation spawn : spawns) {
            double dx = Math.abs(blockPos.x - spawn.x);
            double dz = Math.abs(blockPos.z - spawn.z);
            
            if (dx <= radius && dz <= radius && isInYRange(blockPos.y)) {
                return true;
            }
        }
        return false;
    }
    
    /**
     * Check if a block position is within ANY protected spawn area (any world).
     * Used when world name is not available.
     */
    public boolean isInProtectedArea(Vector3i blockPos) {
        if (!isEnabled()) return false;
        
        int radius = getRadius();
        for (List<SpawnLocation> spawns : worldSpawns.values()) {
            for (SpawnLocation spawn : spawns) {
                double dx = Math.abs(blockPos.x - spawn.x);
                double dz = Math.abs(blockPos.z - spawn.z);

                if (dx <= radius && dz <= radius && isInYRange(blockPos.y)) {
                    return true;
                }
            }
        }
        return false;
    }

    /**
     * Check if an entity position is within ANY protected spawn area of a specific world.
     */
    public boolean isInProtectedArea(String worldName, Vector3d entityPos) {
        if (!isEnabled()) return false;
        
        List<SpawnLocation> spawns = worldSpawns.get(worldName);
        if (spawns == null) return false;
        
        int radius = getRadius();
        for (SpawnLocation spawn : spawns) {
            double dx = Math.abs(entityPos.x - spawn.x);
            double dz = Math.abs(entityPos.z - spawn.z);

            if (dx <= radius && dz <= radius && isInYRange((int) entityPos.y)) {
                return true;
            }
        }
        return false;
    }
    
    /**
     * Check if an entity position is within ANY protected spawn area (any world).
     * Used when world name is not available.
     */
    public boolean isInProtectedArea(Vector3d entityPos) {
        if (!isEnabled()) return false;
        
        int radius = getRadius();
        for (List<SpawnLocation> spawns : worldSpawns.values()) {
            for (SpawnLocation spawn : spawns) {
                double dx = Math.abs(entityPos.x - spawn.x);
                double dz = Math.abs(entityPos.z - spawn.z);

                if (dx <= radius && dz <= radius && isInYRange((int) entityPos.y)) {
                    return true;
                }
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

        if (minY == -1 && maxY == -1) {
            return true;
        }

        if (minY != -1 && y < minY) return false;
        if (maxY != -1 && y > maxY) return false;

        return true;
    }

    /**
     * Check if a player can bypass spawn protection (for block breaking/placing).
     */
    public boolean canBypass(UUID playerId) {
        return PermissionService.get().hasPermission(playerId, Permissions.SPAWN_PROTECTION_BYPASS);
    }
    
    /**
     * Check if a player can bypass damage protection.
     * Nobody bypasses damage protection by default.
     */
    public boolean canBypassDamageProtection(UUID playerId) {
        return false;
    }
    
    /**
     * Get spawn locations for a world (for debugging).
     * Returns the first spawn location for backward compatibility.
     */
    public SpawnLocation getSpawnForWorld(String worldName) {
        List<SpawnLocation> list = worldSpawns.get(worldName);
        return (list != null && !list.isEmpty()) ? list.get(0) : null;
    }
    
    /**
     * Get all worlds with spawn protection.
     */
    public Set<String> getProtectedWorlds() {
        Set<String> result = new HashSet<>();
        for (Map.Entry<String, List<SpawnLocation>> entry : worldSpawns.entrySet()) {
            if (!entry.getValue().isEmpty()) {
                result.add(entry.getKey());
            }
        }
        return result;
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
