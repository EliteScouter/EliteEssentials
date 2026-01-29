package com.eliteessentials.permissions;

import com.eliteessentials.EliteEssentials;
import com.eliteessentials.config.ConfigManager;
import com.eliteessentials.integration.LuckPermsIntegration;
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
     * 
     * Priority:
     * 1. Unlimited permission (returns Integer.MAX_VALUE)
     * 2. LuckPerms permission-based limit (any value via eliteessentials.command.home.limit.<number>)
     * 3. Config default
     * 
     * Note: Custom limit values require LuckPerms. Without LuckPerms, only config default is used.
     */
    public int getMaxHomes(UUID playerId) {
        ConfigManager configManager = EliteEssentials.getInstance().getConfigManager();
        int defaultMax = configManager.getMaxHomes();
        
        // Check for unlimited permission first
        if (hasPermission(playerId, Permissions.LIMIT_HOMES_UNLIMITED)) {
            return Integer.MAX_VALUE;
        }
        
        // Try to get custom limit from LuckPerms (returns highest value found)
        if (LuckPermsIntegration.isAvailable()) {
            // Check full permission format: eliteessentials.command.home.limit.<number>
            int lpLimit = getHighestPermissionValue(playerId, Permissions.HOME_LIMIT_PREFIX);
            if (lpLimit > 0) {
                return lpLimit;
            }
            
            // Check short permission format: homes.limit.<number>
            int shortLimit = getHighestPermissionValue(playerId, "homes.limit.");
            if (shortLimit > 0) {
                return shortLimit;
            }
        }
        
        // No specific limit permission found, return config default
        return defaultMax;
    }
    
    /**
     * Get the highest numeric value from a permission node pattern.
     * Used for limits where higher is better (e.g., home limits).
     * 
     * @param playerId Player UUID
     * @param permissionPrefix Permission prefix to search for
     * @return The highest value found, or -1 if not found
     */
    private int getHighestPermissionValue(UUID playerId, String permissionPrefix) {
        ConfigManager configManager = EliteEssentials.getInstance().getConfigManager();
        boolean debug = configManager != null && configManager.isDebugEnabled();
        
        if (!LuckPermsIntegration.isAvailable()) {
            if (debug) {
                logger.info("[HomeLimit] LuckPerms not available");
            }
            return -1;
        }
        
        // Use reflection to call a modified version that returns highest instead of lowest
        try {
            Object[] lpObjects = getLuckPermsObjectsViaReflection(playerId);
            if (lpObjects == null) {
                if (debug) {
                    logger.info("[HomeLimit] Could not get LuckPerms objects for player");
                }
                return -1;
            }
            
            Object user = lpObjects[2];
            
            // Get CachedData -> PermissionData -> PermissionMap
            java.lang.reflect.Method getCachedDataMethod = user.getClass().getMethod("getCachedData");
            Object cachedData = getCachedDataMethod.invoke(user);
            
            java.lang.reflect.Method getPermissionDataMethod = cachedData.getClass().getMethod("getPermissionData");
            Object permissionData = getPermissionDataMethod.invoke(cachedData);
            
            java.lang.reflect.Method getPermissionMapMethod = permissionData.getClass().getMethod("getPermissionMap");
            @SuppressWarnings("unchecked")
            java.util.Map<String, Boolean> permMap = (java.util.Map<String, Boolean>) getPermissionMapMethod.invoke(permissionData);
            
            if (debug) {
                logger.info("[HomeLimit] Searching for prefix: " + permissionPrefix);
                logger.info("[HomeLimit] Permission map has " + permMap.size() + " entries");
                // Log all permissions that contain "home" or "limit"
                for (java.util.Map.Entry<String, Boolean> entry : permMap.entrySet()) {
                    if (entry.getKey().contains("home") || entry.getKey().contains("limit")) {
                        logger.info("[HomeLimit] Found perm: " + entry.getKey() + " = " + entry.getValue());
                    }
                }
            }
            
            int highestValue = -1;
            
            for (java.util.Map.Entry<String, Boolean> entry : permMap.entrySet()) {
                if (entry.getValue() && entry.getKey().startsWith(permissionPrefix)) {
                    String valuePart = entry.getKey().substring(permissionPrefix.length());
                    try {
                        int value = Integer.parseInt(valuePart);
                        if (debug) {
                            logger.info("[HomeLimit] Matched: " + entry.getKey() + " -> value " + value);
                        }
                        if (value > highestValue) {
                            highestValue = value;
                        }
                    } catch (NumberFormatException ignored) {}
                }
            }
            
            if (debug) {
                logger.info("[HomeLimit] Final result for prefix '" + permissionPrefix + "': " + highestValue);
            }
            
            return highestValue;
            
        } catch (Exception e) {
            if (debug) {
                logger.info("[HomeLimit] Exception: " + e.getMessage());
            }
            return -1;
        }
    }
    
    /**
     * Helper to get LuckPerms objects via reflection (mirrors LuckPermsIntegration.getLuckPermsObjects)
     */
    private Object[] getLuckPermsObjectsViaReflection(UUID playerId) {
        try {
            Class<?> providerClass = Class.forName("net.luckperms.api.LuckPermsProvider");
            java.lang.reflect.Method getMethod = providerClass.getMethod("get");
            Object luckPerms = getMethod.invoke(null);
            
            if (luckPerms == null) return null;
            
            java.lang.reflect.Method getUserManagerMethod = luckPerms.getClass().getMethod("getUserManager");
            Object userManager = getUserManagerMethod.invoke(luckPerms);
            
            java.lang.reflect.Method getUserMethod = userManager.getClass().getMethod("getUser", UUID.class);
            Object user = getUserMethod.invoke(userManager, playerId);
            
            if (user == null) {
                java.lang.reflect.Method loadUserMethod = userManager.getClass().getMethod("loadUser", UUID.class);
                Object completableFuture = loadUserMethod.invoke(userManager, playerId);
                java.lang.reflect.Method joinMethod = completableFuture.getClass().getMethod("join");
                user = joinMethod.invoke(completableFuture);
            }
            
            if (user == null) return null;
            
            return new Object[] { luckPerms, userManager, user };
        } catch (Exception e) {
            return null;
        }
    }

    // ==================== HEAL COOLDOWN ====================

    /**
     * Get the heal cooldown for a player based on permissions.
     * 
     * Priority:
     * 1. Bypass permission (returns 0)
     * 2. LuckPerms permission-based cooldown (any value via eliteessentials.command.misc.heal.cooldown.<seconds>)
     * 3. Config default
     * 
     * Note: Custom cooldown values require LuckPerms. Without LuckPerms, only config default is used.
     */
    public int getHealCooldown(UUID playerId) {
        ConfigManager configManager = EliteEssentials.getInstance().getConfigManager();
        int defaultCooldown = configManager.getConfig().heal.cooldownSeconds;
        
        // Check for bypass permission first
        if (hasPermission(playerId, Permissions.HEAL_BYPASS_COOLDOWN)) {
            return 0;
        }
        
        // Try to get custom cooldown from LuckPerms
        if (LuckPermsIntegration.isAvailable()) {
            int lpCooldown = LuckPermsIntegration.getInheritedPermissionValue(playerId, Permissions.HEAL_COOLDOWN_PREFIX);
            if (lpCooldown >= 0) {
                return lpCooldown;
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
     * 2. LuckPerms permission-based cooldown (any value)
     * 3. Config default
     * 
     * Note: Custom cooldown values require LuckPerms. Without LuckPerms, only config default is used.
     * 
     * @param playerId Player UUID
     * @param commandName Command name (god, fly, repair, clearinv, top)
     * @param defaultCooldown Default cooldown from config
     * @return Effective cooldown in seconds
     */
    public int getCommandCooldown(UUID playerId, String commandName, int defaultCooldown) {
        // Get bypass and cooldown prefix based on command
        String bypassPermission;
        String cooldownPrefix;
        
        switch (commandName.toLowerCase()) {
            case "god":
                bypassPermission = Permissions.GOD_BYPASS_COOLDOWN;
                cooldownPrefix = Permissions.GOD_COOLDOWN_PREFIX;
                break;
            case "fly":
                bypassPermission = Permissions.FLY_BYPASS_COOLDOWN;
                cooldownPrefix = Permissions.FLY_COOLDOWN_PREFIX;
                break;
            case "repair":
                bypassPermission = Permissions.REPAIR_BYPASS_COOLDOWN;
                cooldownPrefix = Permissions.REPAIR_COOLDOWN_PREFIX;
                break;
            case "clearinv":
                bypassPermission = Permissions.CLEARINV_BYPASS_COOLDOWN;
                cooldownPrefix = Permissions.CLEARINV_COOLDOWN_PREFIX;
                break;
            case "top":
                bypassPermission = Permissions.TOP_BYPASS_COOLDOWN;
                cooldownPrefix = Permissions.TOP_COOLDOWN_PREFIX;
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
        
        // Try to get custom cooldown from LuckPerms
        if (LuckPermsIntegration.isAvailable()) {
            int lpCooldown = LuckPermsIntegration.getInheritedPermissionValue(playerId, cooldownPrefix);
            if (lpCooldown >= 0) {
                return lpCooldown;
            }
        }
        
        // No specific cooldown permission found, return config default
        return defaultCooldown;
    }

    // ==================== TP COMMAND COOLDOWN ====================

    /**
     * Get the cooldown for a teleport command based on permissions.
     * Supports: rtp, tpa, tpahere, back
     * 
     * Priority:
     * 1. Bypass permission (returns 0)
     * 2. LuckPerms permission-based cooldown (any value via eliteessentials.command.tp.cooldown.<cmd>.<seconds>)
     * 3. Config default
     * 
     * Note: Custom cooldown values require LuckPerms. Without LuckPerms, only config default is used.
     * 
     * @param playerId Player UUID
     * @param commandName Command name (rtp, tpa, tpahere, back)
     * @param defaultCooldown Default cooldown from config
     * @return Effective cooldown in seconds
     */
    public int getTpCommandCooldown(UUID playerId, String commandName, int defaultCooldown) {
        // Check for bypass permission first
        if (hasPermission(playerId, Permissions.tpBypassCooldown(commandName))) {
            return 0;
        }
        
        // Also check global TP bypass
        if (hasPermission(playerId, Permissions.TP_BYPASS_COOLDOWN)) {
            return 0;
        }
        
        // Try to get custom cooldown from LuckPerms
        if (LuckPermsIntegration.isAvailable()) {
            String cooldownPrefix = Permissions.TP_COOLDOWN_PREFIX + commandName + ".";
            int lpCooldown = LuckPermsIntegration.getInheritedPermissionValue(playerId, cooldownPrefix);
            if (lpCooldown >= 0) {
                return lpCooldown;
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
