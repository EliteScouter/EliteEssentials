package com.eliteessentials.commands.hytale;

import com.eliteessentials.config.ConfigManager;
import com.eliteessentials.model.MailMessage;
import com.eliteessentials.permissions.Permissions;
import com.eliteessentials.services.MailService;
import com.eliteessentials.storage.PlayerFileStorage;
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

import javax.annotation.Nonnull;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Command: /mail [send|read|list|clear|delete] [args...]
 * 
 * Subcommands:
 * - /mail send <player> <message> - Send mail to a player
 * - /mail read [number] - Read mail (marks as read)
 * - /mail list - List all mail
 * - /mail clear - Clear all mail
 * - /mail clear read - Clear only read mail
 * - /mail delete <id> - Delete specific mail
 * 
 * Permissions:
 * - eliteessentials.command.mail.use - Use mail commands
 * - eliteessentials.command.mail.send - Send mail
 */
public class HytaleMailCommand extends AbstractPlayerCommand {

    private final MailService mailService;
    private final ConfigManager configManager;
    private final PlayerFileStorage playerFileStorage;
    private static final SimpleDateFormat DATE_FORMAT = new SimpleDateFormat("MM/dd HH:mm");

    public HytaleMailCommand(MailService mailService, ConfigManager configManager, 
                             PlayerFileStorage playerFileStorage) {
        super("mail", "Send and receive mail from other players");
        this.mailService = mailService;
        this.configManager = configManager;
        this.playerFileStorage = playerFileStorage;
        
        setAllowsExtraArguments(true);
    }

    @Override
    protected boolean canGeneratePermission() {
        return false;
    }

    @Override
    protected void execute(@Nonnull CommandContext ctx, @Nonnull Store<EntityStore> store, 
                          @Nonnull Ref<EntityStore> ref, @Nonnull PlayerRef player, 
                          @Nonnull World world) {
        UUID playerId = player.getUuid();
        
        // Check permission
        if (!CommandPermissionUtil.canExecute(ctx, player, Permissions.MAIL, 
                configManager.getConfig().mail.enabled)) {
            return;
        }

        // Parse command
        String rawInput = ctx.getInputString();
        String[] parts = rawInput.split("\\s+");
        
        // /mail with no args shows help or list
        if (parts.length < 2) {
            showMailList(ctx, player, playerId);
            return;
        }
        
        String subCommand = parts[1].toLowerCase();
        
        switch (subCommand) {
            case "send" -> handleSend(ctx, player, parts);
            case "read" -> handleRead(ctx, player, playerId, parts);
            case "list" -> showMailList(ctx, player, playerId);
            case "clear" -> handleClear(ctx, player, playerId, parts);
            case "delete", "del" -> handleDelete(ctx, player, playerId, parts);
            default -> showUsage(ctx);
        }
    }
    
