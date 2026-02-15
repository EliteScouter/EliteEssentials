package com.eliteessentials.services;

import com.eliteessentials.config.ConfigManager;
import com.eliteessentials.integration.LuckPermsIntegration;
import com.eliteessentials.integration.PAPIIntegration;
import com.eliteessentials.model.GroupChat;
import com.eliteessentials.permissions.PermissionService;
import com.eliteessentials.permissions.Permissions;
import com.eliteessentials.util.MessageFormatter;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;
import com.hypixel.hytale.math.vector.Vector3d;
import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.Universe;
import com.hypixel.hytale.server.core.universe.world.World;

import java.io.*;
import java.lang.reflect.Type;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Logger;

/**
 * Service for managing group-based and permission-based private chat channels.
 * 
 * Two types of chat channels:
 * 1. Group-based (requiresGroup = true): Players in the LuckPerms group can chat
 * 2. Permission-based (requiresGroup = false): Players with eliteessentials.chat.<name> can chat
 * 
 * Features:
 * - Optional chat formatting (prefixes/colors from chatFormat config)
 * - Admin spy mode to monitor all channels
 * 
 * Usage:
 * - /gc [chat] <message> - Send to a chat channel
 * - /g [chat] <message> - Alias for /gc
 * - /chats - List available chat channels
 * - /gcspy - Toggle spy mode (admin)
 */
public class GroupChatService {
    
    private static final Logger logger = Logger.getLogger("EliteEssentials");
    private static final String GROUP_CHAT_FILE = "groupchat.json";
    private static final Gson gson = new GsonBuilder().setPrettyPrinting().create();
    
    private final File dataFolder;
    private final ConfigManager configManager;
    private final Object fileLock = new Object();
    
    /** Players currently spying on all group chat channels */
    private final Set<UUID> spyingPlayers = ConcurrentHashMap.newKeySet();
    
    private List<GroupChat> groupChats = new ArrayList<>();
    private MuteService muteService;
    private IgnoreService ignoreService;
    
    public GroupChatService(File dataFolder, ConfigManager configManager) {
        this.dataFolder = dataFolder;
        this.configManager = configManager;
        load();
    }
    
    public void setMuteService(MuteService muteService) {
        this.muteService = muteService;
    }
    
    public void setIgnoreService(IgnoreService ignoreService) {
        this.ignoreService = ignoreService;
    }
    
    /**
     * Load group chat configuration from file.
     */
    public void load() {
        File file = new File(dataFolder, GROUP_CHAT_FILE);
        
        if (file.exists()) {
            try (Reader reader = new InputStreamReader(new FileInputStream(file), StandardCharsets.UTF_8)) {
                Type type = new TypeToken<List<GroupChat>>(){}.getType();
                List<GroupChat> loaded = gson.fromJson(reader, type);
                if (loaded != null) {
                    groupChats = loaded;
                    
                    // Migration: ensure all chats have requiresGroup set
                    // Existing configs without this field should default to true (group-based)
                    boolean needsSave = false;
                    for (GroupChat gc : groupChats) {
                        if (gc.getRequiresGroupRaw() == null) {
                            gc.setRequiresGroup(true);
                            needsSave = true;
                        }
                    }
                    
                    if (needsSave) {
                        logger.info("Migrating group chat config - adding requiresGroup field to existing chats.");
                        save();
                    }
                    
                    logger.info("Loaded " + groupChats.size() + " chat channel configurations.");
                }
            } catch (Exception e) {
                logger.severe("Failed to load group chat config: " + e.getMessage());
            }
        } else {
            // Create default configuration
            createDefaultConfig();
            save();
        }
    }
    
    /**
     * Save group chat configuration to file.
     */
    public void save() {
        synchronized (fileLock) {
            File file = new File(dataFolder, GROUP_CHAT_FILE);
            try {
                if (!dataFolder.exists()) {
                    dataFolder.mkdirs();
                }
                try (Writer writer = new OutputStreamWriter(new FileOutputStream(file), StandardCharsets.UTF_8)) {
                    gson.toJson(groupChats, writer);
                }
            } catch (Exception e) {
                logger.severe("Failed to save group chat config: " + e.getMessage());
            }
        }
    }
    
