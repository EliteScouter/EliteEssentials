package com.eliteessentials.storage;

import com.eliteessentials.model.PlayTimeReward;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;

import java.io.*;
import java.lang.reflect.Type;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.logging.Logger;

/**
 * Storage for playtime rewards configuration and claim tracking.
 * 
 * Files:
 * - playtime_rewards.json: Reward definitions (milestones and repeatable)
 * - playtime_claims.json: Tracks which rewards each player has claimed
 */
public class PlayTimeRewardStorage {
    
    private static final Logger logger = Logger.getLogger("EliteEssentials");
    private static final Gson gson = new GsonBuilder().setPrettyPrinting().create();
    
    private final File rewardsFile;
    private final File claimsFile;
    private final Object fileLock = new Object();
    
    /** All configured rewards */
    private List<PlayTimeReward> rewards = new ArrayList<>();
    
    /** 
     * Tracks claimed rewards per player.
     * For milestones: Set contains reward IDs that have been claimed
     * For repeatable: Map tracks how many times each reward has been claimed
     */
    private Map<UUID, PlayerRewardData> playerClaims = new HashMap<>();
    
    public PlayTimeRewardStorage(File dataFolder) {
        this.rewardsFile = new File(dataFolder, "playtime_rewards.json");
        this.claimsFile = new File(dataFolder, "playtime_claims.json");
    }
    
    /**
     * Load rewards and claims from files.
     */
    public void load() {
        loadRewards();
        loadClaims();
    }
    
    /**
     * Load reward definitions.
     */
    private void loadRewards() {
        if (!rewardsFile.exists()) {
            createDefaultRewards();
            return;
        }
        
        synchronized (fileLock) {
            try (Reader reader = new InputStreamReader(new FileInputStream(rewardsFile), StandardCharsets.UTF_8)) {
                Type type = new TypeToken<List<PlayTimeReward>>(){}.getType();
                List<PlayTimeReward> loaded = gson.fromJson(reader, type);
                if (loaded != null) {
                    rewards = loaded;
                    logger.info("Loaded " + rewards.size() + " playtime rewards");
                }
            } catch (Exception e) {
                logger.severe("Failed to load playtime_rewards.json: " + e.getMessage());
            }
        }
    }
    
    /**
     * Load player claim data.
     */
    private void loadClaims() {
        if (!claimsFile.exists()) {
            return;
        }
        
        synchronized (fileLock) {
            try (Reader reader = new InputStreamReader(new FileInputStream(claimsFile), StandardCharsets.UTF_8)) {
                Type type = new TypeToken<Map<UUID, PlayerRewardData>>(){}.getType();
                Map<UUID, PlayerRewardData> loaded = gson.fromJson(reader, type);
                if (loaded != null) {
                    playerClaims = loaded;
                }
            } catch (Exception e) {
                logger.severe("Failed to load playtime_claims.json: " + e.getMessage());
            }
        }
    }
    
    /**
     * Save reward definitions.
     */
    public void saveRewards() {
        synchronized (fileLock) {
            try (Writer writer = new OutputStreamWriter(new FileOutputStream(rewardsFile), StandardCharsets.UTF_8)) {
                gson.toJson(rewards, writer);
            } catch (Exception e) {
                logger.severe("Failed to save playtime_rewards.json: " + e.getMessage());
            }
        }
    }
    
    /**
     * Save player claim data.
     */
    public void saveClaims() {
        synchronized (fileLock) {
            try (Writer writer = new OutputStreamWriter(new FileOutputStream(claimsFile), StandardCharsets.UTF_8)) {
                gson.toJson(playerClaims, writer);
            } catch (Exception e) {
                logger.severe("Failed to save playtime_claims.json: " + e.getMessage());
            }
        }
    }
    
