package com.eliteessentials.commands.hytale;

import com.eliteessentials.config.ConfigManager;
import com.eliteessentials.permissions.Permissions;
import com.eliteessentials.services.MessageService;
import com.eliteessentials.util.CommandPermissionUtil;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.command.system.CommandContext;
import com.hypixel.hytale.server.core.command.system.basecommands.AbstractPlayerCommand;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.Universe;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;

import java.util.List;
import java.util.UUID;

/**
 * Command: /reply <message> or /r <message>
 * Reply to the last player who messaged you.
 * 
 * Permissions:
 * - eliteessentials.command.misc.msg - Use /reply command (same as /msg)
 */
public class HytaleReplyCommand extends AbstractPlayerCommand {

    private final MessageService messageService;
    private final ConfigManager configManager;

    public HytaleReplyCommand(MessageService messageService, ConfigManager configManager) {
        super("reply", "Reply to the last player who messaged you");
        this.messageService = messageService;
        this.configManager = configManager;
        
        setAllowsExtraArguments(true);
        addAliases("r");
    }

    @Override
    protected boolean canGeneratePermission() {
        return false;
    }

    @Override
    protected void execute(CommandContext ctx, Store<EntityStore> store, Ref<EntityStore> ref,
                          PlayerRef player, World world) {
        UUID senderId = player.getUuid();
        
        // Check permission (uses same permission as /msg)
        if (!CommandPermissionUtil.canExecute(ctx, player, Permissions.MSG, 
                configManager.getConfig().msg.enabled)) {
            return;
        }

        // Get last conversation partner
        UUID lastPartnerId = messageService.getLastPartner(senderId);
        if (lastPartnerId == null) {
            ctx.sendMessage(Message.raw(configManager.getMessage("replyNoOne")).color("#FF5555"));
            return;
        }

        // Find the partner player
        PlayerRef target = findPlayerByUuid(lastPartnerId);
        if (target == null) {
            ctx.sendMessage(Message.raw(configManager.getMessage("replyOffline")).color("#FF5555"));
            return;
        }

        // Parse message from raw input: "/reply <message...>" or "/r <message...>"
        String rawInput = ctx.getInputString();
        String[] parts = rawInput.split("\\s+", 2);
        
        if (parts.length < 2) {
            ctx.sendMessage(Message.raw(configManager.getMessage("replyUsage")).color("#FF5555"));
            return;
        }
        
        String message = parts[1];

        // Send the message
        sendPrivateMessage(player, target, message, ctx);
        
        // Update tracking
        messageService.recordMessage(senderId, target.getUuid());
    }

    private void sendPrivateMessage(PlayerRef sender, PlayerRef target, String message, CommandContext ctx) {
        String senderName = sender.getUsername();
        String targetName = target.getUsername();
        
        String toTarget = configManager.getMessage("msgReceived", 
            "player", senderName, "message", message);
        target.sendMessage(Message.raw(toTarget).color("#D8BFD8"));
        
        String toSender = configManager.getMessage("msgSent", 
            "player", targetName, "message", message);
        ctx.sendMessage(Message.raw(toSender).color("#D8BFD8"));
    }

    private PlayerRef findPlayerByUuid(UUID uuid) {
        List<PlayerRef> players = Universe.get().getPlayers();
        for (PlayerRef p : players) {
            if (p.getUuid().equals(uuid)) {
                return p;
            }
        }
        return null;
    }
}
