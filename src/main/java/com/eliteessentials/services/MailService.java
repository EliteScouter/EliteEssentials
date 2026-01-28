package com.eliteessentials.services;

import com.eliteessentials.config.ConfigManager;
import com.eliteessentials.model.MailMessage;
import com.eliteessentials.model.PlayerFile;
import com.eliteessentials.storage.PlayerFileStorage;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Logger;

/**
 * Service for managing player mail.
 * 
 * Features:
 * - Send mail to offline/online players
 * - Read/list mail
 * - Mark as read
 * - Clear mail
 * - Spam protection (cooldown between sends to same player)
 */
public class MailService {
    
    private static final Logger logger = Logger.getLogger("EliteEssentials");
    
    private final PlayerFileStorage playerFileStorage;
    private final ConfigManager configManager;
    
    // Spam protection: sender UUID -> (recipient UUID -> last send timestamp)
    private final Map<UUID, Map<UUID, Long>> sendCooldowns = new ConcurrentHashMap<>();
    
    public MailService(PlayerFileStorage playerFileStorage, ConfigManager configManager) {
        this.playerFileStorage = playerFileStorage;
        this.configManager = configManager;
    }
    
    /**
     * Send mail to a player.
     * 
     * @param senderUuid Sender's UUID
     * @param senderName Sender's name
     * @param recipientUuid Recipient's UUID
     * @param message The message content
     * @return Result of the send operation
     */
    public SendResult sendMail(UUID senderUuid, String senderName, UUID recipientUuid, String message) {
        // Check spam cooldown
        if (isOnCooldown(senderUuid, recipientUuid)) {
            long remaining = getRemainingCooldown(senderUuid, recipientUuid);
            return new SendResult(false, "cooldown", remaining);
        }
        
        // Get recipient's player file
        PlayerFile recipientFile = playerFileStorage.getPlayer(recipientUuid);
        if (recipientFile == null) {
            return new SendResult(false, "playerNotFound", 0);
        }
        
        // Check mailbox limit
        int maxMail = configManager.getConfig().mail.maxMailPerPlayer;
        List<MailMessage> mailbox = recipientFile.getMailbox();
        if (mailbox.size() >= maxMail) {
            return new SendResult(false, "mailboxFull", 0);
        }
        
        // Create and add the mail
        MailMessage mail = new MailMessage(senderUuid, senderName, message);
        recipientFile.addMail(mail);
        
        // Save the recipient's file
        playerFileStorage.saveAndMarkDirty(recipientUuid);
        
        // Record cooldown
        recordSend(senderUuid, recipientUuid);
        
        if (configManager.isDebugEnabled()) {
            logger.info("[Mail] " + senderName + " sent mail to " + recipientFile.getName() + ": " + message);
        }
        
        return new SendResult(true, "success", 0);
    }
    
    /**
     * Get all mail for a player.
     */
    public List<MailMessage> getMail(UUID playerId) {
        PlayerFile playerFile = playerFileStorage.getPlayer(playerId);
        if (playerFile == null) {
            return Collections.emptyList();
        }
        return playerFile.getMailbox();
    }
    
    /**
     * Get unread mail count for a player.
     */
    public int getUnreadCount(UUID playerId) {
        PlayerFile playerFile = playerFileStorage.getPlayer(playerId);
        if (playerFile == null) {
            return 0;
        }
        return (int) playerFile.getMailbox().stream().filter(m -> !m.isRead()).count();
    }
    
    /**
     * Mark a specific mail as read.
     */
    public boolean markAsRead(UUID playerId, String mailId) {
        PlayerFile playerFile = playerFileStorage.getPlayer(playerId);
        if (playerFile == null) {
            return false;
        }
        
        for (MailMessage mail : playerFile.getMailbox()) {
            if (mail.getId().equals(mailId)) {
                mail.markAsRead();
                playerFileStorage.saveAndMarkDirty(playerId);
                return true;
            }
        }
        return false;
    }
    