    /**
     * Create default reward examples.
     */
    private void createDefaultRewards() {
        rewards = new ArrayList<>();
        
        // Example repeatable reward - every hour
        PlayTimeReward hourly = new PlayTimeReward("hourly_bonus", "Hourly Bonus", 60, true);
        hourly.setMessage("&a[Reward] &fYou received your hourly playtime bonus!");
        hourly.setCommands(Arrays.asList(
            "eco add {player} 100"
        ));
        hourly.setEnabled(false); // Disabled by default
        rewards.add(hourly);
        
        // Example milestone - 10 hours
        PlayTimeReward tenHours = new PlayTimeReward("10h_milestone", "10 Hour Milestone", 600, false);
        tenHours.setMessage("&6[Milestone] &fCongratulations! You've played for 10 hours!");
        tenHours.setCommands(Arrays.asList(
            "eco add {player} 500"
        ));
        tenHours.setEnabled(false);
        rewards.add(tenHours);
        
        // Example milestone - 100 hours (VIP) using group set
        PlayTimeReward hundredHours = new PlayTimeReward("100h_vip", "100 Hour VIP", 6000, false);
        hundredHours.setMessage("&d[Milestone] &fAmazing! 100 hours played! You've earned VIP status!");
        hundredHours.setCommands(Arrays.asList(
            "lp user {player} group set vip",
            "eco add {player} 5000"
        ));
        hundredHours.setEnabled(false);
        rewards.add(hundredHours);
        
        // Example milestone - using track promotion
        PlayTimeReward trackPromo = new PlayTimeReward("rank_up", "Rank Promotion", 1440, false);
        trackPromo.setMessage("&b[Rank Up] &fYou've been promoted on the ranks track!");
        trackPromo.setCommands(Arrays.asList(
            "lp user {player} promote ranks"
        ));
        trackPromo.setEnabled(false);
        rewards.add(trackPromo);
        
        saveRewards();
        logger.info("Created default playtime_rewards.json with example rewards (disabled by default)");
    }
    
    /**
     * Get all configured rewards.
     */
    public List<PlayTimeReward> getRewards() {
        return new ArrayList<>(rewards);
    }
    
    /**
     * Get only enabled rewards.
     */
    public List<PlayTimeReward> getEnabledRewards() {
        List<PlayTimeReward> enabled = new ArrayList<>();
        for (PlayTimeReward reward : rewards) {
            if (reward.isEnabled()) {
                enabled.add(reward);
            }
        }
        return enabled;
    }
    
    /**
     * Get a reward by ID.
     */
    public Optional<PlayTimeReward> getReward(String id) {
        for (PlayTimeReward reward : rewards) {
            if (reward.getId().equalsIgnoreCase(id)) {
                return Optional.of(reward);
            }
        }
        return Optional.empty();
    }
    
    /**
     * Check if a player has claimed a milestone reward.
     */
    public boolean hasClaimed(UUID playerId, String rewardId) {
        PlayerRewardData data = playerClaims.get(playerId);
        if (data == null) return false;
        return data.claimedMilestones.contains(rewardId);
    }
    
    /**
     * Get how many times a player has claimed a repeatable reward.
     */
    public int getClaimCount(UUID playerId, String rewardId) {
        PlayerRewardData data = playerClaims.get(playerId);
        if (data == null) return 0;
        return data.repeatableCounts.getOrDefault(rewardId, 0);
    }
    
    /**
     * Mark a milestone reward as claimed.
     */
    public void markMilestoneClaimed(UUID playerId, String rewardId) {
        PlayerRewardData data = playerClaims.computeIfAbsent(playerId, k -> new PlayerRewardData());
        data.claimedMilestones.add(rewardId);
        saveClaims();
    }
    
    /**
     * Increment the claim count for a repeatable reward.
     */
    public void incrementRepeatableClaim(UUID playerId, String rewardId) {
        PlayerRewardData data = playerClaims.computeIfAbsent(playerId, k -> new PlayerRewardData());
        int current = data.repeatableCounts.getOrDefault(rewardId, 0);
        data.repeatableCounts.put(rewardId, current + 1);
        saveClaims();
    }
    
    /**
     * Get player reward data (for checking eligibility).
     */
    public PlayerRewardData getPlayerData(UUID playerId) {
        return playerClaims.computeIfAbsent(playerId, k -> new PlayerRewardData());
    }
    
    /**
     * Reload rewards from file.
     */
    public void reload() {
        loadRewards();
        loadClaims();
    }
    
    /**
     * Inner class to track player reward claims.
     */
    public static class PlayerRewardData {
        /** Set of milestone reward IDs that have been claimed */
        public Set<String> claimedMilestones = new HashSet<>();
        
        /** Map of repeatable reward ID -> number of times claimed */
        public Map<String, Integer> repeatableCounts = new HashMap<>();
    }
}
