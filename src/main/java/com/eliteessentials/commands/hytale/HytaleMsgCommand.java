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
 * Command: /msg <player> <message>
 * Send a private message to another player.
 * 
 * Aliases: /m, /message, /whisper, /pm, /tell
 * 
 * Permissions:
 * - eliteessentials.command.misc.msg - Use /msg command
 */
public class HytaleMsgCommand extends AbstractPlayerCommand {

    private static final Logger logger = Logger.getLogger("EliteEssentials");
    private final MessageService messageService;
    private final ConfigManager configManager;

    public HytaleMsgCommand(MessageService messageService, ConfigManager configManager) {
        super("msg", "Send a private message to a player");
        this.messageService = messageService;
        this.configManager = configManager;
        
        // Allow extra arguments for multi-word messages
        setAllowsExtraArguments(true);
        
        addAliases("m", "message", "whisper", "pm", "tell");
    }

    @Override
    protected boolean canGeneratePermission() {
        return false;
    }

    @Override
    protected void execute(@Nonnull CommandContext ctx, @Nonnull Store<EntityStore> store, @Nonnull Ref<EntityStore> ref,
                          @Nonnull PlayerRef player, @Nonnull World world) {
        UUID senderId = player.getUuid();
        
        // Check permission
        if (!CommandPermissionUtil.canExecute(ctx, player, Permissions.MSG, 
                configManager.getConfig().msg.enabled)) {
            return;
        }

        // Parse raw input: "/msg <player> <message...>"
        String rawInput = ctx.getInputString();
        String[] parts = rawInput.split("\\s+", 3);
        
        if (parts.length < 3) {
            ctx.sendMessage(MessageFormatter.formatWithFallback(configManager.getMessage("msgUsage"), "#FF5555"));
            return;
        }
        
        String targetName = parts[1];
        String message = parts[2];

        // Find target player
        PlayerRef target = findPlayer(targetName);
        
        if (target == null) {
            ctx.sendMessage(MessageFormatter.formatWithFallback(configManager.getMessage("playerNotFound"), "#FF5555"));
            return;
        }

        if (target.getUuid().equals(senderId)) {
            ctx.sendMessage(MessageFormatter.formatWithFallback(configManager.getMessage("msgSelf"), "#FF5555"));
            return;
        }

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
        
        // Track for /reply
        messageService.recordMessage(senderId, target.getUuid());
    }

    /**
     * Send a private message between two players.
     */
    private void sendPrivateMessage(PlayerRef sender, PlayerRef target, String message, CommandContext ctx) {
        String senderName = sender.getUsername();
        String targetName = target.getUsername();
        
        // Format: [From PlayerName] message
        String toTarget = configManager.getMessage("msgReceived", 
            "player", senderName, "message", message);
        target.sendMessage(MessageFormatter.formatWithFallback(toTarget, "#D8BFD8")); // Light purple
        
        // Format: [To PlayerName] message
        String toSender = configManager.getMessage("msgSent", 
            "player", targetName, "message", message);
        ctx.sendMessage(MessageFormatter.formatWithFallback(toSender, "#D8BFD8"));
        
        // Broadcast to console if enabled
        if (configManager.getConfig().msg.broadcastToConsole) {
            logger.info("[MSG] " + senderName + " -> " + targetName + ": " + message);
        }
    }

    /**
     * Find a player by name (case-insensitive).
     */
    private PlayerRef findPlayer(String name) {
        List<PlayerRef> players = Universe.get().getPlayers();
        for (PlayerRef p : players) {
            if (p.getUsername().equalsIgnoreCase(name)) {
                return p;
            }
        }
        return null;
    }
}
