package com.eliteessentials.util;

import com.eliteessentials.EliteEssentials;
import com.eliteessentials.config.ConfigManager;
import com.eliteessentials.permissions.PermissionService;
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
}
