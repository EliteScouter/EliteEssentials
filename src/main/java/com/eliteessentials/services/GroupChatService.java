package com.eliteessentials.services;

import com.eliteessentials.config.ConfigManager;
import com.eliteessentials.integration.LuckPermsIntegration;
import com.eliteessentials.model.GroupChat;
import com.eliteessentials.util.MessageFormatter;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;
import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.Universe;
import com.hypixel.hytale.server.core.universe.world.World;

import java.io.*;
import java.lang.reflect.Type;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.logging.Logger;

/**
 * Service for managing group-based private chat channels.
 * 
 * Players can chat with others in their LuckPerms group using /gc [group] <message>.
 * If a player belongs to multiple groups, they can specify which group to chat in.
 * 
 * Configuration is stored in groupchat.json and defines which groups have chat channels,
 * their display names, prefixes, and colors.
 */
public class GroupChatService {
    
    private static final Logger logger = Logger.getLogger("EliteEssentials");
    private static final String GROUP_CHAT_FILE = "groupchat.json";
    private static final Gson gson = new GsonBuilder().setPrettyPrinting().create();
    
    private final File dataFolder;
    private final ConfigManager configManager;
    private final Object fileLock = new Object();
    
    private List<GroupChat> groupChats = new ArrayList<>();
    
    public GroupChatService(File dataFolder, ConfigManager configManager) {
        this.dataFolder = dataFolder;
        this.configManager = configManager;
        load();
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
                    logger.info("Loaded " + groupChats.size() + " group chat configurations.");
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
        groupChats.add(GroupChat.adminGroup());
        groupChats.add(GroupChat.modGroup());
        groupChats.add(GroupChat.staffGroup());
        groupChats.add(GroupChat.vipGroup());
        logger.info("Created default group chat configuration.");
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
     * Get a group chat by group name (case-insensitive).
     */
    public GroupChat getGroupChat(String groupName) {
        for (GroupChat gc : groupChats) {
            if (gc.getGroupName().equalsIgnoreCase(groupName)) {
                return gc;
            }
        }
        return null;
    }
    
    /**
     * Get all groups a player belongs to that have group chat enabled.
     * Uses LuckPerms to determine group membership.
     */
    public List<GroupChat> getPlayerGroupChats(UUID playerId) {
        List<GroupChat> result = new ArrayList<>();
        
        if (!LuckPermsIntegration.isAvailable()) {
            if (configManager.isDebugEnabled()) {
                logger.info("LuckPerms not available for group chat.");
            }
            return result;
        }
        
        List<String> playerGroups = LuckPermsIntegration.getGroups(playerId);
        
        if (configManager.isDebugEnabled()) {
            logger.info("Player groups from LuckPerms: " + playerGroups);
        }
        
        for (GroupChat gc : groupChats) {
            if (!gc.isEnabled()) continue;
            
            for (String playerGroup : playerGroups) {
                if (playerGroup.equalsIgnoreCase(gc.getGroupName())) {
                    result.add(gc);
                    break;
                }
            }
        }
        
        return result;
    }
    
    /**
     * Check if a player belongs to a specific group.
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
     * Broadcast a message to all players in a specific group.
     * 
     * @param groupChat The group chat configuration
     * @param sender The player sending the message
     * @param message The message content
     */
    public void broadcast(GroupChat groupChat, PlayerRef sender, String message) {
        // Build the formatted message
        // Format: [PREFIX] PlayerName: message
        // Color needs & prefix for hex colors (e.g., #FF0000 -> &#FF0000)
        String colorCode = groupChat.getColor();
        if (colorCode != null && colorCode.startsWith("#") && !colorCode.startsWith("&#")) {
            colorCode = "&" + colorCode;
        }
        
        String format = colorCode + groupChat.getPrefix() + " &f" + 
                       sender.getUsername() + "&7: &r" + message;
        
        Message formattedMessage = MessageFormatter.format(format);
        
        // Find all players in this group
        List<PlayerRef> recipients = new ArrayList<>();
        Universe universe = Universe.get();
        
        if (universe != null) {
            for (Map.Entry<String, World> entry : universe.getWorlds().entrySet()) {
                World world = entry.getValue();
                Collection<PlayerRef> players = world.getPlayerRefs();
                
                if (players != null) {
                    for (PlayerRef player : players) {
                        if (player != null && player.isValid()) {
                            if (playerBelongsToGroup(player.getUuid(), groupChat.getGroupName())) {
                                recipients.add(player);
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
        
        if (configManager.isDebugEnabled()) {
            logger.info("Group chat [" + groupChat.getGroupName() + "] from " + 
                       sender.getUsername() + " sent to " + recipients.size() + " players.");
        }
    }
    
    /**
     * Broadcast a message using the player's first available group.
     * 
     * @param sender The player sending the message
     * @param message The message content
     * @return true if message was sent, false if player has no group chat access
     */
    public boolean broadcast(PlayerRef sender, String message) {
        List<GroupChat> playerGroups = getPlayerGroupChats(sender.getUuid());
        if (playerGroups.isEmpty()) {
            return false;
        }
        
        broadcast(playerGroups.get(0), sender, message);
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
}
