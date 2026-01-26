package com.eliteessentials.model;

/**
 * Represents a group chat configuration.
 * Players with the group's permission can send and receive messages in that chat.
 * 
 * Unlike admin chat which is permission-based, group chat is tied to LuckPerms groups.
 * Each LuckPerms group can have its own private chat channel.
 */
public class GroupChat {
    
    private String groupName;
    private String displayName;
    private String prefix;
    private String color;
    private boolean enabled;
    
    public GroupChat() {
        this.enabled = true;
    }
    
    public GroupChat(String groupName, String displayName, String prefix, String color) {
        this.groupName = groupName;
        this.displayName = displayName;
        this.prefix = prefix;
        this.color = color;
        this.enabled = true;
    }
    
    public String getGroupName() {
        return groupName;
    }
    
    public void setGroupName(String groupName) {
        this.groupName = groupName;
    }
    
    public String getDisplayName() {
        return displayName;
    }
    
    public void setDisplayName(String displayName) {
        this.displayName = displayName;
    }
    
    public String getPrefix() {
        return prefix;
    }
    
    public void setPrefix(String prefix) {
        this.prefix = prefix;
    }
    
    public String getColor() {
        return color;
    }
    
    public void setColor(String color) {
        this.color = color;
    }
    
    public boolean isEnabled() {
        return enabled;
    }
    
    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }
    
    /**
     * Creates a default admin group chat.
     */
    public static GroupChat adminGroup() {
        return new GroupChat("admin", "Admin Chat", "[ADMIN]", "#f85149");
    }
    
    /**
     * Creates a default moderator group chat.
     */
    public static GroupChat modGroup() {
        return new GroupChat("moderator", "Mod Chat", "[MOD]", "#58a6ff");
    }
    
    /**
     * Creates a default staff group chat.
     */
    public static GroupChat staffGroup() {
        return new GroupChat("staff", "Staff Chat", "[STAFF]", "#a371f7");
    }
    
    /**
     * Creates a default VIP group chat.
     */
    public static GroupChat vipGroup() {
        return new GroupChat("vip", "VIP Chat", "[VIP]", "#f0883e");
    }
}
