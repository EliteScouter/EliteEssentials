package com.eliteessentials.model;

import java.util.ArrayList;
import java.util.List;

/**
 * Model for an auto-broadcast entry.
 * Each broadcast has an ID, interval, prefix, and list of messages to cycle through.
 */
public class AutoBroadcast {
    
    private String id;
    private boolean enabled;
    private int intervalSeconds;
    private String prefix;
    private List<String> messages;
    private boolean random;
    private boolean requirePlayers;
    
    public AutoBroadcast() {
        this.id = "default";
        this.enabled = true;
        this.intervalSeconds = 300; // 5 minutes default
        this.prefix = "";
        this.messages = new ArrayList<>();
        this.random = false;
        this.requirePlayers = true;
    }
    
    public AutoBroadcast(String id, int intervalSeconds, List<String> messages) {
        this.id = id;
        this.enabled = true;
        this.intervalSeconds = intervalSeconds;
        this.prefix = "";
        this.messages = messages != null ? new ArrayList<>(messages) : new ArrayList<>();
        this.random = false;
        this.requirePlayers = true;
    }
    
    public String getId() {
        return id;
    }
    
    public void setId(String id) {
        this.id = id;
    }
    
    public boolean isEnabled() {
        return enabled;
    }
    
    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }
    
    public int getIntervalSeconds() {
        return intervalSeconds;
    }
    
    public void setIntervalSeconds(int intervalSeconds) {
        this.intervalSeconds = intervalSeconds;
    }
    
    public List<String> getMessages() {
        return messages;
    }
    
    public void setMessages(List<String> messages) {
        this.messages = messages != null ? new ArrayList<>(messages) : new ArrayList<>();
    }
    
    public boolean isRandom() {
        return random;
    }
    
    public void setRandom(boolean random) {
        this.random = random;
    }
    
    public String getPrefix() {
        return prefix;
    }
    
    public void setPrefix(String prefix) {
        this.prefix = prefix != null ? prefix : "";
    }
    
    public boolean isRequirePlayers() {
        return requirePlayers;
    }
    
    public void setRequirePlayers(boolean requirePlayers) {
        this.requirePlayers = requirePlayers;
    }
}
