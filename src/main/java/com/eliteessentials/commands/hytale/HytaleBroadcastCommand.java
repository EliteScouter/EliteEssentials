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

/**
 * /broadcast (alias: /bc) - Broadcast a message to all online players.
 * 
 * Supports color codes (&0-f, &l, &o, &r) in the message.
 * 
 * Usage: /broadcast <message>
 * Aliases: /bc
 * Permission: eliteessentials.command.misc.broadcast (Admin only)
 */
public class HytaleBroadcastCommand extends CommandBase {
    
    private final ConfigManager configManager;
    
    public HytaleBroadcastCommand(ConfigManager configManager) {
        super("broadcast", "Broadcast a message to all online players");
        this.configManager = configManager;
        
        // Add alias
        addAliases("bc");
        
        // Allow extra arguments to capture full message with spaces
        setAllowsExtraArguments(true);
    }
    
    @Override
    protected boolean canGeneratePermission() {
        return false;
    }
    
    @Override
    protected void executeSync(@Nonnull CommandContext ctx) {
        // Permission check - admin only
        PermissionService perms = PermissionService.get();
        if (!perms.canUseAdminCommand(ctx.sender(), Permissions.BROADCAST, 
                configManager.getConfig().broadcast.enabled)) {
            ctx.sendMessage(Message.raw(configManager.getMessage("noPermission")).color("#FF5555"));
            return;
        }
        
        // Parse raw input: "/broadcast <message...>"
        String rawInput = ctx.getInputString();
        String[] parts = rawInput.split("\\s+", 2);
        
        if (parts.length < 2) {
            ctx.sendMessage(Message.raw("Usage: /broadcast <message>").color("#FF5555"));
            return;
        }
        
        String message = parts[1];
        
        if (message.trim().isEmpty()) {
            ctx.sendMessage(Message.raw("Usage: /broadcast <message>").color("#FF5555"));
            return;
        }
        
        // Format broadcast message with config prefix/format
        String broadcastText = configManager.getMessage("broadcast", "message", message);
        
        // Broadcast to all online players
        broadcastMessage(broadcastText);
        
        if (configManager.isDebugEnabled()) {
            ctx.sendMessage(Message.raw("Broadcast sent to all players").color("#55FF55"));
        }
    }
    
    /**
     * Broadcast message to all online players with color code support.
     */
    private void broadcastMessage(String text) {
        // Use MessageFormatter to process color codes
        Message message = MessageFormatter.format(text);
        
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
