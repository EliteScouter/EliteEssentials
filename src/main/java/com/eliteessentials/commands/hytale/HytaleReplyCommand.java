package com.eliteessentials.commands.hytale;

import com.eliteessentials.config.ConfigManager;
import com.eliteessentials.EliteEssentials;
import com.eliteessentials.permissions.Permissions;
import com.eliteessentials.services.MessageService;
import com.eliteessentials.util.CommandPermissionUtil;
import com.eliteessentials.util.MessageFormatter;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.server.core.command.system.CommandContext;
import com.hypixel.hytale.server.core.command.system.basecommands.AbstractPlayerCommand;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.Universe;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;

import java.util.List;
import java.util.UUID;
import java.util.logging.Logger;

import javax.annotation.Nonnull;

/**
 * Command: /reply <message> or /r <message>
 * Reply to the last player who messaged you.
 * 
 * Permissions:
 * - eliteessentials.command.misc.msg - Use /reply command (same as /msg)
 */
public class HytaleReplyCommand extends AbstractPlayerCommand {

    private static final Logger logger = Logger.getLogger("EliteEssentials");
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
    protected void execute(@Nonnull CommandContext ctx, @Nonnull Store<EntityStore> store, @Nonnull Ref<EntityStore> ref,
                          @Nonnull PlayerRef player, @Nonnull World world) {
        UUID senderId = player.getUuid();
        
        // Check permission (uses same permission as /msg)
        if (!CommandPermissionUtil.canExecute(ctx, player, Permissions.MSG, 
                configManager.getConfig().msg.enabled)) {
            return;
        }

        // Get last conversation partner
        UUID lastPartnerId = messageService.getLastPartner(senderId);
        if (lastPartnerId == null) {
            ctx.sendMessage(MessageFormatter.formatWithFallback(configManager.getMessage("replyNoOne"), "#FF5555"));
            return;
        }

        // Find the partner player
        PlayerRef target = findPlayerByUuid(lastPartnerId);
        if (target == null) {
            ctx.sendMessage(MessageFormatter.formatWithFallback(configManager.getMessage("replyOffline"), "#FF5555"));
            return;
        }

        // Parse message from raw input: "/reply <message...>" or "/r <message...>"
        String rawInput = ctx.getInputString();
        String[] parts = rawInput.split("\\s+", 2);
        
        if (parts.length < 2) {
            ctx.sendMessage(MessageFormatter.formatWithFallback(configManager.getMessage("replyUsage"), "#FF5555"));
            return;
        }
        
        String message = parts[1];

        // Block muted players from sending private messages
        var muteService = EliteEssentials.getInstance().getMuteService();
        if (muteService != null && muteService.isMuted(senderId)) {
            ctx.sendMessage(MessageFormatter.formatWithFallback(
                configManager.getMessage("mutedBlocked"), "#FF5555"));
            return;
        }

        // Check if the target is ignoring the sender
        var ignoreService = EliteEssentials.getInstance().getIgnoreService();
        if (ignoreService != null && ignoreService.isIgnoring(target.getUuid(), senderId)) {
            // Silently fail - sender sees their message as sent but target doesn't receive it
            String toSender = configManager.getMessage("msgSent",
                "player", target.getUsername(), "message", message);
            ctx.sendMessage(MessageFormatter.formatWithFallback(toSender, "#D8BFD8"));
            messageService.recordMessage(senderId, target.getUuid());
            return;
        }

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
        target.sendMessage(MessageFormatter.formatWithFallback(toTarget, "#D8BFD8"));
        
        String toSender = configManager.getMessage("msgSent", 
            "player", targetName, "message", message);
        ctx.sendMessage(MessageFormatter.formatWithFallback(toSender, "#D8BFD8"));
        
        // Broadcast to console if enabled
        if (configManager.getConfig().msg.broadcastToConsole) {
            logger.info("[MSG] " + senderName + " -> " + targetName + ": " + message);
        }
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
