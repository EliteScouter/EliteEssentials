package com.eliteessentials.commands.hytale;

import com.eliteessentials.config.ConfigManager;
import com.eliteessentials.integration.LuckPermsIntegration;
import com.eliteessentials.permissions.Permissions;
import com.eliteessentials.permissions.PermissionService;
import com.eliteessentials.services.GroupChatService;
import com.eliteessentials.util.MessageFormatter;
import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.command.system.CommandContext;
import com.hypixel.hytale.server.core.command.system.basecommands.CommandBase;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.Universe;

import javax.annotation.Nonnull;
import java.util.ArrayList;
import java.util.List;
import java.util.logging.Logger;

/**
 * /sendmessage (alias: /sm) - Send a formatted message to a player, group, or all players.
 * 
 * Can be executed from console or by players with admin permission.
 * Supports full chat formatting including hex colors (&#RRGGBB), color codes (&0-f),
 * and placeholders ({player}, {server}, {world}, {playercount}).
 * 
 * Usage:
 *   /sendmessage player <name> <message>  - Send to specific player
 *   /sendmessage group <group> <message>  - Send to all players in a LuckPerms group
 *   /sendmessage all <message>            - Send to all online players
 * 
 * Aliases: /sm
 * Permission: eliteessentials.admin.sendmessage (Admin only)
 */
public class HytaleSendMessageCommand extends CommandBase {
    
    private static final Logger logger = Logger.getLogger("EliteEssentials");
    
    private final ConfigManager configManager;
    private final GroupChatService groupChatService;
    
    public HytaleSendMessageCommand(ConfigManager configManager, GroupChatService groupChatService) {
        super("sendmessage", "Send a formatted message to player/group/all");
        this.configManager = configManager;
        this.groupChatService = groupChatService;
        
        addAliases("sm");
        setAllowsExtraArguments(true);
    }
    
    @Override
    protected boolean canGeneratePermission() {
        return false;
    }
    
    @Override
    protected void executeSync(@Nonnull CommandContext ctx) {
        // Permission check - admin only (but allow console)
        PermissionService perms = PermissionService.get();
        if (!perms.canUseAdminCommand(ctx.sender(), Permissions.ADMIN_SENDMESSAGE, true)) {
            ctx.sendMessage(MessageFormatter.formatWithFallback(configManager.getMessage("noPermission"), "#FF5555"));
            return;
        }
        
        // Parse raw input: "/sendmessage <type> [target] <message...>"
        String rawInput = ctx.getInputString();
        String[] parts = rawInput.split("\\s+", 2);
        
        if (parts.length < 2) {
            sendUsage(ctx);
            return;
        }
        
        String args = parts[1];
        String[] argParts = args.split("\\s+", 3);
        
        if (argParts.length < 2) {
            sendUsage(ctx);
            return;
        }
        
        String type = argParts[0].toLowerCase();
        
        switch (type) {
            case "player":
                handlePlayerMessage(ctx, argParts);
                break;
            case "group":
                handleGroupMessage(ctx, argParts);
                break;
            case "all":
                handleAllMessage(ctx, argParts);
                break;
            default:
                sendUsage(ctx);
                break;
        }
    }
    
    /**
     * Send message to a specific player.
     */
    private void handlePlayerMessage(CommandContext ctx, String[] argParts) {
        if (argParts.length < 3) {
            ctx.sendMessage(Message.raw("Usage: /sendmessage player <name> <message>").color("#FF5555"));
            return;
        }
        
        String targetName = argParts[1];
        String message = argParts[2];
        
        PlayerRef target = findOnlinePlayer(targetName);
        if (target == null) {
            ctx.sendMessage(MessageFormatter.formatWithFallback(
                configManager.getMessage("playerNotFound", "player", targetName), "#FF5555"));
            return;
        }
        
        // Process placeholders and format message
        String formatted = processPlaceholders(message, target);
        Message formattedMsg = MessageFormatter.format(formatted);
        
        target.sendMessage(formattedMsg);
        
        ctx.sendMessage(Message.raw("Message sent to " + target.getUsername()).color("#55FF55"));
        
        if (configManager.isDebugEnabled()) {
            logger.info("SendMessage: Sent to player " + target.getUsername());
        }
    }
    
