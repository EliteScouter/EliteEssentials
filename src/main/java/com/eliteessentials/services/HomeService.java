package com.eliteessentials.services;

import com.eliteessentials.config.ConfigManager;
import com.eliteessentials.model.Home;
import com.eliteessentials.model.Location;
import com.eliteessentials.permissions.PermissionService;
import com.eliteessentials.storage.HomeStorage;

import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.logging.Logger;

/**
 * Service for managing player homes.
 * Supports permission-based home limits via:
 * - eliteessentials.limit.homes.<number> (e.g., eliteessentials.limit.homes.5)
 * - eliteessentials.limit.homes.unlimited
 */
public class HomeService {

    private static final Logger logger = Logger.getLogger("EliteEssentials");

    private final HomeStorage storage;
    private final ConfigManager configManager;

    public HomeService(HomeStorage storage, ConfigManager configManager) {
        this.storage = storage;
        this.configManager = configManager;
    }

    /**
     * Result of a home operation.
     */
    public enum Result {
        SUCCESS,
        HOME_NOT_FOUND,
        LIMIT_REACHED,
        INVALID_NAME
    }

    /**
     * Set a home for a player at their current location.
     * 
     * @param playerId Player UUID
     * @param name Home name
     * @param location Player's current location
     * @return Result of the operation
     */
    public Result setHome(UUID playerId, String name, Location location) {
        if (name == null || name.isBlank()) {
            return Result.INVALID_NAME;
        }

        String normalizedName = name.toLowerCase().trim();
        
        // Check if updating existing home (doesn't count against limit)
        boolean isUpdate = storage.hasHome(playerId, normalizedName);
        
        if (!isUpdate) {
            int maxHomes = getMaxHomes(playerId);
            if (storage.getHomeCount(playerId) >= maxHomes) {
                return Result.LIMIT_REACHED;
            }
        }

        Home home = new Home(normalizedName, location);
        storage.setHome(playerId, home);
        
        // Persist immediately to disk so data isn't lost on crash
        storage.save();
        
        logger.info("Player " + playerId + " set home '" + normalizedName + "' at " + location);
        return Result.SUCCESS;
    }

    /**
     * Get a home location for a player.
     */
    public Optional<Home> getHome(UUID playerId, String name) {
        if (name == null || name.isBlank()) {
            return Optional.empty();
        }
        return storage.getHome(playerId, name.toLowerCase().trim());
    }

    /**
     * Delete a home for a player.
     */
    public Result deleteHome(UUID playerId, String name) {
        if (name == null || name.isBlank()) {
            return Result.INVALID_NAME;
        }
        
        boolean deleted = storage.deleteHome(playerId, name.toLowerCase().trim());
        if (!deleted) {
            return Result.HOME_NOT_FOUND;
        }
        
        // Persist the change to disk
        storage.save();
        
        logger.info("Player " + playerId + " deleted home '" + name + "'");
        return Result.SUCCESS;
    }

    /**
     * Get all homes for a player.
     */
    public Map<String, Home> getHomes(UUID playerId) {
        return storage.getHomes(playerId);
    }

    /**
     * Get all home names for a player.
     */
    public Set<String> getHomeNames(UUID playerId) {
        return storage.getHomeNames(playerId);
    }

    /**
     * Get the number of homes a player has.
     */
    public int getHomeCount(UUID playerId) {
        return storage.getHomeCount(playerId);
    }

    /**
     * Get the maximum number of homes allowed for a player.
     * Checks permissions in this order:
     * 1. eliteessentials.limit.homes.unlimited - returns Integer.MAX_VALUE
     * 2. eliteessentials.limit.homes.<number> - returns highest matching number
     * 3. Config default (homes.maxHomes)
     * 
     * @param playerId Player UUID
     * @return Maximum homes allowed
     */
    public int getMaxHomes(UUID playerId) {
        return PermissionService.get().getMaxHomes(playerId);
    }

    /**
     * Save all home data to disk.
     */
    public void save() {
        storage.save();
    }
}
