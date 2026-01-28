package com.eliteessentials.commands.hytale;

import com.eliteessentials.EliteEssentials;
import com.eliteessentials.config.ConfigManager;
import com.eliteessentials.model.GroupChat;
import com.eliteessentials.permissions.Permissions;
import com.eliteessentials.services.GroupChatService;
import com.eliteessentials.util.CommandPermissionUtil;
import com.eliteessentials.util.MessageFormatter;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.command.system.CommandContext;
import com.hypixel.hytale.server.core.command.system.basecommands.AbstractPlayerCommand;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;

import javax.annotation.Nonnull;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Group chat command - allows players to chat in private channels.
 * 
 * Two types of chat channels:
 * 1. Group-based: Tied to LuckPerms groups (e.g., admin, mod, staff)
 * 2. Permission-based: Tied to permissions (e.g., trade chat with eliteessentials.chat.trade)
 * 
 * Usage:
 * - /gc <message> - Send to your primary chat (or only chat)
 * - /gc <chat> <message> - Send to a specific chat you have access to
 * - /g <message> - Alias for /gc
 * 
 * Aliases: /groupchat, /gchat, /g
 */
public class HytaleGroupChatCommand extends AbstractPlayerCommand {
    
    private final GroupChatService groupChatService;
    private final ConfigManager configManager;
    
    public HytaleGroupChatCommand(GroupChatService groupChatService, ConfigManager configManager) {
        super("gc", "Send a message to a private chat channel");
        this.groupChatService = groupChatService;
        this.configManager = configManager;
        this.addAliases("groupchat", "gchat", "g");
        this.setAllowsExtraArguments(true);
    }
    
    @Override
    protected boolean canGeneratePermission() {
        return false;
    }
    
    @Override
    protected void execute(@Nonnull CommandContext ctx, @Nonnull Store<EntityStore> store,
                          @Nonnull Ref<EntityStore> ref, @Nonnull PlayerRef player, @Nonnull World world) {
        
        // Permission check
        if (!CommandPermissionUtil.canExecute(ctx, player, Permissions.GROUP_CHAT, 
                configManager.getConfig().groupChat.enabled)) {
            return;
        }
        
        // Get player's available chats
        List<GroupChat> playerChats = groupChatService.getPlayerGroupChats(player.getUuid());
        
        if (playerChats.isEmpty()) {
            ctx.sendMessage(MessageFormatter.format(
                configManager.getMessage("groupChatNoAccess")));
            return;
        }
        
        // Parse input
        String inputString = ctx.getInputString().trim();
        String[] parts = inputString.split("\\s+", 2);
        
        if (parts.length < 2 || parts[1].isBlank()) {
            showUsage(ctx, playerChats);
            return;
        }
        
        String remainder = parts[1];
        GroupChat targetChat;
        String message;
        
        // Only check for chat name prefix if player has access to multiple chats
        if (playerChats.size() > 1) {
            String[] messageParts = remainder.split("\\s+", 2);
            GroupChat specifiedChat = groupChatService.getGroupChat(messageParts[0]);
            
            if (specifiedChat != null && playerChats.contains(specifiedChat)) {
                // Player specified a chat they have access to
                if (messageParts.length < 2 || messageParts[1].isBlank()) {
                    ctx.sendMessage(MessageFormatter.format(
                        configManager.getMessage("groupChatUsageGroup", "group", specifiedChat.getGroupName())));
                    return;
                }
                targetChat = specifiedChat;
                message = messageParts[1];
            } else {
                // First word isn't a valid chat, use default chat with full message
                targetChat = playerChats.get(0);
                message = remainder;
            }
        } else {
            // Player only has one chat, use it with full message
            targetChat = playerChats.get(0);
            message = remainder;
        }
        
        // Send the message
        groupChatService.broadcast(targetChat, player, message);
    }
    
    /**
     * Show usage information based on player's available chats.
     */
    private void showUsage(@Nonnull CommandContext ctx, @Nonnull List<GroupChat> chats) {
        if (chats.size() == 1) {
            ctx.sendMessage(MessageFormatter.format(
                configManager.getMessage("groupChatUsage")));
        } else {
            String chatNames = chats.stream()
                .map(GroupChat::getGroupName)
                .collect(Collectors.joining(", "));
            ctx.sendMessage(MessageFormatter.format(
                configManager.getMessage("groupChatUsageMultiple", "groups", chatNames)));
        }
    }
}
