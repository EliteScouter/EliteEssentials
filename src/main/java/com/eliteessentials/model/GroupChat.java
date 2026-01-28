package com.eliteessentials.model;

/**
 * Represents a chat channel configuration.
 * 
 * There are two types of chat channels:
 * 1. Group-based: Tied to LuckPerms groups (requiresGroup = true, default)
 *    - Players in the LuckPerms group can use the chat
 * 2. Permission-based: Tied to a permission node (requiresGroup = false)
 *    - Players with eliteessentials.chat.<chatName> can use the chat
 *    - Useful for cross-group chats like "Trade" or "Help"
 */
public class GroupChat {
    
    private String groupName;
    private String displayName;
    private String prefix;
    private String color;
    private boolean enabled;
    
    /**
     * When true (default): Chat requires membership in the LuckPerms group.
     * When false: Chat requires the permission eliteessentials.chat.<groupName>.
     * 
     * Note: Using Boolean (object) instead of boolean (primitive) to detect
     * if the field was present in JSON during deserialization.
     */
    private Boolean requiresGroup;
    
    public GroupChat() {
        this.enabled = true;
        // Don't set requiresGroup here - leave it null so we can detect missing field during migration
    }
    
    public GroupChat(String groupName, String displayName, String prefix, String color) {
        this.groupName = groupName;
        this.displayName = displayName;
        this.prefix = prefix;
        this.color = color;
        this.enabled = true;
        this.requiresGroup = true;
    }
    
    public GroupChat(String groupName, String displayName, String prefix, String color, boolean requiresGroup) {
        this.groupName = groupName;
        this.displayName = displayName;
        this.prefix = prefix;
        this.color = color;
        this.enabled = true;
        this.requiresGroup = requiresGroup;
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
    
    public boolean isRequiresGroup() {
        return requiresGroup == null || requiresGroup;
    }
    
    /**
     * Get the raw requiresGroup value (may be null if not set in JSON).
     * Used for migration detection.
     */
    public Boolean getRequiresGroupRaw() {
        return requiresGroup;
    }
    
    public void setRequiresGroup(boolean requiresGroup) {
        this.requiresGroup = requiresGroup;
    }
    
    /**
     * Check if this is a permission-based chat (not group-based).
     */
    public boolean isPermissionBased() {
        return !requiresGroup;
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
    
    /**
     * Creates a default trade chat (permission-based).
     * Players need eliteessentials.chat.trade to use this.
     */
    public static GroupChat tradeChat() {
        return new GroupChat("trade", "Trade Chat", "[TRADE]", "#f0c674", false);
    }
}
