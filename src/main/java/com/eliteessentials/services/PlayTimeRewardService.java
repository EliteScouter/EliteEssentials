package com.eliteessentials.services;

import com.eliteessentials.EliteEssentials;
import com.eliteessentials.config.ConfigManager;
import com.eliteessentials.config.PluginConfig;
import com.eliteessentials.model.PlayTimeReward;
import com.eliteessentials.model.PlayerFile;
import com.eliteessentials.storage.PlayTimeRewardStorage;
import com.eliteessentials.storage.PlayerFileStorage;
import com.eliteessentials.util.CommandExecutor;
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
 * Reward definitions are in playtime_rewards.json (server-wide).
 * Player claims are stored in per-player files via PlayerFileStorage.
 */
public class PlayTimeRewardService {

    private static final Logger logger = Logger.getLogger("EliteEssentials");
    
    private final PlayTimeRewardStorage storage;  // For reward definitions only
    private final PlayerService playerService;
    private final ConfigManager configManager;
    private PlayerFileStorage playerFileStorage;
    
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
     * Set the player file storage (called after initialization).
     */
    public void setPlayerFileStorage(PlayerFileStorage storage) {
        this.playerFileStorage = storage;
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
            Optional<PlayerFile> playerOpt = playerService.getPlayer(playerId);
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
     * Reset session start times after a periodic play time flush.
     * Called by PlayerService to keep reward session tracking in sync
     * when accumulated play time is flushed to disk mid-session.
     */
    public void resetSessionStarts(long newStartTime) {
        for (UUID playerId : sessionStartTimes.keySet()) {
            sessionStartTimes.put(playerId, newStartTime);
        }
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
        
        Optional<PlayerFile> playerOpt = playerService.getPlayer(playerId);
        if (playerOpt.isEmpty()) {
            if (configManager.isDebugEnabled()) {
                logger.info("[PlayTimeRewards] No player data found for " + playerId);
            }
            return;
        }
        
        PlayerFile playerData = playerOpt.get();
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
    private long getTotalPlayTimeMinutes(UUID playerId, PlayerFile playerData) {
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
        // Already claimed? Check player file
        if (playerFileStorage != null) {
            PlayerFile playerFile = playerFileStorage.getPlayer(playerId);
            if (playerFile != null && playerFile.hasClaimedMilestone(reward.getId())) {
                return;
            }
        } else if (storage.hasClaimed(playerId, reward.getId())) {
            // Fallback to old storage if playerFileStorage not set
            return;
        }
        
        // Eligible?
        if (totalMinutes >= reward.getMinutesRequired()) {
            grantReward(playerId, playerName, reward);
            
            // Mark as claimed in player file
            if (playerFileStorage != null) {
                PlayerFile playerFile = playerFileStorage.getPlayer(playerId);
                if (playerFile != null) {
                    playerFile.claimMilestone(reward.getId());
                    playerFileStorage.saveAndMarkDirty(playerId);
                }
            } else {
                storage.markMilestoneClaimed(playerId, reward.getId());
            }
            
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
        int claimCount;
        
        // Get claim count from player file
        if (playerFileStorage != null) {
            PlayerFile playerFile = playerFileStorage.getPlayer(playerId);
            claimCount = playerFile != null ? playerFile.getRepeatableClaimCount(reward.getId()) : 0;
        } else {
            claimCount = storage.getClaimCount(playerId, reward.getId());
        }
        
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
            
            // Increment claim count in player file
            if (playerFileStorage != null) {
                PlayerFile playerFile = playerFileStorage.getPlayer(playerId);
                if (playerFile != null) {
                    playerFile.incrementRepeatableClaim(reward.getId());
                    playerFileStorage.saveAndMarkDirty(playerId);
                }
            } else {
                storage.incrementRepeatableClaim(playerId, reward.getId());
            }
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
        CommandExecutor.setDebugEnabled(configManager.isDebugEnabled());
        CommandExecutor.executeCommands(reward.getCommands(), playerName, playerId, "PlayTimeReward");
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
