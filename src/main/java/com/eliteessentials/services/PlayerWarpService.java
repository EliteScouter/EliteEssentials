package com.eliteessentials.services;

import com.eliteessentials.config.ConfigManager;
import com.eliteessentials.integration.HyperPermsIntegration;
import com.eliteessentials.integration.LuckPermsIntegration;
import com.eliteessentials.model.Location;
import com.eliteessentials.model.PlayerWarp;
import com.eliteessentials.permissions.PermissionService;
import com.eliteessentials.permissions.Permissions;
import com.eliteessentials.storage.PlayerWarpStorageProvider;

import java.util.*;
import java.util.regex.Pattern;
import java.util.logging.Logger;

/**
 * Service for managing player-created warps.
 * Handles creation, deletion, visibility toggling, and limit enforcement.
 */
public class PlayerWarpService {

    private static final Logger logger = Logger.getLogger("EliteEssentials");
    private static final Pattern VALID_NAME_PATTERN = Pattern.compile("^[a-zA-Z0-9_-]+$");
    private static final int MAX_NAME_LENGTH = 32;
    private static final int MAX_DESCRIPTION_LENGTH = 100;

    private final PlayerWarpStorageProvider storage;
    private ConfigManager configManager;

    public PlayerWarpService(PlayerWarpStorageProvider storage) {
        this.storage = storage;
    }

    public void setConfigManager(ConfigManager configManager) {
        this.configManager = configManager;
    }

    public enum Result {
        SUCCESS,
        INVALID_NAME,
        NAME_TAKEN,
        LIMIT_REACHED,
        NOT_FOUND,
        NOT_OWNER,
        DESCRIPTION_TOO_LONG
    }

