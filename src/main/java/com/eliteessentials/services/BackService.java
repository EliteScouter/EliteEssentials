package com.eliteessentials.services;

import com.eliteessentials.config.ConfigManager;
import com.eliteessentials.model.Location;
import com.eliteessentials.permissions.PermissionService;
import com.eliteessentials.permissions.Permissions;
import com.eliteessentials.storage.BackStorage;

import java.util.*;
import java.util.logging.Logger;

/**
 * Service for tracking player locations and providing /back functionality.
 * Maintains a history of locations for each player, persisted to JSON.
 */
public class BackService {

    private static final Logger logger = Logger.getLogger("EliteEssentials");

    private final ConfigManager configManager;
    private final BackStorage backStorage;

    public BackService(ConfigManager configManager, BackStorage backStorage) {
        this.configManager = configManager;
        this.backStorage = backStorage;
        
        // Configure storage with max history from config
        backStorage.setMaxHistory(configManager.getBackMaxHistory());
    }

    /**
     * Record a location for a player (called before teleports).
     * 
     * @param playerId Player UUID
     * @param location Location to record
     */
    public void pushLocation(UUID playerId, Location location) {
        if (location == null || playerId == null) return;
        
        backStorage.pushLocation(playerId, location);
        logger.fine("Recorded location for " + playerId + ": " + location);
    }

    /**
     * Record a death location for a player.
     * - Simple mode: Records if backOnDeath is enabled in config (everyone gets it)
     * - Advanced mode: Records only if player has eliteessentials.command.tp.back.ondeath permission
     * 
     * @param playerId Player UUID
     * @param location Death location
     */
    public void pushDeathLocation(UUID playerId, Location location) {
        if (!configManager.isBackOnDeathEnabled()) {
            logger.fine("Back on death disabled in config, not recording death location for " + playerId);
            return;
        }
        
        // In advanced permissions mode, check if player has the back.ondeath permission
        if (configManager.isAdvancedPermissions()) {
            if (!PermissionService.get().hasPermission(playerId, Permissions.BACK_ONDEATH)) {
                if (configManager.isDebugEnabled()) {
                    logger.info("[BackService] Player " + playerId + " lacks " + Permissions.BACK_ONDEATH + " permission, not recording death location");
                }
                return;
            }
        }
        
        if (configManager.isDebugEnabled()) {
            logger.info("[BackService] Recording DEATH location for " + playerId + ": " + 
                String.format("%.1f, %.1f, %.1f", location.getX(), location.getY(), location.getZ()));
        }
        pushLocation(playerId, location);
    }

    /**
     * Get the most recent location for a player without removing it.
     */
    public Optional<Location> peekLocation(UUID playerId) {
        return backStorage.peekLocation(playerId);
    }

    /**
     * Pop and return the most recent location for a player.
     * 
     * @param playerId Player UUID
     * @return The previous location, or empty if none
     */
    public Optional<Location> popLocation(UUID playerId) {
        Optional<Location> location = backStorage.popLocation(playerId);
        location.ifPresent(loc -> logger.fine("Popped location for " + playerId + ": " + loc));
        return location;
    }

    /**
     * Get the number of stored locations for a player.
     */
    public int getHistorySize(UUID playerId) {
        return backStorage.getHistorySize(playerId);
    }

    /**
     * Get all stored locations for a player (for debugging).
     */
    public List<Location> getHistory(UUID playerId) {
        return backStorage.getHistory(playerId);
    }

    /**
     * Clear all location history for a player.
     */
    public void clearHistory(UUID playerId) {
        backStorage.clearHistory(playerId);
    }

    /**
     * Save all data (called on shutdown).
     */
    public void save() {
        backStorage.save();
    }
}
