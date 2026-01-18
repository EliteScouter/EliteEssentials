package com.eliteessentials.listeners;

import com.eliteessentials.config.ConfigManager;
import com.eliteessentials.integration.LuckPermsIntegration;
import com.eliteessentials.permissions.PermissionService;
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
            return;
        }
        
        eventRegistry.registerGlobal(PlayerChatEvent.class, event -> {
            onPlayerChat(event);
        });
    }
    
    /**
     * Handle player chat event.
     */
    private void onPlayerChat(PlayerChatEvent event) {
        PlayerRef sender = event.getSender();
        if (!sender.isValid()) {
            return;
        }
        
        String playerName = sender.getUsername();
        String originalMessage = event.getContent();
        
        // Get the chat format for this player's group
        String format = getChatFormat(sender);
        
        // Create a custom formatter that applies our format
        event.setFormatter((senderRef, message) -> {
            // Replace placeholders
            String formattedMessage = format
                    .replace("{player}", playerName)
                    .replace("{message}", message)
                    .replace("{displayname}", playerName); // For future nickname support
            
            // Apply color codes and formatting
            return MessageFormatter.format(formattedMessage);
        });
        
        if (configManager.isDebugEnabled()) {
            logger.info("Chat format applied for " + playerName + ": " + format);
        }
    }
    
    /**
     * Get the chat format for a player based on their highest priority group.
     */
    private String getChatFormat(PlayerRef playerRef) {
        var config = configManager.getConfig().chatFormat;
        
        String highestPriorityGroup = null;
        int highestPriority = -1;
        
        // Try LuckPerms first if available
        if (LuckPermsIntegration.isAvailable()) {
            // Get all groups the player belongs to
            java.util.List<String> groups = LuckPermsIntegration.getGroups(playerRef.getUuid());
            
            // Find the highest priority group
            for (String group : groups) {
                int priority = config.groupPriorities.getOrDefault(group, 0);
                if (priority > highestPriority && config.groupFormats.containsKey(group)) {
                    highestPriority = priority;
                    highestPriorityGroup = group;
                }
            }
            
            if (highestPriorityGroup != null) {
                return config.groupFormats.get(highestPriorityGroup);
            }
        }
        
        // Fall back to simple permission system
        // Check if player is OP/Admin using PermissionService
        if (PermissionService.get().isAdmin(playerRef.getUuid())) {
            if (config.groupFormats.containsKey("OP")) {
                int priority = config.groupPriorities.getOrDefault("OP", 0);
                if (priority > highestPriority) {
                    highestPriority = priority;
                    highestPriorityGroup = "OP";
                }
            }
            if (config.groupFormats.containsKey("Admin")) {
                int priority = config.groupPriorities.getOrDefault("Admin", 0);
                if (priority > highestPriority) {
                    highestPriority = priority;
                    highestPriorityGroup = "Admin";
                }
            }
        }
        
        if (highestPriorityGroup != null) {
            return config.groupFormats.get(highestPriorityGroup);
        }
        
        // Default format
        return config.groupFormats.getOrDefault("Default", config.defaultFormat);
    }
}

