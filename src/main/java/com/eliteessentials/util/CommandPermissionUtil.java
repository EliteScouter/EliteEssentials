package com.eliteessentials.util;

import com.eliteessentials.EliteEssentials;
import com.eliteessentials.config.ConfigManager;
import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.command.system.CommandContext;
import com.hypixel.hytale.server.core.command.system.CommandSender;
import com.hypixel.hytale.server.core.universe.PlayerRef;

/**
 * Utility class for handling command permission checks.
 * When a command is disabled via config, only OPs can use it.
 */
public class CommandPermissionUtil {

    private static final String ADMIN_PERMISSION = "eliteessentials.admin";

    /**
     * Check if a command can be executed based on enabled state.
     * If the command is disabled, only players with admin permission can use it.
     * 
     * @param ctx The command context
     * @param player The player executing the command
     * @param enabled Whether the command is enabled in config
     * @return true if the command can proceed, false if blocked
     */
    public static boolean canExecute(CommandContext ctx, PlayerRef player, boolean enabled) {
        if (enabled) {
            return true;
        }
        
        // Command is disabled - check if sender has admin permission
        CommandSender sender = ctx.sender();
        if (sender != null && sender.hasPermission(ADMIN_PERMISSION)) {
            return true;
        }
        
        // Player doesn't have permission
        ConfigManager configManager = EliteEssentials.getInstance().getConfigManager();
        ctx.sendMessage(Message.raw(configManager.getMessage("commandDisabled")).color("#FF5555"));
        return false;
    }
}