    /**
     * Create default group chat configuration.
     */
    private void createDefaultConfig() {
        groupChats.clear();
        // Group-based chats (require LuckPerms group membership)
        groupChats.add(GroupChat.adminGroup());
        groupChats.add(GroupChat.modGroup());
        groupChats.add(GroupChat.staffGroup());
        groupChats.add(GroupChat.vipGroup());
        // Permission-based chat (requires eliteessentials.chat.trade)
        groupChats.add(GroupChat.tradeChat());
        // Range-limited local chat (requires eliteessentials.chat.local, 50 block range)
        groupChats.add(GroupChat.localChat());
        logger.info("Created default chat channel configuration.");
    }
    
    /**
     * Reload configuration from file.
     */
    public void reload() {
        load();
    }
    
    /**
     * Get all configured group chats.
     */
    public List<GroupChat> getGroupChats() {
        return new ArrayList<>(groupChats);
    }
    
    /**
     * Get a group chat by name (case-insensitive).
     */
    public GroupChat getGroupChat(String chatName) {
        for (GroupChat gc : groupChats) {
            if (gc.getGroupName().equalsIgnoreCase(chatName)) {
                return gc;
            }
        }
        return null;
    }
    
    /**
     * Check if a player has access to a specific chat channel.
     * 
     * @param playerId Player UUID
     * @param chat The chat channel to check
     * @return true if player can use this chat
     */
    public boolean playerHasAccess(UUID playerId, GroupChat chat) {
        if (!chat.isEnabled()) {
            return false;
        }
        
        if (chat.isPermissionBased()) {
            // Permission-based chat: check eliteessentials.chat.<name>
            return com.eliteessentials.permissions.PermissionService.get()
                .hasPermission(playerId, Permissions.chatAccess(chat.getGroupName()));
        } else {
            // Group-based chat: check LuckPerms group membership
            return playerBelongsToGroup(playerId, chat.getGroupName());
        }
    }
    
    /**
     * Get all chats a player has access to.
     * Includes both group-based chats (from LuckPerms groups) and permission-based chats.
     */
    public List<GroupChat> getPlayerGroupChats(UUID playerId) {
        List<GroupChat> result = new ArrayList<>();
        
        for (GroupChat gc : groupChats) {
            if (!gc.isEnabled()) continue;
            
            if (playerHasAccess(playerId, gc)) {
                result.add(gc);
            }
        }
        
        if (configManager.isDebugEnabled()) {
            logger.info("Player " + playerId + " has access to " + result.size() + " chat channels.");
        }
        
        return result;
    }
    
    /**
     * Check if a player belongs to a specific LuckPerms group.
     */
    public boolean playerBelongsToGroup(UUID playerId, String groupName) {
        if (!LuckPermsIntegration.isAvailable()) {
            return false;
        }
        
        List<String> playerGroups = LuckPermsIntegration.getGroups(playerId);
        for (String group : playerGroups) {
            if (group.equalsIgnoreCase(groupName)) {
                return true;
            }
        }
        return false;
    }
    
