package com.eliteessentials.commands.hytale;

import com.eliteessentials.config.ConfigManager;
import com.eliteessentials.permissions.Permissions;
import com.eliteessentials.services.GroupChatService;
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

/**
 * Toggle group chat spy mode - allows admins to see messages from all
 * group chat channels, even ones they don't belong to.
 * 
 * Usage: /gcspy
 */
public class HytaleGcSpyCommand extends AbstractPlayerCommand {
    
    private final GroupChatService groupChatService;
    private final ConfigManager configManager;
    
    public HytaleGcSpyCommand(GroupChatService groupChatService, ConfigManager configManager) {
        super("gcspy", "Toggle group chat spy mode");
        this.groupChatService = groupChatService;
        this.configManager = configManager;
    }
    
    @Override
    protected boolean canGeneratePermission() {
        return false;
    }
    
    @Override
    protected void execute(@Nonnull CommandContext ctx, @Nonnull Store<EntityStore> store,
                          @Nonnull Ref<EntityStore> ref, @Nonnull PlayerRef player, @Nonnull World world) {
        
        // Admin-only command
        if (!CommandPermissionUtil.canExecuteAdmin(ctx, player, Permissions.GROUP_CHAT_SPY, true)) {
            return;
        }
        
        // Check if spy is enabled in config
        if (!configManager.getConfig().groupChat.allowSpy) {
            ctx.sendMessage(MessageFormatter.format(
                configManager.getMessage("groupChatDisabled")));
            return;
        }
        
        boolean enabled = groupChatService.toggleSpy(player.getUuid());
        
        if (enabled) {
            ctx.sendMessage(MessageFormatter.format(
                configManager.getMessage("groupChatSpyEnabled")));
        } else {
            ctx.sendMessage(MessageFormatter.format(
                configManager.getMessage("groupChatSpyDisabled")));
        }
    }
}
