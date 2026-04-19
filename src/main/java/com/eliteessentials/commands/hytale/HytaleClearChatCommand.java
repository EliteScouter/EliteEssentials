package com.eliteessentials.commands.hytale;

import com.eliteessentials.config.ConfigManager;
import com.eliteessentials.permissions.Permissions;
import com.eliteessentials.permissions.PermissionService;
import com.eliteessentials.util.MessageFormatter;
import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.command.system.CommandContext;
import com.hypixel.hytale.server.core.command.system.basecommands.CommandBase;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.Universe;

import javax.annotation.Nonnull;
import com.eliteessentials.util.CommandSpyUtil;

/**
 * /clearchat (alias: /cc) - Clear chat for all online players.
 * 
 * Sends blank lines to all players to clear their chat window.
 * 
 * Usage: /clearchat
 * Aliases: /cc
 * Permission: eliteessentials.command.misc.clearchat (Admin only)
 */
public class HytaleClearChatCommand extends CommandBase {
    
    private final ConfigManager configManager;
    
    /** Number of blank lines to send to clear chat */
    private static final int CLEAR_LINES = 100;
    
    public HytaleClearChatCommand(ConfigManager configManager) {
        super("clearchat", "Clear chat for all online players");
        this.configManager = configManager;
        
        // Add alias
        addAliases("cc");
    }
    
    @Override
    protected boolean canGeneratePermission() {
        return false;
    }
    
    @Override
    protected void executeSync(@Nonnull CommandContext ctx) {
        CommandSpyUtil.notify(ctx);
        // Permission check - admin only
        PermissionService perms = PermissionService.get();
        if (!perms.canUseAdminCommand(ctx.sender(), Permissions.CLEARCHAT, 
                configManager.getConfig().clearChat.enabled)) {
            ctx.sendMessage(MessageFormatter.formatWithFallback(configManager.getMessage("noPermission"), "#FF5555"));
            return;
        }
        
        // Clear chat for all online players
        clearChatForAllPlayers();
        
        // Send confirmation message to all players
        String clearMessage = configManager.getMessage("chatCleared");
        Message message = MessageFormatter.format(clearMessage);
        
        try {
            Universe universe = Universe.get();
            if (universe != null) {
                var players = universe.getPlayers();
                for (PlayerRef player : players) {
                    player.sendMessage(message);
                }
            }
        } catch (Exception e) {
            java.util.logging.Logger.getLogger("EliteEssentials").warning("Could not send chat cleared message: " + e.getMessage());
        }
        
        if (configManager.isDebugEnabled()) {
            ctx.sendMessage(Message.raw("Chat cleared for all players").color("#55FF55"));
        }
    }
    
    /**
     * Clear chat for all online players by sending blank lines.
     */
    private void clearChatForAllPlayers() {
        try {
            Universe universe = Universe.get();
            if (universe != null) {
                var players = universe.getPlayers();
                for (PlayerRef player : players) {
                    // Send blank lines to clear chat
                    for (int i = 0; i < CLEAR_LINES; i++) {
                        player.sendMessage(Message.raw(" "));
                    }
                }
            }
        } catch (Exception e) {
            java.util.logging.Logger.getLogger("EliteEssentials").warning("Could not clear chat: " + e.getMessage());
        }
    }
}
