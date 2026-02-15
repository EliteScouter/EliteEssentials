package com.eliteessentials.commands.hytale;

import com.eliteessentials.config.ConfigManager;
import com.eliteessentials.model.GroupChat;
import com.eliteessentials.permissions.Permissions;
import com.eliteessentials.services.GroupChatService;
import com.eliteessentials.services.PlayerService;
import com.eliteessentials.util.CommandPermissionUtil;
import com.eliteessentials.util.MessageFormatter;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.server.core.command.system.CommandContext;
import com.hypixel.hytale.server.core.command.system.basecommands.AbstractPlayerCommand;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;

import javax.annotation.Nonnull;
import java.util.List;

/**
 * Set default group chat command - allows players to set their preferred chat channel.
 * 
 * Usage:
 * - /gcset <chat> - Set your default chat for /gc command
 * - /gcset - Show your current default chat
 */
public class HytaleGcSetCommand extends AbstractPlayerCommand {
    
    private final GroupChatService groupChatService;
    private final PlayerService playerService;
    private final ConfigManager configManager;
    
    public HytaleGcSetCommand(GroupChatService groupChatService, PlayerService playerService, 
                              ConfigManager configManager) {
        super("gcset", "Set your default group chat");
        this.groupChatService = groupChatService;
        this.playerService = playerService;
        this.configManager = configManager;
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
        
        // No argument - show current default
        if (parts.length < 2 || parts[1].isBlank()) {
            String currentDefault = playerService.getDefaultGroupChat(player.getUuid());
            if (currentDefault != null) {
                GroupChat defaultChat = groupChatService.getGroupChat(currentDefault);
                if (defaultChat != null && playerChats.contains(defaultChat)) {
                    ctx.sendMessage(MessageFormatter.format(
                        configManager.getMessage("groupChatDefaultCurrent", "chat", currentDefault)));
                    return;
                }
            }
            // No default set or lost access
            GroupChat firstChat = playerChats.get(0);
            ctx.sendMessage(MessageFormatter.format(
                configManager.getMessage("groupChatDefaultNone", "chat", firstChat.getGroupName())));
            return;
        }
        
        // Set new default
        String chatName = parts[1].trim();
        GroupChat targetChat = groupChatService.getGroupChat(chatName);
        
        if (targetChat == null) {
            ctx.sendMessage(MessageFormatter.format(
                configManager.getMessage("groupChatNotFound", "chat", chatName)));
            return;
        }
        
        if (!playerChats.contains(targetChat)) {
            ctx.sendMessage(MessageFormatter.format(
                configManager.getMessage("groupChatNoAccessSpecific", "chat", chatName)));
            return;
        }
        
        // Set the default
        playerService.setDefaultGroupChat(player.getUuid(), targetChat.getGroupName());
        ctx.sendMessage(MessageFormatter.format(
            configManager.getMessage("groupChatDefaultSet", "chat", targetChat.getGroupName())));
    }
}