    private void handleSend(CommandContext ctx, PlayerRef player, String[] parts) {
        // Check send permission
        if (!CommandPermissionUtil.canExecute(ctx, player, Permissions.MAIL_SEND, 
                configManager.getConfig().mail.enabled)) {
            return;
        }
        
        // /mail send <player> <message>
        if (parts.length < 4) {
            ctx.sendMessage(MessageFormatter.formatWithFallback(
                configManager.getMessage("mailSendUsage"), "#FF5555"));
            return;
        }
        
        String targetName = parts[2];
        
        // Build message from remaining parts
        StringBuilder messageBuilder = new StringBuilder();
        for (int i = 3; i < parts.length; i++) {
            if (i > 3) messageBuilder.append(" ");
            messageBuilder.append(parts[i]);
        }
        String message = messageBuilder.toString();
        
        // Check message length
        int maxLength = configManager.getConfig().mail.maxMessageLength;
        if (message.length() > maxLength) {
            ctx.sendMessage(MessageFormatter.formatWithFallback(
                configManager.getMessage("mailMessageTooLong", "max", String.valueOf(maxLength)), 
                "#FF5555"));
            return;
        }
        
        // Can't send to self
        if (targetName.equalsIgnoreCase(player.getUsername())) {
            ctx.sendMessage(MessageFormatter.formatWithFallback(
                configManager.getMessage("mailSendSelf"), "#FF5555"));
            return;
        }
        
        // Find recipient UUID
        Optional<UUID> recipientUuid = playerFileStorage.getUuidByName(targetName);
        if (recipientUuid.isEmpty()) {
            ctx.sendMessage(MessageFormatter.formatWithFallback(
                configManager.getMessage("mailPlayerNotFound", "player", targetName), "#FF5555"));
            return;
        }
        
        // Send the mail
        MailService.SendResult result = mailService.sendMail(
            player.getUuid(), player.getUsername(), recipientUuid.get(), message);
        
        if (result.success) {
            ctx.sendMessage(MessageFormatter.formatWithFallback(
                configManager.getMessage("mailSent", "player", targetName), "#55FF55"));
            
            // Notify recipient if online
            notifyRecipientIfOnline(recipientUuid.get(), player.getUsername());
        } else {
            switch (result.reason) {
                case "cooldown" -> ctx.sendMessage(MessageFormatter.formatWithFallback(
                    configManager.getMessage("mailOnCooldown", 
                        "seconds", String.valueOf(result.cooldownRemaining)), "#FF5555"));
                case "mailboxFull" -> ctx.sendMessage(MessageFormatter.formatWithFallback(
                    configManager.getMessage("mailRecipientFull", "player", targetName), "#FF5555"));
                default -> ctx.sendMessage(MessageFormatter.formatWithFallback(
                    configManager.getMessage("mailSendFailed"), "#FF5555"));
            }
        }
    }
    
    private void handleRead(CommandContext ctx, PlayerRef player, UUID playerId, String[] parts) {
        List<MailMessage> mail = mailService.getMail(playerId);
        
        if (mail.isEmpty()) {
            ctx.sendMessage(MessageFormatter.formatWithFallback(
                configManager.getMessage("mailEmpty"), "#FFAA00"));
            return;
        }
        
        // /mail read [number] - read specific mail or first unread
        int index = 0;
        if (parts.length >= 3) {
            try {
                index = Integer.parseInt(parts[2]) - 1; // 1-indexed for users
            } catch (NumberFormatException e) {
                ctx.sendMessage(MessageFormatter.formatWithFallback(
                    configManager.getMessage("mailInvalidNumber"), "#FF5555"));
                return;
            }
        } else {
            // Find first unread
            for (int i = 0; i < mail.size(); i++) {
                if (!mail.get(i).isRead()) {
                    index = i;
                    break;
                }
            }
        }
        
        if (index < 0 || index >= mail.size()) {
            ctx.sendMessage(MessageFormatter.formatWithFallback(
                configManager.getMessage("mailNotFound"), "#FF5555"));
            return;
        }
        
        MailMessage msg = mail.get(index);
        
        // Display the mail
        String dateStr = DATE_FORMAT.format(new Date(msg.getTimestamp()));
        ctx.sendMessage(MessageFormatter.formatWithFallback(
            configManager.getMessage("mailReadHeader", 
                "number", String.valueOf(index + 1),
                "total", String.valueOf(mail.size())), "#55FFFF"));
        ctx.sendMessage(MessageFormatter.formatWithFallback(
            configManager.getMessage("mailReadFrom", 
                "player", msg.getSenderName(),
                "date", dateStr), "#AAAAAA"));
        ctx.sendMessage(MessageFormatter.formatWithFallback(
            configManager.getMessage("mailReadContent", "message", msg.getMessage()), "#FFFFFF"));
        
        // Mark as read
        if (!msg.isRead()) {
            mailService.markAsRead(playerId, msg.getId());
        }
    }
    