    /**
     * Validate a player warp name.
     * @return error message if invalid, null if valid
     */
    public String validateName(String name) {
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
     * Create a new player warp.
     */
    public Result createWarp(String name, Location location, UUID ownerId, String ownerName,
                             PlayerWarp.Visibility visibility, String description) {
        String validationError = validateName(name);
        if (validationError != null) {
            return Result.INVALID_NAME;
        }

        if (description != null && description.length() > MAX_DESCRIPTION_LENGTH) {
            return Result.DESCRIPTION_TOO_LONG;
        }

        if (storage.hasWarp(name)) {
            return Result.NAME_TAKEN;
        }

        int limit = getWarpLimit(ownerId);
        if (limit != -1 && storage.getWarpCountByOwner(ownerId) >= limit) {
            return Result.LIMIT_REACHED;
        }

        PlayerWarp warp = new PlayerWarp(name, location, ownerId, ownerName, visibility, description);
        storage.setWarp(warp);
        logger.info("Player " + ownerName + " (" + ownerId + ") created player warp '" + name + "' [" + visibility + "]");
        return Result.SUCCESS;
    }

    /**
     * Delete a player warp. Only the owner or an admin can delete.
     */
    public Result deleteWarp(String name, UUID requesterId, boolean isAdmin) {
        Optional<PlayerWarp> warpOpt = storage.getWarp(name);
        if (warpOpt.isEmpty()) {
            return Result.NOT_FOUND;
        }

        PlayerWarp warp = warpOpt.get();
        if (!isAdmin && !warp.getOwnerId().equals(requesterId)) {
            return Result.NOT_OWNER;
        }

        storage.deleteWarp(name);
        logger.info("Player warp '" + name + "' deleted by " + requesterId);
        return Result.SUCCESS;
    }

    /**
     * Toggle visibility of a player warp. Only the owner can toggle.
     */
    public Result toggleVisibility(String name, UUID requesterId) {
        Optional<PlayerWarp> warpOpt = storage.getWarp(name);
        if (warpOpt.isEmpty()) {
            return Result.NOT_FOUND;
        }

        PlayerWarp warp = warpOpt.get();
        if (!warp.getOwnerId().equals(requesterId)) {
            return Result.NOT_OWNER;
        }

        PlayerWarp.Visibility newVis = warp.isPublic()
                ? PlayerWarp.Visibility.PRIVATE
                : PlayerWarp.Visibility.PUBLIC;
        warp.setVisibility(newVis);
        storage.setWarp(warp);
        return Result.SUCCESS;
    }

    /**
     * Update a player warp's description. Only the owner can update.
     */
    public Result updateDescription(String name, UUID requesterId, String description) {
        Optional<PlayerWarp> warpOpt = storage.getWarp(name);
        if (warpOpt.isEmpty()) {
            return Result.NOT_FOUND;
        }

        PlayerWarp warp = warpOpt.get();
        if (!warp.getOwnerId().equals(requesterId)) {
            return Result.NOT_OWNER;
        }

        if (description != null && description.length() > MAX_DESCRIPTION_LENGTH) {
            return Result.DESCRIPTION_TOO_LONG;
        }

        warp.setDescription(description);
        storage.setWarp(warp);
        return Result.SUCCESS;
    }

    /**
     * Update a player warp's location. Only the owner can update.
     */
    public Result updateLocation(String name, UUID requesterId, Location location) {
        Optional<PlayerWarp> warpOpt = storage.getWarp(name);
        if (warpOpt.isEmpty()) {
            return Result.NOT_FOUND;
        }

        PlayerWarp warp = warpOpt.get();
        if (!warp.getOwnerId().equals(requesterId)) {
            return Result.NOT_OWNER;
        }

        warp.setLocation(location);
        storage.setWarp(warp);
        return Result.SUCCESS;
    }

    public Optional<PlayerWarp> getWarp(String name) {
        return storage.getWarp(name);
    }

    public boolean warpExists(String name) {
        return storage.hasWarp(name);
    }

    public List<PlayerWarp> getWarpsByOwner(UUID ownerId) {
        return storage.getWarpsByOwner(ownerId);
    }

    public int getWarpCountByOwner(UUID ownerId) {
        return storage.getWarpCountByOwner(ownerId);
    }

    public List<PlayerWarp> getPublicWarps() {
        return storage.getPublicWarps();
    }

    /**
     * Get warps accessible to a player (their own private + all public).
     */
    public List<PlayerWarp> getAccessibleWarps(UUID playerId) {
        return storage.getAccessibleWarps(playerId);
    }

    /**
     * Get the warp limit for a player based on permissions/groups.
     * Returns -1 for unlimited.
     */
    public int getWarpLimit(UUID playerId) {
        if (configManager == null) {
            return -1;
        }

        var config = configManager.getConfig().playerWarps;

        if (configManager.getConfig().advancedPermissions) {
            if (PermissionService.get().hasPermission(playerId, Permissions.PWARP_LIMIT_UNLIMITED)) {
                return -1;
            }

            // Try LuckPerms permission-based limit
            if (LuckPermsIntegration.isAvailable()) {
                int lpLimit = LuckPermsIntegration.getPermissionValue(playerId, Permissions.PWARP_LIMIT_PREFIX);
                if (lpLimit > 0) {
                    return lpLimit;
                }
            }

            // Try HyperPerms as fallback
            if (HyperPermsIntegration.isAvailable()) {
                int hpLimit = HyperPermsIntegration.getHighestPermissionValue(playerId, Permissions.PWARP_LIMIT_PREFIX);
                if (hpLimit > 0) {
                    return hpLimit;
                }
            }

            // Check group-based limits
            Set<String> groups = new HashSet<>();
            if (LuckPermsIntegration.isAvailable()) {
                groups.addAll(LuckPermsIntegration.getGroups(playerId));
            } else if (HyperPermsIntegration.isAvailable()) {
                groups.addAll(HyperPermsIntegration.getGroups(playerId));
            }
            int groupLimit = getHighestGroupLimit(groups, config.groupLimits);
            if (groupLimit != Integer.MIN_VALUE) {
                return groupLimit;
            }
        }

        return config.maxWarps;
    }

    private int getHighestGroupLimit(Set<String> playerGroups, Map<String, Integer> groupLimits) {
        int highest = Integer.MIN_VALUE;
        for (String group : playerGroups) {
            for (Map.Entry<String, Integer> entry : groupLimits.entrySet()) {
                if (entry.getKey().equalsIgnoreCase(group)) {
                    int limit = entry.getValue();
                    if (limit == -1) return -1;
                    if (limit > highest) highest = limit;
                }
            }
        }
        return highest;
    }

    public boolean canCreateWarp(UUID playerId) {
        int limit = getWarpLimit(playerId);
        if (limit == -1) return true;
        return storage.getWarpCountByOwner(playerId) < limit;
    }

    public void reload() {
        storage.load();
    }

    public void save() {
        storage.save();
    }
}
