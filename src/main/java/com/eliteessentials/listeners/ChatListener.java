package com.eliteessentials.listeners;

import com.eliteessentials.config.ConfigManager;
import com.eliteessentials.integration.LuckPermsIntegration;
import com.eliteessentials.integration.PAPIIntegration;
import com.eliteessentials.permissions.PermissionService;
import com.eliteessentials.permissions.Permissions;
import com.eliteessentials.util.MessageFormatter;
import com.hypixel.hytale.event.EventRegistry;
import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.event.events.player.PlayerChatEvent;
import com.hypixel.hytale.server.core.universe.PlayerRef;

import java.util.logging.Logger;

/**
 * Listener for chat events to apply group-based formatting.
 * 
 * Supports both simple permission groups and LuckPerms groups.
 * Chat format is determined by the highest priority group the player belongs to.
 */
public class ChatListener {
    
    private static final Logger logger = Logger.getLogger("EliteEssentials");
    private final ConfigManager configManager;
    
    public ChatListener(ConfigManager configManager) {
        this.configManager = configManager;
    }
    
    /**
     * Register event listeners.
     */
    public void registerEvents(EventRegistry eventRegistry) {
        if (!configManager.getConfig().chatFormat.enabled) {
            logger.info("Chat formatting is disabled in config");
            return;
        }
        
        eventRegistry.registerGlobal(PlayerChatEvent.class, event -> {
            onPlayerChat(event);
        });
        
        logger.info("Chat formatting listener registered successfully");
        
        if (LuckPermsIntegration.isAvailable()) {
            logger.warning("=".repeat(60));
            logger.warning("LuckPerms detected! If chat formatting doesn't work:");
            logger.warning("1. LuckPerms may have its own chat formatting enabled");
            logger.warning("2. Check LuckPerms config and disable chat formatting there");
            logger.warning("3. Or set 'advancedPermissions: false' to use simple mode");
            logger.warning("=".repeat(60));
        }
    }
    
    /**
     * Handle player chat event.
     */
    private void onPlayerChat(PlayerChatEvent event) {
        PlayerRef sender = event.getSender();
        if (!sender.isValid()) {
            logger.warning("Chat event received but sender is invalid");
            return;
        }
        
        String playerName = sender.getUsername();
        String originalMessage = event.getContent();
        
        if (configManager.isDebugEnabled()) {
            logger.info("Processing chat from " + playerName);
            logger.info("LuckPerms available: " + LuckPermsIntegration.isAvailable());
        }
        
        // Process player message - strip color/format codes if they don't have permission
        String processedMessage = processPlayerMessage(sender, originalMessage);
        
        // Get the chat format for this player's group
        String format = getChatFormat(sender);
        
        if (configManager.isDebugEnabled()) {
            logger.info("Selected format for " + playerName + ": " + format);
        }
        
        // Cancel the event to prevent default/LuckPerms formatting
        event.setCancelled(true);
        
        // Replace placeholders
        String formattedMessage = format
                .replace("{player}", playerName)
                .replace("{message}", processedMessage)
                .replace("{displayname}", playerName);

        if (PAPIIntegration.available() && configManager.getConfig().chatFormat.placeholderapi && formattedMessage.indexOf('%') != -1) {
            formattedMessage = PAPIIntegration.setPlaceholders(sender, formattedMessage);
        }
        
        if (configManager.isDebugEnabled()) {
            logger.info("Formatted message: " + formattedMessage);
        }
        
        // Broadcast the formatted message to all players
        Message message = MessageFormatter.format(formattedMessage);
        for (PlayerRef player : com.hypixel.hytale.server.core.universe.Universe.get().getPlayers()) {
            player.sendMessage(message);
        }
        
        if (configManager.isDebugEnabled()) {
            logger.info("Message broadcasted to all players");
        }
    }
    
    /**
     * Process player message, stripping color/format codes if they don't have permission.
     * 
     * @param sender The player sending the message
     * @param message The original message
     * @return The processed message with unauthorized codes stripped
     */
    private String processPlayerMessage(PlayerRef sender, String message) {
        var config = configManager.getConfig().chatFormat;
        
        // Check if player can use colors
        boolean canUseColors = config.allowPlayerColors || hasColorPermission(sender);
        
        // Check if player can use formatting
        boolean canUseFormatting = config.allowPlayerFormatting || hasFormatPermission(sender);
        
        if (configManager.isDebugEnabled()) {
            logger.info("Player " + sender.getUsername() + " - canUseColors: " + canUseColors + ", canUseFormatting: " + canUseFormatting);
        }
        
        // If player has both permissions (or they're allowed), return original
        if (canUseColors && canUseFormatting) {
            return message;
        }
        
        // If player has neither permission, strip everything
        if (!canUseColors && !canUseFormatting) {
            return MessageFormatter.toRawString(message);
        }
        
        // Strip only what they don't have permission for
        if (!canUseColors) {
            message = MessageFormatter.stripColors(message);
        }
        if (!canUseFormatting) {
            message = MessageFormatter.stripFormatting(message);
        }
        
        return message;
    }
    
