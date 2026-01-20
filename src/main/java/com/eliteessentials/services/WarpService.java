package com.eliteessentials.services;

import com.eliteessentials.config.ConfigManager;
import com.eliteessentials.integration.LuckPermsIntegration;
import com.eliteessentials.model.Location;
import com.eliteessentials.model.Warp;
import com.eliteessentials.permissions.PermissionService;
import com.eliteessentials.permissions.Permissions;
import com.eliteessentials.storage.WarpStorage;

import java.util.*;
import java.util.regex.Pattern;

/**
 * Service for managing server warps.
 */
public class WarpService {

    private static final Pattern VALID_NAME_PATTERN = Pattern.compile("^[a-zA-Z0-9_-]+$");
    private static final int MAX_NAME_LENGTH = 32;

    private final WarpStorage storage;
    private ConfigManager configManager;

    public WarpService(WarpStorage storage) {
        this.storage = storage;
    }
    
    /**
     * Set the config manager for warp limits.
     */
    public void setConfigManager(ConfigManager configManager) {
        this.configManager = configManager;
    }

    /**
     * Validate a warp name.
     * @return error message if invalid, null if valid
     */
    public String validateWarpName(String name) {
        if (name == null || name.isEmpty()) {
            return "Warp name cannot be empty.";
        }
        if (name.length() > MAX_NAME_LENGTH) {
            return "Warp name cannot be longer than " + MAX_NAME_LENGTH + " characters.";
        }
        if (!VALID_NAME_PATTERN.matcher(name).matches()) {
            return "Warp name must be alphanumeric (letters, numbers, underscore, dash).";
        }
        return null;
    }

    /**
     * Create or update a warp.
     * @return error message if failed, null if successful
     */
    public String setWarp(String name, Location location, Warp.Permission permission, String createdBy) {
        String validationError = validateWarpName(name);
        if (validationError != null) {
            return validationError;
        }

        Warp warp = new Warp(name, location, permission, createdBy);
        storage.setWarp(warp);
        return null;
    }

    /**
     * Get a warp by name.
     */
    public Optional<Warp> getWarp(String name) {
        return storage.getWarp(name);
    }

    /**
     * Delete a warp.
     * @return true if deleted, false if not found
     */
    public boolean deleteWarp(String name) {
        return storage.deleteWarp(name);
    }

    /**
     * Check if a warp exists.
     */
    public boolean warpExists(String name) {
        return storage.hasWarp(name);
    }

    /**
     * Get all warps as a Map.
     */
    public Map<String, Warp> getAllWarps() {
        return storage.getAllWarps();
    }

    /**
     * Get all warps as a List.
     */
    public List<Warp> getAllWarpsList() {
        return new ArrayList<>(storage.getAllWarps().values());
    }

    /**
     * Get warps accessible to a player (based on OP status).
     * Uses traditional loop instead of streams for better performance in hot paths.
     */
    public List<Warp> getAccessibleWarps(boolean isOp) {
        List<Warp> result = new ArrayList<>();
        for (Warp warp : storage.getAllWarps().values()) {
            if (isOp || warp.getPermission() == Warp.Permission.ALL) {
                result.add(warp);
            }
        }
        result.sort(Comparator.comparing(Warp::getName));
        return result;
    }

    /**
     * Get warp names accessible to a player.
     * Uses traditional loop instead of streams for better performance.
     */
    public Set<String> getAccessibleWarpNames(boolean isOp) {
        Set<String> result = new HashSet<>();
        for (Warp warp : storage.getAllWarps().values()) {
            if (isOp || warp.getPermission() == Warp.Permission.ALL) {
                result.add(warp.getName());
            }
        }
        return result;
    }

    /**
     * Check if a player can use a specific warp.
     */
    public boolean canUseWarp(Warp warp, boolean isOp) {
        if (warp == null) return false;
        return isOp || warp.getPermission() == Warp.Permission.ALL;
    }

    /**
     * Update warp permission level.
     */
    public boolean updateWarpPermission(String name, Warp.Permission permission) {
        Optional<Warp> warpOpt = storage.getWarp(name);
        if (warpOpt.isEmpty()) {
            return false;
        }
        Warp warp = warpOpt.get();
        warp.setPermission(permission);
        storage.setWarp(warp);
        return true;
    }

    /**
     * Save warps to disk.
     */
    public void save() {
        storage.save();
    }
    
    /**
     * Get the warp limit for a player based on their permissions/groups.
     * Returns -1 for unlimited.
     */
    public int getWarpLimit(UUID playerId) {
        if (configManager == null) {
            return -1; // No limit if config not set
        }
        
        var config = configManager.getConfig().warps;
        
        // Check if advanced permissions mode
        if (configManager.getConfig().advancedPermissions) {
            // Check for unlimited permission
            if (PermissionService.get().hasPermission(playerId, Permissions.WARP_LIMIT_UNLIMITED)) {
                return -1;
            }
            
            // Check for specific limit permissions (highest wins)
            int highestLimit = -1;
            boolean foundLimit = false;
            for (int i = 100; i >= 1; i--) {
                if (PermissionService.get().hasPermission(playerId, Permissions.warpLimit(i))) {
                    highestLimit = i;
                    foundLimit = true;
                    break;
                }
            }
            
            // If found a permission-based limit, use it
            if (foundLimit) {
                return highestLimit;
            }
            
            // Check group-based limits from config
            Set<String> groups = new HashSet<>(LuckPermsIntegration.getGroups(playerId));
            int groupLimit = getHighestGroupLimit(groups, config.groupLimits);
            if (groupLimit != Integer.MIN_VALUE) {
                return groupLimit;
            }
        }
        
        // Fall back to global config limit
        return config.maxWarps;
    }
    
    /**
     * Get the highest warp limit from a player's groups.
     * Returns Integer.MIN_VALUE if no group limit found.
     */
    private int getHighestGroupLimit(Set<String> playerGroups, Map<String, Integer> groupLimits) {
        int highest = Integer.MIN_VALUE;
        
        for (String group : playerGroups) {
            // Check case-insensitive
            for (Map.Entry<String, Integer> entry : groupLimits.entrySet()) {
                if (entry.getKey().equalsIgnoreCase(group)) {
                    int limit = entry.getValue();
                    // -1 means unlimited, which is always highest
                    if (limit == -1) {
                        return -1;
                    }
                    if (limit > highest) {
                        highest = limit;
                    }
                }
            }
        }
        
        return highest;
    }
    
    /**
     * Check if a player can create more warps.
     * @return true if player can create more warps, false if at limit
     */
    public boolean canCreateWarp(UUID playerId) {
        int limit = getWarpLimit(playerId);
        if (limit == -1) {
            return true; // Unlimited
        }
        
        int currentCount = getWarpCount();
        return currentCount < limit;
    }
    
    /**
     * Get the total number of warps.
     */
    public int getWarpCount() {
        return storage.getAllWarps().size();
    }
}
