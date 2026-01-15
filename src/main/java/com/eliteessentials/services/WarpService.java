package com.eliteessentials.services;

import com.eliteessentials.config.ConfigManager;
import com.eliteessentials.model.Location;
import com.eliteessentials.model.Warp;
import com.eliteessentials.storage.WarpStorage;

import java.util.*;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * Service for managing server warps.
 */
public class WarpService {

    private static final Pattern VALID_NAME_PATTERN = Pattern.compile("^[a-zA-Z0-9_-]+$");
    private static final int MAX_NAME_LENGTH = 32;

    private final WarpStorage storage;
    private final ConfigManager configManager;

    public WarpService(WarpStorage storage, ConfigManager configManager) {
        this.storage = storage;
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
     */
    public List<Warp> getAccessibleWarps(boolean isOp) {
        return storage.getAllWarps().values().stream()
                .filter(warp -> isOp || warp.getPermission() == Warp.Permission.ALL)
                .sorted(Comparator.comparing(Warp::getName))
                .collect(Collectors.toList());
    }

    /**
     * Get warp names accessible to a player.
     */
    public Set<String> getAccessibleWarpNames(boolean isOp) {
        return getAccessibleWarps(isOp).stream()
                .map(Warp::getName)
                .collect(Collectors.toSet());
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
}
