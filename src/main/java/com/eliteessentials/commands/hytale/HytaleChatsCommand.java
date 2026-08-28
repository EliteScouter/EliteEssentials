package com.eliteessentials.commands.hytale;

import com.eliteessentials.config.ConfigManager;
import com.eliteessentials.model.GroupChat;
import com.eliteessentials.permissions.Permissions;
import com.eliteessentials.services.GroupChatService;
import com.eliteessentials.util.CommandPermissionUtil;
import com.eliteessentials.util.MessageFormatter;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.server.core.command.system.CommandContext;
import com.eliteessentials.commands.base.ElitePlayerCommand;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;

import javax.annotation.Nonnull;
import java.util.List;
import com.eliteessentials.util.CommandSpyUtil;

/**
 * Lists all chat channels the player has access to.
 * Shows both group-based chats (from LuckPerms groups) and permission-based chats.
 * 
 * Usage: /chats
 */
public class HytaleChatsCommand extends ElitePlayerCommand {
    
    private final GroupChatService groupChatService;
    private final ConfigManager configManager;
    
    public HytaleChatsCommand(GroupChatService groupChatService, ConfigManager configManager) {
        super("chats", "List all chat channels you have access to");
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
        CommandSpyUtil.notify(ctx);
        
        // Permission check
        if (!CommandPermissionUtil.canExecute(ctx, player, Permissions.CHATS_LIST, 
                configManager.getConfig().groupChat.enabled)) {
            return;
        }
        
        // Get player's available chats
        List<GroupChat> playerChats = groupChatService.getPlayerGroupChats(player.getUuid());
        
        if (playerChats.isEmpty()) {
            ctx.sendMessage(MessageFormatter.format(
                configManager.getMessage("chatsNoAccess")));
            return;
        }
        
        // Show header
        ctx.sendMessage(MessageFormatter.format(
            configManager.getMessage("chatsHeader", "count", String.valueOf(playerChats.size()))));
        
        // List each chat
        for (GroupChat chat : playerChats) {
            String type = chat.isPermissionBased() ? "permission" : "group";
            String colorCode = chat.getColor();
            if (colorCode != null && colorCode.startsWith("#") && !colorCode.startsWith("&#")) {
                colorCode = "&" + colorCode;
            }
            
            // Add range info if applicable
            String rangeInfo = "";
            if (chat.hasRangeLimit()) {
                rangeInfo = " &7(" + chat.getRange() + " blocks)";
            }
            
            String entry = configManager.getMessage("chatsEntry",
                "color", colorCode != null ? colorCode : "&f",
                "name", chat.getGroupName(),
                "displayName", chat.getDisplayName(),
                "prefix", chat.getPrefix(),
                "type", type,
                "range", rangeInfo);
            ctx.sendMessage(MessageFormatter.format(entry));
        }
        
        // Show usage hint
        ctx.sendMessage(MessageFormatter.format(
            configManager.getMessage("chatsFooter")));
    }
}
