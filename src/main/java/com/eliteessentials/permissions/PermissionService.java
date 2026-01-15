package com.eliteessentials.permissions;

import com.eliteessentials.EliteEssentials;
import com.eliteessentials.config.ConfigManager;
import com.hypixel.hytale.server.core.command.system.CommandSender;
import com.hypixel.hytale.server.core.permissions.PermissionsModule;
import com.hypixel.hytale.server.core.universe.PlayerRef;

import java.util.Set;
import java.util.UUID;
import java.util.logging.Logger;

/**
 * Service for checking permissions using Hytale's permission system.
 * 
 * Supports two modes:
 * - Simple mode (advancedPermissions=false): Commands are either Everyone or Admin only
 * - Advanced mode (advancedPermissions=true): Full granular permission nodes
 */
public class PermissionService {

    private static final Logger logger = Logger.getLogger("EliteEssentials");
    private static PermissionService instance;

    private PermissionService() {}

    public static PermissionService get() {
        if (instance == null) {
            instance = new PermissionService();
        }
        return instance;
    }
    
    // ==================== MODE CHECK ====================
    
    /**
     * Check if advanced permissions mode is enabled.
     */
    private boolean isAdvancedMode() {
        try {
            return EliteEssentials.getInstance().getConfigManager().isAdvancedPermissions();
        } catch (Exception e) {
            return false;
        }
    }

    // ==================== BASIC PERMISSION CHECKS ====================

    /**
     * Check if a player has a permission (defaults to false if not set).
     */
    public boolean hasPermission(UUID playerId, String permission) {
        return hasPermission(playerId, permission, false);
    }

    /**
     * Check if a player has a permission with a default value.
     */
    public boolean hasPermission(UUID playerId, String permission, boolean defaultValue) {
        try {
            PermissionsModule perms = PermissionsModule.get();
            return perms.hasPermission(playerId, permission, defaultValue);
        } catch (Exception e) {
            logger.warning("[Permissions] Error checking permission " + permission + ": " + e.getMessage());
            return defaultValue;
        }
    }

    /**
     * Check if a CommandSender has a permission.
     */
    public boolean hasPermission(CommandSender sender, String permission) {
        if (sender == null) return false;
        return sender.hasPermission(permission, false);
    }

    /**
     * Check if a PlayerRef has a permission.
     */
    public boolean hasPermission(PlayerRef player, String permission) {
        if (player == null) return false;
        return hasPermission(player.getUuid(), permission);
    }
    
    /**
     * Check if a player is an admin (has OP or admin permission).
     */
    public boolean isAdmin(UUID playerId) {
        return hasPermission(playerId, Permissions.ADMIN) || 
               hasPermission(playerId, "hytale.command.op.*");
    }
    
    /**
     * Check if a CommandSender is an admin.
     */
    public boolean isAdmin(CommandSender sender) {
        if (sender == null) return false;
        return sender.hasPermission(Permissions.ADMIN, false) ||
               sender.hasPermission("hytale.command.op.*", false);
    }

    // ==================== SIMPLE MODE COMMAND CHECKS ====================
    
    /**
     * Simple mode permission check for "Everyone" commands.
     * In simple mode: always allowed (if command is enabled)
     * In advanced mode: checks the specific permission
     */
    public boolean canUseEveryoneCommand(UUID playerId, String advancedPermission, boolean commandEnabled) {
        // Command must be enabled (admins bypass this)
        if (!commandEnabled && !isAdmin(playerId)) {
            return false;
        }
        
        // In simple mode, everyone can use "Everyone" commands
        if (!isAdvancedMode()) {
            return true;
        }
        
        // In advanced mode, check the specific permission
        return hasPermission(playerId, advancedPermission) || isAdmin(playerId);
    }
    
    /**
     * Simple mode permission check for "Everyone" commands (CommandSender variant).
     */
    public boolean canUseEveryoneCommand(CommandSender sender, String advancedPermission, boolean commandEnabled) {
        if (sender == null) return false;
        
        // Command must be enabled (admins bypass this)
        if (!commandEnabled && !isAdmin(sender)) {
            return false;
        }
        
        // In simple mode, everyone can use "Everyone" commands
        if (!isAdvancedMode()) {
            return true;
        }
        
        // In advanced mode, check the specific permission
        return hasPermission(sender, advancedPermission) || isAdmin(sender);
    }
    
    /**
     * Simple mode permission check for "Admin" commands.
     * In both modes: only admins can use these commands.
     */
    public boolean canUseAdminCommand(UUID playerId, String advancedPermission, boolean commandEnabled) {
        // Command must be enabled (admins bypass this)
        if (!commandEnabled && !isAdmin(playerId)) {
            return false;
        }
        
        // In simple mode, only admins
        if (!isAdvancedMode()) {
            return isAdmin(playerId);
        }
        
        // In advanced mode, check the specific permission (or admin)
        return hasPermission(playerId, advancedPermission) || isAdmin(playerId);
    }
    
