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
                .replace("{message}", originalMessage)
                .replace("{displayname}", playerName);
        
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
     * Get the chat format for a player based on their highest priority group.
     * Uses traditional loops instead of streams for better performance in this hot path.
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
            
            // Find the highest priority group using traditional loop
            for (String group : groups) {
                int priority = config.groupPriorities.getOrDefault(group, 0);
                if (configManager.isDebugEnabled()) {
                    logger.info("  Group '" + group + "' priority: " + priority + 
                               " (has format: " + config.groupFormats.containsKey(group) + ")");
                }
                if (priority > highestPriority && config.groupFormats.containsKey(group)) {
                    highestPriority = priority;
                    highestPriorityGroup = group;
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
}