    /**
     * Broadcast a message to all players who have access to a specific chat.
     * If the chat has a range limit, only players within that range will receive the message.
     * Optionally applies chat formatting (prefixes/colors) from the chatFormat config.
     * Sends spy messages to admins with spy mode enabled who aren't in the channel.
     * 
     * @param groupChat The chat channel configuration
     * @param sender The player sending the message
     * @param message The message content
     */
    public void broadcast(GroupChat groupChat, PlayerRef sender, String message) {
        var gcConfig = configManager.getConfig().groupChat;
        
        // Build the channel color code
        String colorCode = groupChat.getColor();
        if (colorCode != null && colorCode.startsWith("#") && !colorCode.startsWith("&#")) {
            colorCode = "&" + colorCode;
        }
        
        // Build the formatted message based on whether chat formatting is enabled
        String format;
        if (gcConfig.useChatFormatting) {
            // Get the player's chat format from chatFormat config (same as regular chat)
            String chatFormat = getChatFormatForPlayer(sender);
            
            // Replace player placeholders in the chat format
            String playerFormatted = chatFormat
                    .replace("{player}", sender.getUsername())
                    .replace("{displayname}", sender.getUsername());
            
            // Replace LuckPerms placeholders if available
            playerFormatted = replaceLuckPermsPlaceholders(sender, playerFormatted);
            
            // Replace PAPI placeholders if available
            boolean isPapiAvailable = PAPIIntegration.available() && configManager.getConfig().chatFormat.placeholderapi;
            if (isPapiAvailable) {
                playerFormatted = PAPIIntegration.setPlaceholders(sender, playerFormatted);
            }
            
            // Replace {message} in the chat format with the actual message
            playerFormatted = playerFormatted.replace("{message}", message);
            
            // Build the final format using the group chat formatted message format
            format = gcConfig.formattedMessageFormat
                    .replace("{channel_prefix}", groupChat.getPrefix())
                    .replace("{channel_color}", colorCode != null ? colorCode : "")
                    .replace("{chat_format}", playerFormatted)
                    .replace("{player}", sender.getUsername())
                    .replace("{message}", message);
        } else {
            // Plain format: [PREFIX] PlayerName: message
            format = colorCode + groupChat.getPrefix() + " &f" + 
                           sender.getUsername() + "&7: &r" + message;
        }
        
        Message formattedMessage = MessageFormatter.format(format);
        
        // Build spy message for admins not in the channel
        Message spyMessage = null;
        if (gcConfig.allowSpy && !spyingPlayers.isEmpty()) {
            String spyFormat = gcConfig.spyFormat
                    .replace("{channel}", groupChat.getGroupName())
                    .replace("{player}", sender.getUsername())
                    .replace("{message}", message);
            spyMessage = MessageFormatter.format(spyFormat);
        }
        
        // Get sender position if range-limited
        Vector3d senderPos = null;
        String senderWorldName = null;
        if (groupChat.hasRangeLimit()) {
            try {
                senderPos = sender.getTransform().getPosition();
            } catch (Exception e) {
                if (configManager.isDebugEnabled()) {
                    logger.info("Failed to get sender position: " + e.getMessage());
                }
            }
        }
        
        // Find all players with access to this chat + spy targets
        List<PlayerRef> recipients = new ArrayList<>();
        List<PlayerRef> spyRecipients = new ArrayList<>();
        Universe universe = Universe.get();
        
        if (universe != null) {
            for (Map.Entry<String, World> entry : universe.getWorlds().entrySet()) {
                World world = entry.getValue();
                String worldName = world.getName();
                Collection<PlayerRef> players = world.getPlayerRefs();
                
                // Track sender's world when we find them
                if (groupChat.hasRangeLimit() && senderWorldName == null && players != null) {
                    for (PlayerRef p : players) {
                        if (p != null && p.getUuid().equals(sender.getUuid())) {
                            senderWorldName = worldName;
                            break;
                        }
                    }
                }
                
                if (players != null) {
                    for (PlayerRef player : players) {
                        if (player != null && player.isValid()) {
                            boolean hasAccess = playerHasAccess(player.getUuid(), groupChat);
                            
                            if (hasAccess) {
                                // Check range if applicable
                                if (groupChat.hasRangeLimit() && senderPos != null && senderWorldName != null) {
                                    if (!isPlayerInRange(player, worldName, senderWorldName, senderPos, groupChat.getRange())) {
                                        continue;
                                    }
                                }
                                // Skip recipients who are ignoring the sender
                                if (ignoreService != null && ignoreService.isIgnoring(player.getUuid(), sender.getUuid())) {
                                    continue;
                                }
                                recipients.add(player);
                            } else if (spyMessage != null && isSpying(player.getUuid())) {
                                // Player doesn't have access but is spying
                                spyRecipients.add(player);
                            }
                        }
                    }
                }
            }
        }
        
        // Send to all recipients
        for (PlayerRef recipient : recipients) {
            recipient.sendMessage(formattedMessage);
        }
        
        // Send spy messages
        if (spyMessage != null) {
            for (PlayerRef spy : spyRecipients) {
                spy.sendMessage(spyMessage);
            }
        }
        
        if (configManager.isDebugEnabled()) {
            String rangeInfo = groupChat.hasRangeLimit() ? " (range: " + groupChat.getRange() + " blocks)" : "";
            String spyInfo = spyRecipients.isEmpty() ? "" : " (+" + spyRecipients.size() + " spies)";
            logger.info("Chat [" + groupChat.getGroupName() + "]" + rangeInfo + " from " + 
                       sender.getUsername() + " sent to " + recipients.size() + " players" + spyInfo + ".");
        }
    }
    
