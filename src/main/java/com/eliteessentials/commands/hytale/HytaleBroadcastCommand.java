package com.eliteessentials.commands.hytale;

import com.eliteessentials.config.ConfigManager;
import com.eliteessentials.permissions.Permissions;
import com.eliteessentials.permissions.PermissionService;
import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.command.system.CommandContext;
import com.hypixel.hytale.server.core.command.system.arguments.system.RequiredArg;
import com.hypixel.hytale.server.core.command.system.basecommands.CommandBase;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.Universe;

/**
 * /broadcast (alias: /bc) - Broadcast a message to all online players.
 * 
 * Usage: /broadcast <message>
 * Aliases: /bc
 * Permission: eliteessentials.command.misc.broadcast (Admin only)
 */
public class HytaleBroadcastCommand extends CommandBase {
    
    private final ConfigManager configManager;
    private final RequiredArg<String> messageArg;
    
    public HytaleBroadcastCommand(ConfigManager configManager) {
        super("broadcast", "Broadcast a message to all online players");
        this.configManager = configManager;
        
        // Add alias
        addAliases("bc");
        
        // Add argument: message (greedy string - captures all remaining text)
        this.messageArg = withRequiredArg("message", "Message to broadcast", 
            com.eliteessentials.commands.args.SimpleStringArg.GREEDY);
    }
    
    @Override
    protected boolean canGeneratePermission() {
        return false;
    }
    
    @Override
    protected void executeSync(CommandContext ctx) {
        // Permission check - admin only
        PermissionService perms = PermissionService.get();
        if (!perms.canUseAdminCommand(ctx.sender(), Permissions.BROADCAST, 
                configManager.getConfig().broadcast.enabled)) {
            ctx.sendMessage(Message.raw(configManager.getMessage("noPermission")).color("#FF5555"));
            return;
        }
        
        // Get message argument
        String message = ctx.get(messageArg);
        if (message == null || message.trim().isEmpty()) {
            ctx.sendMessage(Message.raw("Usage: /broadcast <message>").color("#FF5555"));
            return;
        }
        
        // Format broadcast message
        String broadcastText = configManager.getMessage("broadcast", "message", message);
        
        // Broadcast to all online players
        broadcastMessage(broadcastText);
        
        if (configManager.isDebugEnabled()) {
            ctx.sendMessage(Message.raw("Broadcast sent: " + broadcastText).color("#55FF55"));
        }
    }
    
    /**
     * Broadcast message to all online players.
     */
    private void broadcastMessage(String text) {
        Message message = Message.raw(text).color("#FFFF55");
        
        try {
            // Get all online players and broadcast
            Universe universe = Universe.get();
            if (universe != null) {
                var players = universe.getPlayers();
                for (PlayerRef player : players) {
                    player.sendMessage(message);
                }
            }
        } catch (Exception e) {
            // Log error if broadcast fails
            System.err.println("Could not broadcast message: " + e.getMessage());
        }
    }
}
