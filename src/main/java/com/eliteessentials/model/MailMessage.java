package com.eliteessentials.model;

import java.util.UUID;

/**
 * Represents a mail message sent between players.
 */
public class MailMessage {
    
    /** Unique ID for this message */
    private String id;
    
    /** UUID of the sender */
    private UUID senderUuid;
    
    /** Name of the sender (cached for offline display) */
    private String senderName;
    
    /** The message content */
    private String message;
    
    /** Timestamp when the message was sent */
    private long timestamp;
    
    /** Whether the message has been read */
    private boolean read;
    
    public MailMessage() {
        // For Gson deserialization
    }
    
    public MailMessage(UUID senderUuid, String senderName, String message) {
        this.id = UUID.randomUUID().toString().substring(0, 8);
        this.senderUuid = senderUuid;
        this.senderName = senderName;
        this.message = message;
        this.timestamp = System.currentTimeMillis();
        this.read = false;
    }
    
    public String getId() {
        return id;
    }
    
    public void setId(String id) {
        this.id = id;
    }
    
    public UUID getSenderUuid() {
        return senderUuid;
    }
    
    public void setSenderUuid(UUID senderUuid) {
        this.senderUuid = senderUuid;
    }
    
    public String getSenderName() {
        return senderName;
    }
    
    public void setSenderName(String senderName) {
        this.senderName = senderName;
    }
    
    public String getMessage() {
        return message;
    }
    
    public void setMessage(String message) {
        this.message = message;
    }
    
    public long getTimestamp() {
        return timestamp;
    }
    
    public void setTimestamp(long timestamp) {
        this.timestamp = timestamp;
    }
    
    public boolean isRead() {
        return read;
    }
    
    public void setRead(boolean read) {
        this.read = read;
    }
    
    /**
     * Mark this message as read.
     */
    public void markAsRead() {
        this.read = true;
    }
}