    /**
     * Mark all mail as read for a player.
     */
    public int markAllAsRead(UUID playerId) {
        PlayerFile playerFile = playerFileStorage.getPlayer(playerId);
        if (playerFile == null) {
            return 0;
        }
        
        int count = 0;
        for (MailMessage mail : playerFile.getMailbox()) {
            if (!mail.isRead()) {
                mail.markAsRead();
                count++;
            }
        }
        
        if (count > 0) {
            playerFileStorage.saveAndMarkDirty(playerId);
        }
        return count;
    }
    
    /**
     * Clear all mail for a player.
     */
    public int clearMail(UUID playerId) {
        PlayerFile playerFile = playerFileStorage.getPlayer(playerId);
        if (playerFile == null) {
            return 0;
        }
        
        int count = playerFile.getMailbox().size();
        playerFile.clearMailbox();
        playerFileStorage.saveAndMarkDirty(playerId);
        return count;
    }
    
    /**
     * Clear only read mail for a player.
     */
    public int clearReadMail(UUID playerId) {
        PlayerFile playerFile = playerFileStorage.getPlayer(playerId);
        if (playerFile == null) {
            return 0;
        }
        
        List<MailMessage> mailbox = playerFile.getMailbox();
        int before = mailbox.size();
        mailbox.removeIf(MailMessage::isRead);
        int removed = before - mailbox.size();
        
        if (removed > 0) {
            playerFileStorage.saveAndMarkDirty(playerId);
        }
        return removed;
    }
    
    /**
     * Delete a specific mail message.
     */
    public boolean deleteMail(UUID playerId, String mailId) {
        PlayerFile playerFile = playerFileStorage.getPlayer(playerId);
        if (playerFile == null) {
            return false;
        }
        
        boolean removed = playerFile.getMailbox().removeIf(m -> m.getId().equals(mailId));
        if (removed) {
            playerFileStorage.saveAndMarkDirty(playerId);
        }
        return removed;
    }
    
    // ==================== Spam Protection ====================
    
    /**
     * Check if sender is on cooldown for sending to recipient.
     */
    private boolean isOnCooldown(UUID sender, UUID recipient) {
        int cooldownSeconds = configManager.getConfig().mail.sendCooldownSeconds;
        if (cooldownSeconds <= 0) {
            return false;
        }
        
        Map<UUID, Long> senderCooldowns = sendCooldowns.get(sender);
        if (senderCooldowns == null) {
            return false;
        }
        
        Long lastSend = senderCooldowns.get(recipient);
        if (lastSend == null) {
            return false;
        }
        
        long elapsed = System.currentTimeMillis() - lastSend;
        return elapsed < (cooldownSeconds * 1000L);
    }
    
    /**
     * Get remaining cooldown in seconds.
     */
    private long getRemainingCooldown(UUID sender, UUID recipient) {
        int cooldownSeconds = configManager.getConfig().mail.sendCooldownSeconds;
        Map<UUID, Long> senderCooldowns = sendCooldowns.get(sender);
        if (senderCooldowns == null) {
            return 0;
        }
        
        Long lastSend = senderCooldowns.get(recipient);
        if (lastSend == null) {
            return 0;
        }
        
        long elapsed = System.currentTimeMillis() - lastSend;
        long remaining = (cooldownSeconds * 1000L) - elapsed;
        return Math.max(0, remaining / 1000);
    }
    
    /**
     * Record a send for cooldown tracking.
     */
    private void recordSend(UUID sender, UUID recipient) {
        sendCooldowns.computeIfAbsent(sender, k -> new ConcurrentHashMap<>())
                .put(recipient, System.currentTimeMillis());
    }
    
    /**
     * Result of a send operation.
     */
    public static class SendResult {
        public final boolean success;
        public final String reason;
        public final long cooldownRemaining;
        
        public SendResult(boolean success, String reason, long cooldownRemaining) {
            this.success = success;
            this.reason = reason;
            this.cooldownRemaining = cooldownRemaining;
        }
    }
}