    /**
     * Simple mode permission check for "Admin" commands (CommandSender variant).
     */
    public boolean canUseAdminCommand(CommandSender sender, String advancedPermission, boolean commandEnabled) {
        if (sender == null) return false;
        
        // Command must be enabled (admins bypass this)
        if (!commandEnabled && !isAdmin(sender)) {
            return false;
        }
        
        // In simple mode, only admins
        if (!isAdvancedMode()) {
            return isAdmin(sender);
        }
        
        // In advanced mode, check the specific permission (or admin)
        return hasPermission(sender, advancedPermission) || isAdmin(sender);
    }

    // ==================== BYPASS CHECKS ====================

    /**
     * Check if a player can bypass cooldown for a command.
     */
    public boolean canBypassCooldown(UUID playerId, String commandName) {
        // Check global bypass first
        if (hasPermission(playerId, Permissions.BYPASS_COOLDOWN)) {
            return true;
        }
        // Check command-specific bypass
        return hasPermission(playerId, Permissions.bypassCooldown(commandName));
    }

    /**
     * Check if a player can bypass warmup for a command.
     */
    public boolean canBypassWarmup(UUID playerId, String commandName) {
        // Check global bypass first
        if (hasPermission(playerId, Permissions.BYPASS_WARMUP)) {
            return true;
        }
        // Check command-specific bypass
        return hasPermission(playerId, Permissions.bypassWarmup(commandName));
    }

    // ==================== LIMIT CHECKS ====================

    /**
     * Get the maximum number of homes a player can have based on permissions.
     * Checks permissions like eliteessentials.limit.homes.5, eliteessentials.limit.homes.10, etc.
     * Returns the highest limit found, or the config default if no permission is set.
     * 
     * Note: We check common limit values since Hytale's PermissionsModule doesn't expose
     * a way to enumerate all permissions a user has. Server admins should use standard
     * limit values (1, 2, 3, 5, 10, 15, 20, 25, 50, 100) for best compatibility.
     */
    public int getMaxHomes(UUID playerId) {
        ConfigManager configManager = EliteEssentials.getInstance().getConfigManager();
        int defaultMax = configManager.getMaxHomes();
        
        // Check for unlimited permission first
        if (hasPermission(playerId, Permissions.LIMIT_HOMES_UNLIMITED)) {
            return Integer.MAX_VALUE;
        }
        
        // Check for specific limit permissions (common values)
        // We check from highest to lowest and return the first match for efficiency
        int[] commonLimits = {100, 50, 25, 20, 15, 10, 5, 3, 2, 1};
        
        for (int limit : commonLimits) {
            if (hasPermission(playerId, Permissions.homeLimit(limit))) {
                return limit;
            }
        }
        
        // No specific limit permission found, return config default
        return defaultMax;
    }

    // ==================== WARP ACCESS ====================

    /**
     * Check if a player can access a specific warp.
     * @param playerId Player UUID
     * @param warpName Warp name
     * @param warpPermission The warp's required permission enum (ALL or OP)
     */
    public boolean canAccessWarp(UUID playerId, String warpName, com.eliteessentials.model.Warp.Permission warpPermission) {
        // ALL means everyone with base warp permission can access
        if (warpPermission == com.eliteessentials.model.Warp.Permission.ALL) {
            return true;
        }
        
        // OP means only OPs/admins
        if (warpPermission == com.eliteessentials.model.Warp.Permission.OP) {
            return hasPermission(playerId, Permissions.ADMIN) || 
                   hasPermission(playerId, Permissions.WARPADMIN);
        }
        
        // Fallback - check the specific warp permission
        return hasPermission(playerId, Permissions.warpAccess(warpName)) ||
               hasPermission(playerId, Permissions.ADMIN);
    }

    // ==================== DEFAULT PERMISSION SETUP ====================

    /**
     * Get the default permissions for the "Default" group.
     * These are the basic commands available to all players.
     */
    public static Set<String> getDefaultGroupPermissions() {
        return Set.of(
            // Home commands
            Permissions.HOME,
            Permissions.SETHOME,
            Permissions.DELHOME,
            Permissions.HOMES,
            // Teleport commands
            Permissions.BACK,
            Permissions.RTP,
            Permissions.TPA,
            Permissions.TPACCEPT,
            Permissions.TPDENY,
            // Spawn
            Permissions.SPAWN,
            // Warps (use only)
            Permissions.WARP,
            Permissions.WARPS
        );
    }

    /**
     * Get the default permissions for the "OP" group.
     * OPs have all permissions via wildcard.
     */
    public static Set<String> getOpGroupPermissions() {
        return Set.of("*");
    }
}
