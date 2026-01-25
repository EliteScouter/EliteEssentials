package com.eliteessentials.model;

import java.util.ArrayList;
import java.util.List;

/**
 * Represents a playtime reward configuration.
 * Can be either a milestone (one-time) or repeatable reward.
 */
public class PlayTimeReward {
    
    /** Unique identifier for this reward */
    private String id;
    
    /** Display name for the reward */
    private String name;
    
    /** 
     * Minutes of playtime required.
     * For milestones: total playtime needed (e.g., 6000 = 100 hours)
     * For repeatable: interval between rewards (e.g., 60 = every hour)
     */
    private int minutesRequired;
    
    /**
     * Whether this reward repeats.
     * false = milestone (one-time reward at X total playtime)
     * true = repeatable (reward every X minutes of playtime)
     */
    private boolean repeatable;
    
    /** Message to send to the player when they receive this reward */
    private String message;
    
    /** Commands to execute when reward is granted. Supports {player} placeholder. */
    private List<String> commands = new ArrayList<>();
    
    /** Whether this reward is enabled */
    private boolean enabled = true;
    
    public PlayTimeReward() {
        // Default constructor for Gson
    }
    
    public PlayTimeReward(String id, String name, int minutesRequired, boolean repeatable) {
        this.id = id;
        this.name = name;
        this.minutesRequired = minutesRequired;
        this.repeatable = repeatable;
    }
    
    // Getters and setters
    
    public String getId() {
        return id;
    }
    
    public void setId(String id) {
        this.id = id;
    }
    
    public String getName() {
        return name;
    }
    
    public void setName(String name) {
        this.name = name;
    }
    
    public int getMinutesRequired() {
        return minutesRequired;
    }
    
    public void setMinutesRequired(int minutesRequired) {
        this.minutesRequired = minutesRequired;
    }
    
    public boolean isRepeatable() {
        return repeatable;
    }
    
    public void setRepeatable(boolean repeatable) {
        this.repeatable = repeatable;
    }
    
    public String getMessage() {
        return message;
    }
    
    public void setMessage(String message) {
        this.message = message;
    }
    
    public List<String> getCommands() {
        return commands;
    }
    
    public void setCommands(List<String> commands) {
        this.commands = commands;
    }
    
    public boolean isEnabled() {
        return enabled;
    }
    
    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }
    
    /**
     * Format minutes as human-readable time.
     */
    public String getFormattedTime() {
        if (minutesRequired < 60) {
            return minutesRequired + " minute" + (minutesRequired != 1 ? "s" : "");
        }
        int hours = minutesRequired / 60;
        int mins = minutesRequired % 60;
        if (mins == 0) {
            return hours + " hour" + (hours != 1 ? "s" : "");
        }
        return hours + "h " + mins + "m";
    }
}
