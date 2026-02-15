package com.eliteessentials.model;

import java.util.UUID;

/**
 * Represents cached player data for tracking player information.
 * Stored in players.json keyed by player UUID.
 */
public class PlayerData {
    
    private UUID uuid;
    private String name;
    private long firstJoin;
    private long lastSeen;
    private double wallet;
    private long playTime;  // Total play time in seconds
    private String lastKnownIp;  // For admin reference (optional)
    private String defaultGroupChat;  // Default group chat for /gc command
    
    public PlayerData() {
        // Default constructor for Gson
    }
    
    public PlayerData(UUID uuid, String name) {
        this.uuid = uuid;
        this.name = name;
        this.firstJoin = System.currentTimeMillis();
        this.lastSeen = System.currentTimeMillis();
        this.wallet = 0.0;
        this.playTime = 0;
        this.lastKnownIp = null;
    }
    
    // Getters and setters
    
    public UUID getUuid() {
        return uuid;
    }
    
    public void setUuid(UUID uuid) {
        this.uuid = uuid;
    }
    
    public String getName() {
        return name;
    }
    
    public void setName(String name) {
        this.name = name;
    }
    
    public long getFirstJoin() {
        return firstJoin;
    }
    
    public void setFirstJoin(long firstJoin) {
        this.firstJoin = firstJoin;
    }
    
    public long getLastSeen() {
        return lastSeen;
    }
    
    public void setLastSeen(long lastSeen) {
        this.lastSeen = lastSeen;
    }
    
    public double getWallet() {
        return wallet;
    }
    
    public void setWallet(double wallet) {
        this.wallet = wallet;
    }
    
    public long getPlayTime() {
        return playTime;
    }
    
    public void setPlayTime(long playTime) {
        this.playTime = playTime;
    }
    
    public String getLastKnownIp() {
        return lastKnownIp;
    }
    
    public void setLastKnownIp(String lastKnownIp) {
        this.lastKnownIp = lastKnownIp;
    }
    
    /**
     * Add time to total play time.
     */
    public void addPlayTime(long seconds) {
        this.playTime += seconds;
    }
    
    /**
     * Add or remove money from wallet.
     */
    public boolean modifyWallet(double amount) {
        double newBalance = this.wallet + amount;
        if (newBalance < 0) {
            return false;  // Insufficient funds
        }
        this.wallet = newBalance;
        return true;
    }
    
    /**
     * Update last seen timestamp to now.
     */
    public void updateLastSeen() {
        this.lastSeen = System.currentTimeMillis();
    }
    
    public String getDefaultGroupChat() {
        return defaultGroupChat;
    }
    
    public void setDefaultGroupChat(String defaultGroupChat) {
        this.defaultGroupChat = defaultGroupChat;
    }
}