    /**
     * Check if a player is within range of the sender.
     */
    private boolean isPlayerInRange(PlayerRef player, String playerWorldName, String senderWorldName, 
                                    Vector3d senderPos, int range) {
        // Must be in same world
        if (!playerWorldName.equals(senderWorldName)) {
            return false;
        }
        
        try {
            Vector3d playerPos = player.getTransform().getPosition();
            double distance = calculateDistance(senderPos, playerPos);
            return distance <= range;
        } catch (Exception e) {
            return false;
        }
    }
    
    /**
     * Calculate 3D distance between two positions.
     */
    private double calculateDistance(Vector3d pos1, Vector3d pos2) {
        double dx = pos1.x - pos2.x;
        double dy = pos1.y - pos2.y;
        double dz = pos1.z - pos2.z;
        return Math.sqrt(dx * dx + dy * dy + dz * dz);
    }
    
    /**
     * Broadcast a message using the player's first available chat.
     * 
     * @param sender The player sending the message
     * @param message The message content
     * @return true if message was sent, false if player has no chat access
     */
    public boolean broadcast(PlayerRef sender, String message) {
        List<GroupChat> playerChats = getPlayerGroupChats(sender.getUuid());
        if (playerChats.isEmpty()) {
            return false;
        }
        
        broadcast(playerChats.get(0), sender, message);
        return true;
    }
    
    /**
     * Add a new group chat configuration.
     */
    public void addGroupChat(GroupChat groupChat) {
        // Remove existing with same name
        groupChats.removeIf(gc -> gc.getGroupName().equalsIgnoreCase(groupChat.getGroupName()));
        groupChats.add(groupChat);
        save();
    }
    
    /**
     * Remove a group chat configuration.
     */
    public boolean removeGroupChat(String groupName) {
        boolean removed = groupChats.removeIf(gc -> gc.getGroupName().equalsIgnoreCase(groupName));
        if (removed) {
            save();
        }
        return removed;
    }
    
    // ==================== SPY MODE ====================
    
    /**
     * Toggle spy mode for a player.
     * @return true if spy is now enabled, false if disabled
     */
    public boolean toggleSpy(UUID playerId) {
        if (spyingPlayers.contains(playerId)) {
            spyingPlayers.remove(playerId);
            return false;
        } else {
            spyingPlayers.add(playerId);
            return true;
        }
    }
    
    /**
     * Check if a player is currently spying on group chats.
     */
    public boolean isSpying(UUID playerId) {
        return spyingPlayers.contains(playerId);
    }
    
    /**
     * Remove a player from spy mode (e.g., on disconnect).
     */
    public void removeSpy(UUID playerId) {
        spyingPlayers.remove(playerId);
    }
    
    // ==================== CHAT FORMAT HELPERS ====================
    
