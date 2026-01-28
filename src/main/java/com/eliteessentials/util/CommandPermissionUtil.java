package com.eliteessentials.util;

import com.eliteessentials.EliteEssentials;
import com.eliteessentials.config.ConfigManager;
import com.eliteessentials.permissions.PermissionService;
import com.eliteessentials.services.CostService;
import com.hypixel.hytale.server.core.command.system.CommandContext;
import com.hypixel.hytale.server.core.command.system.CommandSender;
import com.hypixel.hytale.server.core.universe.PlayerRef;

import java.util.UUID;

/**
 * Utility class for handling command permission checks.
 * 
 * Supports two modes based on config.advancedPermissions:
 * - Simple mode (false): Commands are either Everyone or Admin only
 * - Advanced mode (true): Full granular permission nodes
 */
public class CommandPermissionUtil {

    private CommandPermissionUtil() {} // Utility class

    /**
     * Check if an "Everyone" command can be executed.
     * In simple mode: always allowed (if enabled)
     * In advanced mode: checks the specific permission
     */
    public static boolean canExecute(CommandContext ctx, PlayerRef player, String permission, boolean enabled) {
        PermissionService perms = PermissionService.get();
        ConfigManager configManager = EliteEssentials.getInstance().getConfigManager();
        
        if (!perms.canUseEveryoneCommand(player.getUuid(), permission, enabled)) {
            ctx.sendMessage(MessageFormatter.formatWithFallback(configManager.getMessage("noPermission"), "#FF5555"));
            return false;
        }
        
        return true;
    }
    
    /**
     * Check if an "Admin" command can be executed.
     * In both modes: only admins can use these commands.
     */
    public static boolean canExecuteAdmin(CommandContext ctx, PlayerRef player, String permission, boolean enabled) {
        PermissionService perms = PermissionService.get();
        ConfigManager configManager = EliteEssentials.getInstance().getConfigManager();
        
        if (!perms.canUseAdminCommand(player.getUuid(), permission, enabled)) {
            ctx.sendMessage(MessageFormatter.formatWithFallback(configManager.getMessage("noPermission"), "#FF5555"));
            return false;
        }
        
        return true;
    }
    
    /**
     * Check if an "Admin" command can be executed (CommandSender variant).
     */
    public static boolean canExecuteAdmin(CommandContext ctx, String permission, boolean enabled) {
        PermissionService perms = PermissionService.get();
        ConfigManager configManager = EliteEssentials.getInstance().getConfigManager();
        CommandSender sender = ctx.sender();
        
        if (!perms.canUseAdminCommand(sender, permission, enabled)) {
            ctx.sendMessage(MessageFormatter.formatWithFallback(configManager.getMessage("noPermission"), "#FF5555"));
            return false;
        }
        
        return true;
    }

    /**
     * Legacy method - check if command is enabled (no permission check).
     */
    public static boolean canExecute(CommandContext ctx, PlayerRef player, boolean enabled) {
        ConfigManager configManager = EliteEssentials.getInstance().getConfigManager();
        PermissionService perms = PermissionService.get();
        
        if (enabled) {
            return true;
        }
        
        // Command is disabled - check if sender has admin permission
        if (perms.isAdmin(player.getUuid())) {
            return true;
        }
        
        ctx.sendMessage(MessageFormatter.formatWithFallback(configManager.getMessage("commandDisabled"), "#FF5555"));
        return false;
    }

    /**
     * Check if a player can bypass cooldown for a command.
     */
    public static boolean canBypassCooldown(UUID playerId, String commandName) {
        return PermissionService.get().canBypassCooldown(playerId, commandName);
    }

    /**
     * Check if a player can bypass warmup for a command.
     */
    public static boolean canBypassWarmup(UUID playerId, String commandName) {
        return PermissionService.get().canBypassWarmup(playerId, commandName);
    }

    /**
     * Get the effective cooldown for a player (0 if they can bypass).
     */
    public static int getEffectiveCooldown(UUID playerId, String commandName, int configCooldown) {
        if (canBypassCooldown(playerId, commandName)) {
            return 0;
        }
        return configCooldown;
    }

    /**
     * Get the effective warmup for a player (0 if they can bypass).
     */
    public static int getEffectiveWarmup(UUID playerId, String commandName, int configWarmup) {
        if (canBypassWarmup(playerId, commandName)) {
            return 0;
        }
        return configWarmup;
    }

    /**
     * Send a no-permission message to the player.
     */
    public static void sendNoPermission(CommandContext ctx) {
        ConfigManager configManager = EliteEssentials.getInstance().getConfigManager();
        ctx.sendMessage(MessageFormatter.formatWithFallback(configManager.getMessage("noPermission"), "#FF5555"));
    }

    /**
     * Send a command-disabled message to the player.
     */
    public static void sendCommandDisabled(CommandContext ctx) {
        ConfigManager configManager = EliteEssentials.getInstance().getConfigManager();
        ctx.sendMessage(MessageFormatter.formatWithFallback(configManager.getMessage("commandDisabled"), "#FF5555"));
    }

    /**
     * Check if a command can be executed with cost.
     * Only CHECKS if player can afford - does NOT charge.
     * Use this for commands with warmups, then call chargeCost() after success.
     * 
     * @param ctx Command context
     * @param player Player executing the command
     * @param permission Permission to check
     * @param enabled Whether the command is enabled
     * @param commandName Command name for cost bypass check
     * @param cost Cost to charge (0 = free)
     * @return true if command can proceed (has permission and can afford cost)
     */
    public static boolean canExecuteWithCost(CommandContext ctx, PlayerRef player, 
            String permission, boolean enabled, String commandName, double cost) {
        // First check permission
        if (!canExecute(ctx, player, permission, enabled)) {
            return false;
        }
        
        // Then check if player can afford (but don't charge yet)
        CostService costService = EliteEssentials.getInstance().getCostService();
        if (costService != null) {
            return costService.checkCanAfford(ctx, player, commandName, cost);
        }
        
        return true;
    }

    /**
     * Charge the player for a command cost.
     * Call this AFTER the command succeeds (e.g., after warmup completes).
     * 
     * @param ctx Command context
     * @param player Player to charge
     * @param commandName Command name for cost calculation
     * @param cost Cost to charge (0 = free)
     * @return true if charged successfully or no cost needed
     */
    public static boolean chargeCost(CommandContext ctx, PlayerRef player, String commandName, double cost) {
        CostService costService = EliteEssentials.getInstance().getCostService();
        if (costService != null) {
            return costService.charge(ctx, player, commandName, cost);
        }
        return true;
    }

    /**
     * Check if a player can bypass cost for a command.
     */
    public static boolean canBypassCost(UUID playerId, String commandName) {
        CostService costService = EliteEssentials.getInstance().getCostService();
        if (costService != null) {
            return costService.canBypassCost(playerId, commandName);
        }
        return true;
    }

    /**
     * Get the effective cost for a player (0 if they can bypass).
     */
    public static double getEffectiveCost(UUID playerId, String commandName, double configCost) {
        CostService costService = EliteEssentials.getInstance().getCostService();
        if (costService != null) {
            return costService.getEffectiveCost(playerId, commandName, configCost);
        }
        return 0.0;
    }
}