    /**
     * Send message to all players in a LuckPerms group.
     */
    private void handleGroupMessage(CommandContext ctx, String[] argParts) {
        if (argParts.length < 3) {
            ctx.sendMessage(Message.raw("Usage: /sendmessage group <group> <message>").color("#FF5555"));
            return;
        }
        
        String groupName = argParts[1];
        String message = argParts[2];
        
        if (!LuckPermsIntegration.isAvailable()) {
            ctx.sendMessage(Message.raw("LuckPerms is required for group messages.").color("#FF5555"));
            return;
        }
        
        // Find all online players in the group
        List<PlayerRef> recipients = new ArrayList<>();
        Universe universe = Universe.get();
        
        if (universe != null) {
            for (PlayerRef player : universe.getPlayers()) {
                if (player != null && player.isValid()) {
                    List<String> playerGroups = LuckPermsIntegration.getGroups(player.getUuid());
                    for (String group : playerGroups) {
                        if (group.equalsIgnoreCase(groupName)) {
                            recipients.add(player);
                            break;
                        }
                    }
                }
            }
        }
        
        if (recipients.isEmpty()) {
            ctx.sendMessage(Message.raw("No online players found in group '" + groupName + "'.").color("#FFAA00"));
            return;
        }
        
        // Send to all recipients
        for (PlayerRef recipient : recipients) {
            String formatted = processPlaceholders(message, recipient);
            Message formattedMsg = MessageFormatter.format(formatted);
            recipient.sendMessage(formattedMsg);
        }
        
        ctx.sendMessage(Message.raw("Message sent to " + recipients.size() + " player(s) in group '" + groupName + "'.").color("#55FF55"));
        
        if (configManager.isDebugEnabled()) {
            logger.info("SendMessage: Sent to " + recipients.size() + " players in group " + groupName);
        }
    }
    
    /**
     * Send message to all online players.
     */
    private void handleAllMessage(CommandContext ctx, String[] argParts) {
        // For "all", the message starts at index 1 (no target needed)
        String message;
        if (argParts.length >= 2) {
            // Reconstruct message from remaining parts
            StringBuilder sb = new StringBuilder();
            for (int i = 1; i < argParts.length; i++) {
                if (sb.length() > 0) sb.append(" ");
                sb.append(argParts[i]);
            }
            message = sb.toString();
        } else {
            ctx.sendMessage(Message.raw("Usage: /sendmessage all <message>").color("#FF5555"));
            return;
        }
        
        if (message.trim().isEmpty()) {
            ctx.sendMessage(Message.raw("Usage: /sendmessage all <message>").color("#FF5555"));
            return;
        }
        
        Universe universe = Universe.get();
        if (universe == null) {
            ctx.sendMessage(Message.raw("No players online.").color("#FFAA00"));
            return;
        }
        
        List<PlayerRef> players = universe.getPlayers();
        if (players.isEmpty()) {
            ctx.sendMessage(Message.raw("No players online.").color("#FFAA00"));
            return;
        }
        
        int count = 0;
        for (PlayerRef player : players) {
            if (player != null && player.isValid()) {
                String formatted = processPlaceholders(message, player);
                Message formattedMsg = MessageFormatter.format(formatted);
                player.sendMessage(formattedMsg);
                count++;
            }
        }
        
        ctx.sendMessage(Message.raw("Message sent to " + count + " player(s).").color("#55FF55"));
        
        if (configManager.isDebugEnabled()) {
            logger.info("SendMessage: Sent to " + count + " players (all)");
        }
    }
    
    /**
     * Process placeholders in the message.
     */
    private String processPlaceholders(String message, PlayerRef player) {
        String result = message;
        
        // {player} - player name
        result = result.replace("{player}", player.getUsername());
        
        // {server} - server name (use "Hytale Server" as default)
        result = result.replace("{server}", "Hytale Server");
        
        // {world} - player's current world (find by iterating worlds)
        try {
            String worldName = "unknown";
            Universe universe = Universe.get();
            if (universe != null) {
                for (var entry : universe.getWorlds().entrySet()) {
                    if (entry.getValue().getPlayerRefs().contains(player)) {
                        worldName = entry.getKey();
                        break;
                    }
                }
            }
            result = result.replace("{world}", worldName);
        } catch (Exception e) {
            result = result.replace("{world}", "unknown");
        }
        
        // {playercount} - online player count
        try {
            int playerCount = Universe.get() != null ? Universe.get().getPlayers().size() : 0;
            result = result.replace("{playercount}", String.valueOf(playerCount));
        } catch (Exception e) {
            result = result.replace("{playercount}", "0");
        }
        
        return result;
    }
    
    /**
     * Find an online player by name (case-insensitive).
     */
    private PlayerRef findOnlinePlayer(String name) {
        Universe universe = Universe.get();
        if (universe == null) return null;
        
        for (PlayerRef p : universe.getPlayers()) {
            if (p.getUsername().equalsIgnoreCase(name)) {
                return p;
            }
        }
        return null;
    }
    
    /**
     * Send usage information.
     */
    private void sendUsage(CommandContext ctx) {
        ctx.sendMessage(Message.raw("Usage:").color("#FFAA00"));
        ctx.sendMessage(Message.raw("  /sendmessage player <name> <message>").color("#AAAAAA"));
        ctx.sendMessage(Message.raw("  /sendmessage group <group> <message>").color("#AAAAAA"));
        ctx.sendMessage(Message.raw("  /sendmessage all <message>").color("#AAAAAA"));
        ctx.sendMessage(Message.raw("Placeholders: {player}, {server}, {world}, {playercount}").color("#AAAAAA"));
        ctx.sendMessage(Message.raw("Supports color codes: &0-f, &#RRGGBB, &l, &o, &r").color("#AAAAAA"));
    }
}
