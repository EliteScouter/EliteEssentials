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
 * Group chat command - allows players to chat privately with their LuckPerms group.
 * 
 * Usage:
 * - /gc <message> - Send to your primary group (or only group)
 * - /gc <group> <message> - Send to a specific group you belong to
 * 
 * Aliases: /groupchat, /gchat
 */
public class HytaleGroupChatCommand extends AbstractPlayerCommand {
    
    private final GroupChatService groupChatService;
    private final ConfigManager configManager;
    
    public HytaleGroupChatCommand(GroupChatService groupChatService, ConfigManager configManager) {
        super("gc", "Send a message to your group's private chat");
        this.groupChatService = groupChatService;
        this.configManager = configManager;
        this.addAliases("groupchat", "gchat");
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
        
        // Get player's available group chats
        List<GroupChat> playerGroups = groupChatService.getPlayerGroupChats(player.getUuid());
        
        if (playerGroups.isEmpty()) {
            ctx.sendMessage(MessageFormatter.format(
                configManager.getMessage("groupChatNoAccess")));
            return;
        }
        
        // Parse input
        String inputString = ctx.getInputString().trim();
        String[] parts = inputString.split("\\s+", 2);
        
        if (parts.length < 2 || parts[1].isBlank()) {
            showUsage(ctx, playerGroups);
            return;
        }
        
        String remainder = parts[1];
        GroupChat targetGroup;
        String message;
        
        // Only check for group name prefix if player belongs to multiple groups
        if (playerGroups.size() > 1) {
            String[] messageParts = remainder.split("\\s+", 2);
            GroupChat specifiedGroup = groupChatService.getGroupChat(messageParts[0]);
            
            if (specifiedGroup != null && playerGroups.contains(specifiedGroup)) {
                // Player specified a group they belong to
                if (messageParts.length < 2 || messageParts[1].isBlank()) {
                    ctx.sendMessage(MessageFormatter.format(
                        configManager.getMessage("groupChatUsageGroup", "group", specifiedGroup.getGroupName())));
                    return;
                }
                targetGroup = specifiedGroup;
                message = messageParts[1];
            } else {
                // First word isn't a valid group, use default group with full message
                targetGroup = playerGroups.get(0);
                message = remainder;
            }
        } else {
            // Player only has one group, use it with full message
            targetGroup = playerGroups.get(0);
            message = remainder;
        }
        
        // Send the message
        groupChatService.broadcast(targetGroup, player, message);
    }
    
    /**
     * Show usage information based on player's available groups.
     */
    private void showUsage(@Nonnull CommandContext ctx, @Nonnull List<GroupChat> groups) {
        if (groups.size() == 1) {
            ctx.sendMessage(MessageFormatter.format(
                configManager.getMessage("groupChatUsage")));
        } else {
            String groupNames = groups.stream()
                .map(GroupChat::getGroupName)
                .collect(Collectors.joining(", "));
            ctx.sendMessage(MessageFormatter.format(
                configManager.getMessage("groupChatUsageMultiple", "groups", groupNames)));
        }
    }
}
