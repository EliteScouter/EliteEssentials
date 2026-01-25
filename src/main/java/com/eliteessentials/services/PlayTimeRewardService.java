package com.eliteessentials.services;

import com.eliteessentials.EliteEssentials;
import com.eliteessentials.config.ConfigManager;
import com.eliteessentials.config.PluginConfig;
import com.eliteessentials.integration.LuckPermsIntegration;
import com.eliteessentials.model.PlayTimeReward;
import com.eliteessentials.model.PlayerData;
import com.eliteessentials.storage.PlayTimeRewardStorage;
import com.eliteessentials.util.MessageFormatter;
import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.Universe;

import java.util.*;
import java.util.concurrent.*;
import java.util.logging.Logger;

/**
 * Service for managing playtime rewards.
 * Periodically checks online players and grants eligible rewards.
 */
public class PlayTimeRewardService {

    private static final Logger logger = Logger.getLogger("EliteEssentials");
    
    private final PlayTimeRewardStorage storage;
    private final PlayerService playerService;
    private final ConfigManager configManager;
    
    private ScheduledExecutorService scheduler;
    private final Map<UUID, Long> sessionStartTimes = new ConcurrentHashMap<>();
    private final Map<UUID, Long> playerBaselines = new ConcurrentHashMap<>();

    public PlayTimeRewardService(PlayTimeRewardStorage storage, PlayerService playerService, 
                                  ConfigManager configManager) {
        this.storage = storage;
        this.playerService = playerService;
        this.configManager = configManager;
    }
    