    private void showMailList(CommandContext ctx, PlayerRef player, UUID playerId) {
        List<MailMessage> mail = mailService.getMail(playerId);
        
        if (mail.isEmpty()) {
            ctx.sendMessage(MessageFormatter.formatWithFallback(
                configManager.getMessage("mailEmpty"), "#FFAA00"));
            return;
        }
        
        int unread = mailService.getUnreadCount(playerId);
        ctx.sendMessage(MessageFormatter.formatWithFallback(
            configManager.getMessage("mailListHeader", 
                "count", String.valueOf(mail.size()),
                "unread", String.valueOf(unread)), "#55FFFF"));
        
        // Show up to 10 most recent
        int shown = Math.min(mail.size(), 10);
        for (int i = 0; i < shown; i++) {
            MailMessage msg = mail.get(i);
            String status = msg.isRead() ? "&7" : "&a[NEW] ";
            String dateStr = DATE_FORMAT.format(new Date(msg.getTimestamp()));
            String preview = msg.getMessage();
            if (preview.length() > 25) {
                preview = preview.substring(0, 22) + "...";
            }
            
            String line = configManager.getMessage("mailListEntry",
                "number", String.valueOf(i + 1),
                "status", status,
                "date", dateStr,
                "player", msg.getSenderName(),
                "preview", preview);
            ctx.sendMessage(MessageFormatter.format(line));
        }
        
        if (mail.size() > 10) {
            ctx.sendMessage(MessageFormatter.formatWithFallback(
                configManager.getMessage("mailListMore", 
                    "count", String.valueOf(mail.size() - 10)), "#AAAAAA"));
        }
        
        ctx.sendMessage(MessageFormatter.formatWithFallback(
            configManager.getMessage("mailListFooter"), "#AAAAAA"));
    }
    
    private void handleClear(CommandContext ctx, PlayerRef player, UUID playerId, String[] parts) {
        // /mail clear [read]
        boolean readOnly = parts.length >= 3 && parts[2].equalsIgnoreCase("read");
        
        int cleared;
        if (readOnly) {
            cleared = mailService.clearReadMail(playerId);
            ctx.sendMessage(MessageFormatter.formatWithFallback(
                configManager.getMessage("mailClearedRead", "count", String.valueOf(cleared)), 
                "#55FF55"));
        } else {
            cleared = mailService.clearMail(playerId);
            ctx.sendMessage(MessageFormatter.formatWithFallback(
                configManager.getMessage("mailCleared", "count", String.valueOf(cleared)), 
                "#55FF55"));
        }
    }
    
    private void handleDelete(CommandContext ctx, PlayerRef player, UUID playerId, String[] parts) {
        if (parts.length < 3) {
            ctx.sendMessage(MessageFormatter.formatWithFallback(
                configManager.getMessage("mailDeleteUsage"), "#FF5555"));
            return;
        }
        
        // Try to parse as number first (mail index)
        try {
            int index = Integer.parseInt(parts[2]) - 1;
            List<MailMessage> mail = mailService.getMail(playerId);
            
            if (index < 0 || index >= mail.size()) {
                ctx.sendMessage(MessageFormatter.formatWithFallback(
                    configManager.getMessage("mailNotFound"), "#FF5555"));
                return;
            }
            
            String mailId = mail.get(index).getId();
            if (mailService.deleteMail(playerId, mailId)) {
                ctx.sendMessage(MessageFormatter.formatWithFallback(
                    configManager.getMessage("mailDeleted"), "#55FF55"));
            } else {
                ctx.sendMessage(MessageFormatter.formatWithFallback(
                    configManager.getMessage("mailDeleteFailed"), "#FF5555"));
            }
        } catch (NumberFormatException e) {
            // Try as mail ID
            if (mailService.deleteMail(playerId, parts[2])) {
                ctx.sendMessage(MessageFormatter.formatWithFallback(
                    configManager.getMessage("mailDeleted"), "#55FF55"));
            } else {
                ctx.sendMessage(MessageFormatter.formatWithFallback(
                    configManager.getMessage("mailNotFound"), "#FF5555"));
            }
        }
    }
    
    private void showUsage(CommandContext ctx) {
        ctx.sendMessage(MessageFormatter.formatWithFallback(
            configManager.getMessage("mailUsage"), "#FFAA00"));
    }
    
    /**
     * Notify recipient if they're online that they received new mail.
     */
    private void notifyRecipientIfOnline(UUID recipientUuid, String senderName) {
        try {
            for (PlayerRef p : Universe.get().getPlayers()) {
                if (p.getUuid().equals(recipientUuid)) {
                    p.sendMessage(MessageFormatter.formatWithFallback(
                        configManager.getMessage("mailReceived", "player", senderName), 
                        "#55FF55"));
                    break;
                }
            }
        } catch (Exception e) {
            // Ignore - player might have disconnected
        }
    }
}
