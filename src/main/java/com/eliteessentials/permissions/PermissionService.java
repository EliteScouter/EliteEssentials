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
               hasPermission(playerId, Permissions.ADMIN_BASE) ||  // eliteessentials.admin (without wildcard)
               hasPermission(playerId, "hytale.command.op.*");
    }
    
    /**
     * Check if a CommandSender is an admin.
     */
    public boolean isAdmin(CommandSender sender) {
        if (sender == null) return false;
        return sender.hasPermission(Permissions.ADMIN, false) ||
               sender.hasPermission(Permissions.ADMIN_BASE, false) ||  // eliteessentials.admin (without wildcard)
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

    // ==================== HEAL COOLDOWN ====================

    /**
     * Get the heal cooldown for a player based on permissions.
     * Checks for permission-based cooldowns like eliteessentials.command.misc.heal.cooldown.300
     * 
     * Priority:
     * 1. Bypass permission (returns 0)
     * 2. Permission-based cooldown (heal.cooldown.<seconds>)
     * 3. Config default
     * 
     * Note: We check common cooldown values. Server admins should use standard
     * values (30, 60, 120, 180, 300, 600, 900, 1800, 3600) for best compatibility.
     */
    public int getHealCooldown(UUID playerId) {
        ConfigManager configManager = EliteEssentials.getInstance().getConfigManager();
        int defaultCooldown = configManager.getConfig().heal.cooldownSeconds;
        
        // Check for bypass permission first
        if (hasPermission(playerId, Permissions.HEAL_BYPASS_COOLDOWN)) {
            return 0;
        }
        
        // Check for specific cooldown permissions (common values in seconds)
        // We check from lowest to highest and return the first match
        // This way groups with lower cooldowns get priority
        int[] commonCooldowns = {30, 60, 120, 180, 300, 600, 900, 1800, 3600};
        
        for (int cooldown : commonCooldowns) {
            if (hasPermission(playerId, Permissions.healCooldown(cooldown))) {
                return cooldown;
            }
        }
        
        // No specific cooldown permission found, return config default
        return defaultCooldown;
    }

    // ==================== GENERIC COMMAND COOLDOWN ====================

    /**
     * Get the cooldown for a command based on permissions.
     * Supports: god, fly, repair, clearinv, top
     * 
     * Priority:
     * 1. Bypass permission (returns 0)
     * 2. Permission-based cooldown (<command>.cooldown.<seconds>)
     * 3. Config default
     * 
     * @param playerId Player UUID
     * @param commandName Command name (god, fly, repair, clearinv, top)
     * @param defaultCooldown Default cooldown from config
     * @return Effective cooldown in seconds
     */
    public int getCommandCooldown(UUID playerId, String commandName, int defaultCooldown) {
        // Get bypass and cooldown prefix based on command
        String bypassPermission;
        java.util.function.IntFunction<String> cooldownPermissionFunc;
        
        switch (commandName.toLowerCase()) {
            case "god":
                bypassPermission = Permissions.GOD_BYPASS_COOLDOWN;
                cooldownPermissionFunc = Permissions::godCooldown;
                break;
            case "fly":
                bypassPermission = Permissions.FLY_BYPASS_COOLDOWN;
                cooldownPermissionFunc = Permissions::flyCooldown;
                break;
            case "repair":
                bypassPermission = Permissions.REPAIR_BYPASS_COOLDOWN;
                cooldownPermissionFunc = Permissions::repairCooldown;
                break;
            case "clearinv":
                bypassPermission = Permissions.CLEARINV_BYPASS_COOLDOWN;
                cooldownPermissionFunc = Permissions::clearinvCooldown;
                break;
            case "top":
                bypassPermission = Permissions.TOP_BYPASS_COOLDOWN;
                cooldownPermissionFunc = Permissions::topCooldown;
                break;
            case "heal":
                // Use existing heal method for consistency
                return getHealCooldown(playerId);
            default:
                // Unknown command, return default
                return defaultCooldown;
        }
        
        // Check for bypass permission first
        if (hasPermission(playerId, bypassPermission)) {
            return 0;
        }
        
        // Check for specific cooldown permissions (common values in seconds)
        int[] commonCooldowns = {30, 60, 120, 180, 300, 600, 900, 1800, 3600};
        
        for (int cooldown : commonCooldowns) {
            if (hasPermission(playerId, cooldownPermissionFunc.apply(cooldown))) {
                return cooldown;
            }
        }
        
        // No specific cooldown permission found, return config default
        return defaultCooldown;
    }

    // ==================== COMMAND COST ====================

    /**
     * Get the cost for a command based on permissions.
     * Checks permissions like eliteessentials.cost.home.5, eliteessentials.cost.rtp.100, etc.
     * Returns the lowest cost found (most favorable to player), or the config default if no permission is set.
     * 
     * Note: We check common cost values since Hytale's PermissionsModule doesn't expose
     * a way to enumerate all permissions a user has. Server admins should use standard
     * cost values (0, 1, 2, 5, 10, 15, 20, 25, 50, 100, 250, 500, 1000) for best compatibility.
     * 
     * @param playerId Player UUID
     * @param commandName Command name (e.g., "home", "rtp", "warp", "spawn", "back", "tpa", "tpahere", "sethome")
     * @param defaultCost Default cost from config (fallback if no permission set)
     * @return The effective cost for this player
     */
    public double getCommandCost(UUID playerId, String commandName, double defaultCost) {
        // Check for specific cost permissions (common values)
        // We check from lowest to highest and return the first match
        // This way groups with lower costs get priority (VIP pays less)
        int[] commonCosts = {0, 1, 2, 5, 10, 15, 20, 25, 50, 100, 250, 500, 1000};
        
        for (int cost : commonCosts) {
            if (hasPermission(playerId, Permissions.commandCost(commandName, cost))) {
                return cost;
            }
        }
        
        // No specific cost permission found, return config default
        return defaultCost;
    }

    // ==================== WARP ACCESS ====================

    /**
     * Check if a player can access a specific warp.
     * @param playerId Player UUID
     * @param warpName Warp name
     * @param warpPermission The warp's required permission enum (ALL or OP)
     * 
     * Permission logic:
     * - Simple mode: ALL warps = everyone, OP warps = admins only
     * - Advanced mode: Player needs warp.use (all public warps) OR warp.<name> (specific warp)
     *   - OP warps still require admin permission
     */
    public boolean canAccessWarp(UUID playerId, String warpName, com.eliteessentials.model.Warp.Permission warpPermission) {
        // Admins always have access
        if (isAdmin(playerId)) {
            return true;
        }
        
        // OP warps require admin permission
        if (warpPermission == com.eliteessentials.model.Warp.Permission.OP) {
            return hasPermission(playerId, Permissions.WARPADMIN);
        }
        
        // For ALL warps:
        // - Simple mode: everyone can access
        // - Advanced mode: need warp.use OR warp.<name>
        if (!isAdvancedMode()) {
            return true;  // Simple mode - everyone can use ALL warps
        }
        
        // Advanced mode - check warp.use (general access) OR warp.<name> (specific access)
        return hasPermission(playerId, Permissions.WARP) || 
               hasPermission(playerId, Permissions.warpAccess(warpName));
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