    /**
     * Start the reward check scheduler.
     */
    public void start() {
        PluginConfig config = configManager.getConfig();
        if (!config.playTimeRewards.enabled) {
            return;
        }
        
        // Record when the system was first enabled (for onlyCountNewPlaytime)
        if (config.playTimeRewards.enabledTimestamp == 0) {
            config.playTimeRewards.enabledTimestamp = System.currentTimeMillis();
            configManager.saveConfig();
            logger.info("PlayTime Rewards enabled for the first time - timestamp recorded");
        }
        
        scheduler = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "EliteEssentials-PlayTimeRewards");
            t.setDaemon(true);
            return t;
        });
        
        int intervalMinutes = Math.max(1, config.playTimeRewards.checkIntervalMinutes);
        scheduler.scheduleAtFixedRate(this::checkAllPlayers, intervalMinutes, intervalMinutes, TimeUnit.MINUTES);
        
        logger.info("PlayTime Rewards service started (checking every " + intervalMinutes + " minutes)");
        if (config.playTimeRewards.onlyCountNewPlaytime) {
            logger.info("PlayTime Rewards: Only counting playtime since " + new java.util.Date(config.playTimeRewards.enabledTimestamp));
        }
    }
    
    /**
     * Stop the scheduler.
     */
    public void stop() {
        if (scheduler != null) {
            scheduler.shutdown();
            try {
                if (!scheduler.awaitTermination(5, TimeUnit.SECONDS)) {
                    scheduler.shutdownNow();
                }
            } catch (InterruptedException e) {
                scheduler.shutdownNow();
                Thread.currentThread().interrupt();
            }
        }
    }
    
    /**
     * Track when a player joins (for session time calculation).
     */
    public void onPlayerJoin(UUID playerId) {
        sessionStartTimes.put(playerId, System.currentTimeMillis());
        
        // Initialize baseline for new playtime tracking if needed
        PluginConfig config = configManager.getConfig();
        if (config.playTimeRewards.onlyCountNewPlaytime && !playerBaselines.containsKey(playerId)) {
            Optional<PlayerData> playerOpt = playerService.getPlayer(playerId);
            if (playerOpt.isPresent()) {
                long storedSeconds = playerOpt.get().getPlayTime();
                playerBaselines.put(playerId, storedSeconds);
                if (configManager.isDebugEnabled()) {
                    logger.info("[PlayTimeRewards] Recorded baseline for " + playerOpt.get().getName() + ": " + storedSeconds + "s");
                }
            }
        }
    }
    
    /**
     * Clean up when player leaves.
     */
    public void onPlayerQuit(UUID playerId) {
        sessionStartTimes.remove(playerId);
    }

    /**
     * Check all online players for eligible rewards.
     */
    private void checkAllPlayers() {
        try {
            Universe universe = Universe.get();
            if (universe == null) return;
            
            for (PlayerRef playerRef : universe.getPlayers()) {
                if (playerRef != null && playerRef.isValid()) {
                    UUID playerId = playerRef.getUuid();
                    
                    // Ensure session tracking exists (in case player joined before service started)
                    if (!sessionStartTimes.containsKey(playerId)) {
                        sessionStartTimes.put(playerId, System.currentTimeMillis());
                    }
                    
                    checkPlayerRewards(playerId);
                }
            }
        } catch (Exception e) {
            logger.warning("Error checking playtime rewards: " + e.getMessage());
        }
    }
    
    /**
     * Check and grant eligible rewards for a specific player.
     */
    public void checkPlayerRewards(UUID playerId) {
        PluginConfig config = configManager.getConfig();
        if (!config.playTimeRewards.enabled) {
            if (configManager.isDebugEnabled()) {
                logger.info("[PlayTimeRewards] System is disabled in config");
            }
            return;
        }
        
        Optional<PlayerData> playerOpt = playerService.getPlayer(playerId);
        if (playerOpt.isEmpty()) {
            if (configManager.isDebugEnabled()) {
                logger.info("[PlayTimeRewards] No player data found for " + playerId);
            }
            return;
        }
        
        PlayerData playerData = playerOpt.get();
        long totalPlayTimeMinutes = getTotalPlayTimeMinutes(playerId, playerData);
        
        if (configManager.isDebugEnabled()) {
            logger.info("[PlayTimeRewards] Checking rewards for " + playerData.getName() + 
                    " - Total playtime: " + totalPlayTimeMinutes + " minutes");
        }
        
        List<PlayTimeReward> rewards = storage.getEnabledRewards();
        
        if (configManager.isDebugEnabled()) {
            logger.info("[PlayTimeRewards] Found " + rewards.size() + " enabled rewards");
        }
        
        for (PlayTimeReward reward : rewards) {
            if (reward.isRepeatable()) {
                checkRepeatableReward(playerId, playerData.getName(), reward, totalPlayTimeMinutes);
            } else {
                checkMilestoneReward(playerId, playerData.getName(), reward, totalPlayTimeMinutes);
            }
        }
    }
    
    /**
     * Get total play time including current session.
     * If onlyCountNewPlaytime is enabled, only counts time since the system was enabled.
     */
    private long getTotalPlayTimeMinutes(UUID playerId, PlayerData playerData) {
        PluginConfig config = configManager.getConfig();
        
        long storedSeconds = playerData.getPlayTime();
        Long sessionStart = sessionStartTimes.get(playerId);
        long sessionSeconds = 0;
        if (sessionStart != null) {
            sessionSeconds = (System.currentTimeMillis() - sessionStart) / 1000;
        }
        long totalSeconds = storedSeconds + sessionSeconds;
        
        // If onlyCountNewPlaytime is enabled, subtract time before the system was enabled
        if (config.playTimeRewards.onlyCountNewPlaytime && config.playTimeRewards.enabledTimestamp > 0) {
            // Calculate how much playtime the player had BEFORE the system was enabled
            long enabledTimestamp = config.playTimeRewards.enabledTimestamp;
            long playerFirstJoin = playerData.getFirstJoin();
            
            // If player joined before the system was enabled, subtract their pre-existing playtime
            if (playerFirstJoin < enabledTimestamp && playerFirstJoin > 0) {
                // Estimate pre-existing playtime: stored playtime at the time of enabling
                // We use the player's stored playtime minus current session as a baseline
                // This isn't perfect but prevents the flood of catch-up rewards
                
                // Get the player's baseline (what they had when system was enabled)
                Long baseline = playerBaselines.get(playerId);
                if (baseline == null) {
                    // First time checking this player - record their current stored time as baseline
                    baseline = storedSeconds;
                    playerBaselines.put(playerId, baseline);
                    
                    if (configManager.isDebugEnabled()) {
                        logger.info("[PlayTimeRewards] Recorded baseline for " + playerData.getName() + ": " + baseline + "s");
                    }
                }
                
                // Only count time accumulated after baseline was recorded
                totalSeconds = Math.max(0, (storedSeconds + sessionSeconds) - baseline);
            }
        }
        
        if (configManager.isDebugEnabled()) {
            logger.info("[PlayTimeRewards] Playtime calc for " + playerData.getName() + 
                    ": stored=" + storedSeconds + "s, session=" + sessionSeconds + "s, effective=" + totalSeconds + "s (" + (totalSeconds / 60) + " min)");
        }
        
        return totalSeconds / 60; // Convert to minutes
    }

    /**
     * Check and grant a milestone (one-time) reward.
     */
    private void checkMilestoneReward(UUID playerId, String playerName, PlayTimeReward reward, long totalMinutes) {
        // Already claimed?
        if (storage.hasClaimed(playerId, reward.getId())) {
            return;
        }
        
        // Eligible?
        if (totalMinutes >= reward.getMinutesRequired()) {
            grantReward(playerId, playerName, reward);
            storage.markMilestoneClaimed(playerId, reward.getId());
            
            // Broadcast milestone if configured
            PluginConfig config = configManager.getConfig();
            if (config.playTimeRewards.broadcastMilestones) {
                broadcastMilestone(playerName, reward);
            }
            
            if (configManager.isDebugEnabled()) {
                logger.info("Granted milestone reward '" + reward.getId() + "' to " + playerName);
            }
        }
    }
    
    /**
     * Check and grant a repeatable reward.
     * Only grants ONE reward per check cycle to prevent spam.
     */
    private void checkRepeatableReward(UUID playerId, String playerName, PlayTimeReward reward, long totalMinutes) {
        int claimCount = storage.getClaimCount(playerId, reward.getId());
        int interval = reward.getMinutesRequired();
        
        // How many times should they have received this reward by now?
        int expectedClaims = (int) (totalMinutes / interval);
        
        if (configManager.isDebugEnabled()) {
            logger.info("[PlayTimeRewards] Repeatable '" + reward.getId() + "' for " + playerName + 
                    ": interval=" + interval + "min, totalMinutes=" + totalMinutes + 
                    ", expectedClaims=" + expectedClaims + ", actualClaims=" + claimCount);
        }
        
        // Grant only ONE reward per check cycle (prevents spam)
        // If they're behind, they'll catch up over multiple cycles
        if (claimCount < expectedClaims) {
            grantReward(playerId, playerName, reward);
            storage.incrementRepeatableClaim(playerId, reward.getId());
            claimCount++;
            
            if (configManager.isDebugEnabled()) {
                logger.info("[PlayTimeRewards] Granted repeatable reward '" + reward.getId() + "' to " + playerName + " (claim #" + claimCount + ")");
            }
        }
    }

    /**
     * Grant a reward to a player (send message and execute commands).
     */
    private void grantReward(UUID playerId, String playerName, PlayTimeReward reward) {
        PluginConfig config = configManager.getConfig();
        
        // Send message to player
        if (config.playTimeRewards.showRewardMessage) {
            try {
                PlayerRef playerRef = Universe.get().getPlayer(playerId);
                if (playerRef != null && playerRef.isValid()) {
                    // Use reward's custom message if set, otherwise use default from config
                    String message;
                    if (reward.getMessage() != null && !reward.getMessage().isEmpty()) {
                        message = reward.getMessage().replace("{player}", playerName)
                                .replace("{reward}", reward.getName())
                                .replace("{time}", reward.getFormattedTime());
                    } else {
                        message = configManager.getMessage("playTimeRewardReceived", 
                                "reward", reward.getName());
                    }
                    playerRef.sendMessage(MessageFormatter.format(message));
                }
            } catch (Exception e) {
                logger.warning("Could not send reward message to " + playerName + ": " + e.getMessage());
            }
        }
        
        // Execute commands
        for (String command : reward.getCommands()) {
            executeCommand(command, playerName, playerId);
        }
    }
    
    /**
     * Execute a reward command with placeholder replacement.
     * Note: Hytale doesn't have a direct command dispatch API, so we handle
     * common commands internally and log others for manual execution.
     */
    private void executeCommand(String command, String playerName, UUID playerId) {
        try {
            // Replace placeholders
            String processedCommand = command.replace("{player}", playerName);
            
            // Remove leading slash if present
            if (processedCommand.startsWith("/")) {
                processedCommand = processedCommand.substring(1);
            }
            
            // Parse command and args
            String[] parts = processedCommand.split(" ", 2);
            String cmdName = parts[0].toLowerCase();
            String args = parts.length > 1 ? parts[1].trim() : "";
            
            // Handle known commands internally
            switch (cmdName) {
                case "eco":
                    handleEcoCommand(playerId, playerName, args);
                    break;
                case "lp":
                case "luckperms":
                    handleLuckPermsCommand(playerId, playerName, args);
                    break;
                default:
                    // Unknown command - log for manual execution (only in debug mode)
                    if (configManager.isDebugEnabled()) {
                        logger.info("[PlayTimeReward] Command for " + playerName + ": /" + processedCommand);
                        logger.info("[PlayTimeReward] Note: This command type is not handled automatically");
                    }
            }
            
            if (configManager.isDebugEnabled()) {
                logger.info("Processed reward command: " + processedCommand);
            }
        } catch (Exception e) {
            logger.warning("Failed to execute reward command '" + command + "': " + e.getMessage());
        }
    }
    
    /**
     * Handle economy commands internally.
     */
    private void handleEcoCommand(UUID playerId, String playerName, String args) {
        // Parse: give/take/set <player> <amount>
        String[] parts = args.split(" ");
        if (parts.length < 3) {
            logger.warning("[PlayTimeReward] Invalid eco command format: eco " + args);
            return;
        }
        
        String action = parts[0].toLowerCase();
        String targetName = parts[1];
        double amount;
        
        try {
            amount = Double.parseDouble(parts[2]);
        } catch (NumberFormatException e) {
            logger.warning("[PlayTimeReward] Invalid amount in eco command: " + parts[2]);
            return;
        }
        
        // Get target player ID
        UUID targetId = playerId; // Default to reward recipient
        if (!targetName.equalsIgnoreCase(playerName) && !targetName.equals("{player}")) {
            // Try to find player by name
            var targetData = EliteEssentials.getInstance().getPlayerService().getPlayerByName(targetName);
            if (targetData.isPresent()) {
                targetId = targetData.get().getUuid();
            } else {
                logger.warning("[PlayTimeReward] Player not found for eco command: " + targetName);
                return;
            }
        }
        
        PlayerService playerService = EliteEssentials.getInstance().getPlayerService();
        
        switch (action) {
            case "give":
            case "add":
                if (playerService.addMoney(targetId, amount)) {
                    if (configManager.isDebugEnabled()) {
                        logger.info("[PlayTimeReward] Added " + amount + " to " + targetName);
                    }
                }
                break;
            case "take":
            case "remove":
                if (playerService.removeMoney(targetId, amount)) {
                    if (configManager.isDebugEnabled()) {
                        logger.info("[PlayTimeReward] Removed " + amount + " from " + targetName);
                    }
                }
                break;
            case "set":
                if (playerService.setBalance(targetId, amount)) {
                    if (configManager.isDebugEnabled()) {
                        logger.info("[PlayTimeReward] Set " + targetName + " balance to " + amount);
                    }
                }
                break;
            default:
                logger.warning("[PlayTimeReward] Unknown eco action: " + action);
        }
    }
    
    /**
     * Handle LuckPerms commands via API.
     * Supported formats:
     * - lp user <player> parent set <group>
     * - lp user <player> parent add <group>
     * - lp user <player> parent remove <group>
     * - lp user <player> group set <group>
     * - lp user <player> group add <group>
     * - lp user <player> group remove <group>
     * - lp user <player> permission set <permission> [true/false]
     * - lp user <player> permission unset <permission>
     */
    private void handleLuckPermsCommand(UUID playerId, String playerName, String args) {
        if (!LuckPermsIntegration.isAvailable()) {
            logger.warning("[PlayTimeReward] LuckPerms not available, cannot execute: lp " + args);
            return;
        }
        
        String[] parts = args.split(" ");
        if (parts.length < 4) {
            logger.warning("[PlayTimeReward] Invalid LuckPerms command format: lp " + args);
            return;
        }
        
        // Parse: user <player> <action> <subaction> [value]
        String subCommand = parts[0].toLowerCase();
        if (!subCommand.equals("user")) {
            logger.warning("[PlayTimeReward] Only 'lp user' commands are supported: lp " + args);
            return;
        }
        
        String targetName = parts[1];
        String action = parts[2].toLowerCase();
        String subAction = parts[3].toLowerCase();
        
        // Get target player ID
        UUID targetId = playerId;
        if (!targetName.equalsIgnoreCase(playerName) && !targetName.equals("{player}")) {
            var targetData = EliteEssentials.getInstance().getPlayerService().getPlayerByName(targetName);
            if (targetData.isPresent()) {
                targetId = targetData.get().getUuid();
            } else {
                logger.warning("[PlayTimeReward] Player not found for LP command: " + targetName);
                return;
            }
        }
        
        boolean success = false;
        
        // Handle parent/group commands
        if (action.equals("parent") || action.equals("group")) {
            if (parts.length < 5) {
                logger.warning("[PlayTimeReward] Missing group name in LP command: lp " + args);
                return;
            }
            String groupName = parts[4];
            
            switch (subAction) {
                case "set":
                    success = LuckPermsIntegration.setGroup(targetId, groupName);
                    if (success) {
                        logger.info("[PlayTimeReward] Set " + targetName + " group to: " + groupName);
                    }
                    break;
                case "add":
                    success = LuckPermsIntegration.addGroup(targetId, groupName);
                    if (success) {
                        logger.info("[PlayTimeReward] Added " + targetName + " to group: " + groupName);
                    }
                    break;
                case "remove":
                    success = LuckPermsIntegration.removeGroup(targetId, groupName);
                    if (success) {
                        logger.info("[PlayTimeReward] Removed " + targetName + " from group: " + groupName);
                    }
                    break;
                default:
                    logger.warning("[PlayTimeReward] Unknown LP parent/group action: " + subAction);
            }
        }
        // Handle permission commands
        else if (action.equals("permission")) {
            if (parts.length < 5) {
                logger.warning("[PlayTimeReward] Missing permission in LP command: lp " + args);
                return;
            }
            String permission = parts[4];
            
            switch (subAction) {
                case "set":
                    // Check for optional true/false value
                    boolean value = true;
                    if (parts.length >= 6) {
                        value = !parts[5].equalsIgnoreCase("false");
                    }
                    success = LuckPermsIntegration.setPermission(targetId, permission, value);
                    if (success) {
                        logger.info("[PlayTimeReward] Set permission " + permission + "=" + value + " for " + targetName);
                    }
                    break;
                case "unset":
                    success = LuckPermsIntegration.unsetPermission(targetId, permission);
                    if (success) {
                        logger.info("[PlayTimeReward] Unset permission " + permission + " for " + targetName);
                    }
                    break;
                default:
                    logger.warning("[PlayTimeReward] Unknown LP permission action: " + subAction);
            }
        }
        // Handle promote command: lp user <player> promote <track>
        else if (action.equals("promote")) {
            // For promote, subAction is actually the track name
            String trackName = subAction;
            success = LuckPermsIntegration.promote(targetId, trackName);
            if (success) {
                logger.info("[PlayTimeReward] Promoted " + targetName + " on track: " + trackName);
            }
        }
        // Handle demote command: lp user <player> demote <track>
        else if (action.equals("demote")) {
            // For demote, subAction is actually the track name
            String trackName = subAction;
            success = LuckPermsIntegration.demote(targetId, trackName);
            if (success) {
                logger.info("[PlayTimeReward] Demoted " + targetName + " on track: " + trackName);
            }
        }
        else {
            logger.warning("[PlayTimeReward] Unsupported LP action: " + action + ". Supported: parent, group, permission, promote, demote");
        }
        
        if (!success && configManager.isDebugEnabled()) {
            logger.warning("[PlayTimeReward] LP command may have failed: lp " + args);
        }
    }

    /**
     * Broadcast a milestone achievement to all players.
     */
    private void broadcastMilestone(String playerName, PlayTimeReward reward) {
        try {
            Universe universe = Universe.get();
            if (universe == null) return;
            
            String broadcastMsg = configManager.getMessage("playTimeMilestoneBroadcast",
                    "player", playerName,
                    "reward", reward.getName(),
                    "time", reward.getFormattedTime());
            Message message = MessageFormatter.format(broadcastMsg);
            
            for (PlayerRef player : universe.getPlayers()) {
                if (player != null && player.isValid()) {
                    player.sendMessage(message);
                }
            }
        } catch (Exception e) {
            logger.warning("Could not broadcast milestone: " + e.getMessage());
        }
    }
    
    /**
     * Get storage for external access.
     */
    public PlayTimeRewardStorage getStorage() {
        return storage;
    }
    
    /**
     * Reload rewards configuration.
     */
    public void reload() {
        storage.reload();
        
        // Restart scheduler with potentially new interval
        stop();
        start();
    }
}