    /**
     * Get the chat format string for a player based on their highest priority group.
     * Mirrors the logic from ChatListener.getChatFormat() so group chat can reuse
     * the same prefix/color formatting as regular chat.
     */
    private String getChatFormatForPlayer(PlayerRef playerRef) {
        var config = configManager.getConfig().chatFormat;
        
        String highestPriorityGroup = null;
        int highestPriority = -1;
        
        // Try LuckPerms first if available
        if (LuckPermsIntegration.isAvailable()) {
            List<String> groups = LuckPermsIntegration.getGroups(playerRef.getUuid());
            
            for (String group : groups) {
                String matchedConfigKey = findConfigKeyIgnoreCase(config.groupFormats, group);
                
                if (matchedConfigKey != null) {
                    int priority = getGroupPriorityIgnoreCase(config.groupPriorities, group, matchedConfigKey);
                    if (priority > highestPriority) {
                        highestPriority = priority;
                        highestPriorityGroup = matchedConfigKey;
                    }
                }
            }
            
            if (highestPriorityGroup != null) {
                return config.groupFormats.get(highestPriorityGroup);
            }
        }
        
        // Fall back to simple permission system
        if (PermissionService.get().isAdmin(playerRef.getUuid())) {
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
        
        // Default format
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
    private String findConfigKeyIgnoreCase(Map<String, String> map, String group) {
        if (map.containsKey(group)) {
            return group;
        }
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
    private int getGroupPriorityIgnoreCase(Map<String, Integer> priorities, String group, String matchedConfigKey) {
        if (priorities.containsKey(matchedConfigKey)) {
            return priorities.get(matchedConfigKey);
        }
        if (priorities.containsKey(group)) {
            return priorities.get(group);
        }
        for (Map.Entry<String, Integer> entry : priorities.entrySet()) {
            if (entry.getKey().equalsIgnoreCase(group)) {
                return entry.getValue();
            }
        }
        return 0;
    }
    
    /**
     * Replace LuckPerms placeholders in the format string.
     * Supports {prefix}, {suffix}, {group} and their %luckperms_*% equivalents.
     */
    private String replaceLuckPermsPlaceholders(PlayerRef player, String format) {
        if (!LuckPermsIntegration.isAvailable()) {
            return format
                    .replace("%luckperms_prefix%", "")
                    .replace("%luckperms_suffix%", "")
                    .replace("%luckperms_primary_group%", "")
                    .replace("{prefix}", "")
                    .replace("{suffix}", "")
                    .replace("{group}", "");
        }
        
        UUID playerId = player.getUuid();
        
        boolean hasPrefix = format.contains("%luckperms_prefix%") || format.contains("{prefix}");
        boolean hasSuffix = format.contains("%luckperms_suffix%") || format.contains("{suffix}");
        boolean hasGroup = format.contains("%luckperms_primary_group%") || format.contains("{group}");
        
        if (hasPrefix) {
            String prefix = LuckPermsIntegration.getPrefix(playerId);
            format = format
                    .replace("%luckperms_prefix%", prefix)
                    .replace("{prefix}", prefix);
        }
        
        if (hasSuffix) {
            String suffix = LuckPermsIntegration.getSuffix(playerId);
            format = format
                    .replace("%luckperms_suffix%", suffix)
                    .replace("{suffix}", suffix);
        }
        
        if (hasGroup) {
            String group = LuckPermsIntegration.getPrimaryGroupDisplay(playerId);
            format = format
                    .replace("%luckperms_primary_group%", group)
                    .replace("{group}", group);
        }
        
        return format;
    }


    /**
     * Get the default chat for a player, or their first available chat if no default is set.
     *
     * @param playerId Player UUID
     * @param playerService PlayerService to retrieve default chat preference
     * @return The default chat, or null if player has no chat access
     */
    public GroupChat getDefaultChat(UUID playerId, PlayerService playerService) {
        List<GroupChat> playerChats = getPlayerGroupChats(playerId);
        if (playerChats.isEmpty()) {
            return null;
        }

        // Check if player has a default chat set
        String defaultChatName = playerService.getDefaultGroupChat(playerId);
        if (defaultChatName != null) {
            // Find the chat and verify player still has access
            for (GroupChat chat : playerChats) {
                if (chat.getGroupName().equalsIgnoreCase(defaultChatName)) {
                    return chat;
                }
            }
        }

        // No default set or player lost access - return first available
        return playerChats.get(0);
    }

}