    /**
     * Check if player has permission to use color codes in chat.
     */
    private boolean hasColorPermission(PlayerRef player) {
        // In simple mode, only OPs can use colors
        if (!configManager.getConfig().advancedPermissions) {
            return PermissionService.get().isAdmin(player.getUuid());
        }
        // In advanced mode, check specific permission
        return PermissionService.get().hasPermission(player.getUuid(), Permissions.CHAT_COLOR);
    }
    
    /**
     * Check if player has permission to use formatting codes in chat.
     */
    private boolean hasFormatPermission(PlayerRef player) {
        // In simple mode, only OPs can use formatting
        if (!configManager.getConfig().advancedPermissions) {
            return PermissionService.get().isAdmin(player.getUuid());
        }
        // In advanced mode, check specific permission
        return PermissionService.get().hasPermission(player.getUuid(), Permissions.CHAT_FORMAT);
    }
    
    /**
     * Get the chat format for a player based on their highest priority group.
     * Uses traditional loops instead of streams for better performance in this hot path.
     * Supports case-insensitive group name matching.
     */
    private String getChatFormat(PlayerRef playerRef) {
        var config = configManager.getConfig().chatFormat;
        
        String highestPriorityGroup = null;
        int highestPriority = -1;
        
        // Try LuckPerms first if available
        if (LuckPermsIntegration.isAvailable()) {
            // Get all groups the player belongs to
            java.util.List<String> groups = LuckPermsIntegration.getGroups(playerRef.getUuid());
            
            if (configManager.isDebugEnabled()) {
                logger.info("Player " + playerRef.getUsername() + " has groups: " + groups);
            }
            
            // Find the highest priority group using case-insensitive matching
            for (String group : groups) {
                // Find matching config key (case-insensitive)
                String matchedConfigKey = findConfigKeyIgnoreCase(config.groupFormats, group);
                
                if (matchedConfigKey != null) {
                    int priority = getGroupPriorityIgnoreCase(config.groupPriorities, group, matchedConfigKey);
                    if (configManager.isDebugEnabled()) {
                        logger.info("  Group '" + group + "' matched config key '" + matchedConfigKey + "' with priority: " + priority);
                    }
                    if (priority > highestPriority) {
                        highestPriority = priority;
                        highestPriorityGroup = matchedConfigKey;
                    }
                } else if (configManager.isDebugEnabled()) {
                    logger.info("  Group '" + group + "' has no matching format in config");
                }
            }
            
            if (highestPriorityGroup != null) {
                if (configManager.isDebugEnabled()) {
                    logger.info("Selected group '" + highestPriorityGroup + "' with priority " + highestPriority);
                }
                return config.groupFormats.get(highestPriorityGroup);
            }
        }
        
        // Fall back to simple permission system
        // Check if player is OP/Admin using PermissionService
        if (PermissionService.get().isAdmin(playerRef.getUuid())) {
            // Check all configured groups for admin using traditional loop
            for (String groupName : config.groupFormats.keySet()) {
                int priority = config.groupPriorities.getOrDefault(groupName, 0);
                if (priority > highestPriority) {
                    highestPriority = priority;
                    highestPriorityGroup = groupName;
                }
            }
        }
        
        if (highestPriorityGroup != null) {
            return config.groupFormats.get(highestPriorityGroup);
        }
        
        // Default format - try "default" first (lowercase), then "Default" (capitalized)
        String defaultFormat = config.groupFormats.get("default");
        if (defaultFormat != null) {
            return defaultFormat;
        }
        defaultFormat = config.groupFormats.get("Default");
        return defaultFormat != null ? defaultFormat : config.defaultFormat;
    }
    
    /**
     * Find a config key that matches the group name (case-insensitive).
     */
    private String findConfigKeyIgnoreCase(java.util.Map<String, String> map, String group) {
        // Try exact match first
        if (map.containsKey(group)) {
            return group;
        }
        // Try case-insensitive match
        for (String key : map.keySet()) {
            if (key.equalsIgnoreCase(group)) {
                return key;
            }
        }
        return null;
    }
    
    /**
     * Get group priority with case-insensitive fallback.
     */
    private int getGroupPriorityIgnoreCase(java.util.Map<String, Integer> priorities, String group, String matchedConfigKey) {
        // Try the matched config key first
        if (priorities.containsKey(matchedConfigKey)) {
            return priorities.get(matchedConfigKey);
        }
        // Try the original group name
        if (priorities.containsKey(group)) {
            return priorities.get(group);
        }
        // Try case-insensitive match
        for (java.util.Map.Entry<String, Integer> entry : priorities.entrySet()) {
            if (entry.getKey().equalsIgnoreCase(group)) {
                return entry.getValue();
            }
        }
        return 0;
    }
}

