package com.eliteessentials.services;

import com.eliteessentials.config.ConfigManager;
import com.eliteessentials.model.Location;
import com.eliteessentials.model.PlayerFile;
import com.eliteessentials.permissions.PermissionService;
import com.eliteessentials.permissions.Permissions;
import com.eliteessentials.storage.PlayerFileStorage;

import java.util.*;
import java.util.logging.Logger;

/**
 * Service for tracking player locations and providing /back functionality.
 * Maintains a history of locations for each player, persisted to player files.
 */
public class BackService {

    private static final Logger logger = Logger.getLogger("EliteEssentials");

    private final ConfigManager configManager;
    private final PlayerFileStorage storage;
    private int maxHistory = 5;

    public BackService(ConfigManager configManager, PlayerFileStorage storage) {
        this.configManager = configManager;
        this.storage = storage;
        this.maxHistory = configManager.getBackMaxHistory();
    }

    /**
     * Record a location for a player (called before teleports).
     * 
     * @param playerId Player UUID
     * @param location Location to record
     */
    public void pushLocation(UUID playerId, Location location) {
        if (location == null || playerId == null) return;
        
        PlayerFile playerFile = storage.getPlayer(playerId);
        if (playerFile == null) {
            if (configManager.isDebugEnabled()) {
                logger.info("[BackService] Could not find player file for " + playerId + ", cannot save back location");
            }
            return;
        }
        
        playerFile.pushBackLocation(location, maxHistory);
        storage.saveAndMarkDirty(playerId);
        
        if (configManager.isDebugEnabled()) {
            logger.info("[BackService] Saved back location for " + playerId + ": " + 
                String.format("%.1f, %.1f, %.1f (history size: %d)", 
                    location.getX(), location.getY(), location.getZ(), playerFile.getBackHistorySize()));
        }
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
        PlayerFile playerFile = storage.getPlayer(playerId);
        if (playerFile == null) return Optional.empty();
        return playerFile.peekBackLocation();
    }

    /**
     * Pop and return the most recent location for a player.
     * 
     * @param playerId Player UUID
     * @return The previous location, or empty if none
     */
    public Optional<Location> popLocation(UUID playerId) {
        PlayerFile playerFile = storage.getPlayer(playerId);
        if (playerFile == null) return Optional.empty();
        
        Optional<Location> location = playerFile.popBackLocation();
        if (location.isPresent()) {
            storage.saveAndMarkDirty(playerId);
            logger.fine("Popped location for " + playerId + ": " + location.get());
        }
        return location;
    }

    /**
     * Get the number of stored locations for a player.
     */
    public int getHistorySize(UUID playerId) {
        PlayerFile playerFile = storage.getPlayer(playerId);
        if (playerFile == null) return 0;
        return playerFile.getBackHistorySize();
    }

    /**
     * Get all stored locations for a player (for debugging).
     */
    public List<Location> getHistory(UUID playerId) {
        PlayerFile playerFile = storage.getPlayer(playerId);
        if (playerFile == null) return Collections.emptyList();
        return playerFile.getBackHistory();
    }

    /**
     * Clear all location history for a player.
     */
    public void clearHistory(UUID playerId) {
        PlayerFile playerFile = storage.getPlayer(playerId);
        if (playerFile == null) return;
        playerFile.clearBackHistory();
        storage.saveAndMarkDirty(playerId);
    }

    /**
     * Save all data (called on shutdown).
     */
    public void save() {
        storage.saveAll();
    }
}
