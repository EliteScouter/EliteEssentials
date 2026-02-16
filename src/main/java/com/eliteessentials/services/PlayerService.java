package com.eliteessentials.services;

import com.eliteessentials.config.ConfigManager;
import com.eliteessentials.model.PlayerFile;
import com.eliteessentials.storage.PlayerFileStorage;
import com.eliteessentials.util.MessageFormatter;
import com.hypixel.hytale.server.core.universe.PlayerRef;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.logging.Logger;

/**
 * Service for managing player data.
 * Tracks player sessions and updates play time on disconnect.
 */
public class PlayerService {

    private static final Logger logger = Logger.getLogger("EliteEssentials");
    
    private final PlayerFileStorage storage;
    private final ConfigManager configManager;
    
    // Track session start times for play time calculation
    private final Map<UUID, Long> sessionStartTimes = new ConcurrentHashMap<>();
    
    // Periodic save scheduler for crash protection
    private ScheduledExecutorService periodicSaveScheduler;
    
    // Reference to reward service for session sync during periodic flush
    private PlayTimeRewardService playTimeRewardService;

    public PlayerService(PlayerFileStorage storage, ConfigManager configManager) {
        this.storage = storage;
        this.configManager = configManager;
    }
    
    /**
     * Set the play time reward service for session sync during periodic flushes.
     */
    public void setPlayTimeRewardService(PlayTimeRewardService service) {
        this.playTimeRewardService = service;
    }

    /**
     * Called when a player joins the server.
     * Creates or updates their player data.
     */
    public PlayerFile onPlayerJoin(UUID playerId, String playerName) {
        boolean isNew = !storage.hasPlayer(playerId);
        PlayerFile data = storage.getPlayer(playerId, playerName);
        
        // Update name in case it changed
        if (!data.getName().equals(playerName)) {
            logger.info("Player " + playerId + " name changed: " + data.getName() + " -> " + playerName);
            data.setName(playerName);
            storage.markDirty(playerId);
        }
        
        // Set starting balance for new players if economy is enabled
        if (isNew && configManager.getConfig().economy.enabled) {
            double startingBalance = configManager.getConfig().economy.startingBalance;
            if (startingBalance > 0) {
                data.setWallet(startingBalance);
                storage.markDirty(playerId);
                logger.info("Set starting balance of " + startingBalance + " for new player " + playerName);
            }
        }
        
        // Track session start
        sessionStartTimes.put(playerId, System.currentTimeMillis());
        
        // Save if new player
        if (isNew) {
            storage.savePlayer(playerId);
        }
        
        return data;
    }

    /**
     * Called when a player leaves the server.
     * Updates last seen and play time.
     */
    public void onPlayerQuit(UUID playerId) {
        PlayerFile data = storage.getPlayer(playerId);
        if (data != null) {
            // Update last seen
            data.updateLastSeen();
            
            // Calculate and add session play time
            Long sessionStart = sessionStartTimes.remove(playerId);
            if (sessionStart != null) {
                long sessionSeconds = (System.currentTimeMillis() - sessionStart) / 1000;
                data.addPlayTime(sessionSeconds);
            }
            
            storage.markDirty(playerId);
        }
        
        // Unload player (saves if dirty)
        storage.unloadPlayer(playerId);
    }

    /**
     * Check if this is a player's first time joining.
     */
    public boolean isFirstJoin(UUID playerId) {
        return !storage.hasPlayer(playerId);
    }

    /**
     * Get player data by UUID.
     */
    public Optional<PlayerFile> getPlayer(UUID playerId) {
        return Optional.ofNullable(storage.getPlayer(playerId));
    }

    /**
     * Get player data by name.
     */
    public Optional<PlayerFile> getPlayerByName(String name) {
        return Optional.ofNullable(storage.getPlayerByName(name));
    }
    /**
     * Get the current session play time in seconds for an online player.
     * Returns 0 if the player has no active session.
     */
    public long getCurrentSessionSeconds(UUID playerId) {
        Long sessionStart = sessionStartTimes.get(playerId);
        if (sessionStart == null) {
            return 0;
        }
        return (System.currentTimeMillis() - sessionStart) / 1000;
    }

    /**
     * Get wallet balance for a player.
     */
    public double getBalance(UUID playerId) {
        PlayerFile data = storage.getPlayer(playerId);
        return data != null ? data.getWallet() : 0.0;
    }

    /**
     * Add money to a player's wallet.
     */
    public boolean addMoney(UUID playerId, double amount) {
        return addMoney(playerId, amount, null, null);
    }
    
    /**
     * Add money to a player's wallet with optional notification.
     * @param playerId Player UUID
     * @param amount Amount to add
     * @param playerRef Player reference for notifications (null if not available)
     */
    public boolean addMoney(UUID playerId, double amount, PlayerRef playerRef) {
        return addMoney(playerId, amount, playerRef, null);
    }
    
    /**
     * Add money to a player's wallet with optional notification.
     * @param playerId Player UUID
     * @param amount Amount to add
     * @param playerRef Player reference for notifications (null if not available)
     * @param senderName Name of the sender (player name or "Server" for console)
     */
    public boolean addMoney(UUID playerId, double amount, PlayerRef playerRef, String senderName) {
        PlayerFile data = storage.getPlayer(playerId);
        if (data == null) {
            return false;
        }
        
        double oldBalance = data.getWallet();
        data.modifyWallet(amount);
        storage.saveAndMarkDirty(playerId);
        
        // Notify player of balance change if configured
        notifyBalanceChange(playerId, oldBalance, data.getWallet(), playerRef, senderName);
        
        return true;
    }

    /**
     * Remove money from a player's wallet.
     * Returns false if insufficient funds.
     */
    public boolean removeMoney(UUID playerId, double amount) {
        return removeMoney(playerId, amount, null, null);
    }
    
    /**
     * Remove money from a player's wallet with optional notification.
     * @param playerId Player UUID
     * @param amount Amount to remove
     * @param playerRef Player reference for notifications (null if not available)
     */
    public boolean removeMoney(UUID playerId, double amount, PlayerRef playerRef) {
        return removeMoney(playerId, amount, playerRef, null);
    }
    
    /**
     * Remove money from a player's wallet with optional notification.
     * @param playerId Player UUID
     * @param amount Amount to remove
     * @param playerRef Player reference for notifications (null if not available)
     * @param senderName Name of the sender (player name or "Server" for console)
     */
    public boolean removeMoney(UUID playerId, double amount, PlayerRef playerRef, String senderName) {
        PlayerFile data = storage.getPlayer(playerId);
        if (data == null) {
            return false;
        }
        
        double oldBalance = data.getWallet();
        if (!data.modifyWallet(-amount)) {
            return false;  // Insufficient funds
        }
        
        storage.saveAndMarkDirty(playerId);
        
        // Notify player of balance change if configured
        notifyBalanceChange(playerId, oldBalance, data.getWallet(), playerRef, senderName);
        
        return true;
    }

    /**
     * Set a player's wallet balance directly.
     */
    public boolean setBalance(UUID playerId, double amount) {
        return setBalance(playerId, amount, null, null);
    }
    
    /**
     * Set a player's wallet balance directly with optional notification.
     * @param playerId Player UUID
     * @param amount New balance
     * @param playerRef Player reference for notifications (null if not available)
     */
    public boolean setBalance(UUID playerId, double amount, PlayerRef playerRef) {
        return setBalance(playerId, amount, playerRef, null);
    }
    
    /**
     * Set a player's wallet balance directly with optional notification.
     * @param playerId Player UUID
     * @param amount New balance
     * @param playerRef Player reference for notifications (null if not available)
     * @param senderName Name of the sender (player name or "Server" for console)
     */
    public boolean setBalance(UUID playerId, double amount, PlayerRef playerRef, String senderName) {
        PlayerFile data = storage.getPlayer(playerId);
        if (data == null) {
            return false;
        }
        
        double oldBalance = data.getWallet();
        data.setWallet(amount);
        storage.saveAndMarkDirty(playerId);
        
        // Notify player of balance change if configured
        notifyBalanceChange(playerId, oldBalance, data.getWallet(), playerRef, senderName);
        
        return true;
    }

    /**
     * Get total unique player count.
     */
    public int getTotalPlayerCount() {
        return storage.getPlayerCount();
    }

    /**
     * Get all players sorted by last seen.
     */
    public List<PlayerFile> getRecentPlayers(int limit) {
        List<PlayerFile> players = storage.getPlayersByLastSeen();
        return players.subList(0, Math.min(limit, players.size()));
    }

    /**
     * Get top players by play time.
     */
    public List<PlayerFile> getTopByPlayTime(int limit) {
        List<PlayerFile> players = storage.getPlayersByPlayTime();
        return players.subList(0, Math.min(limit, players.size()));
    }

    /**
     * Get top players by wallet balance.
     */
    public List<PlayerFile> getTopByBalance(int limit) {
        List<PlayerFile> players = storage.getPlayersByWallet();
        return players.subList(0, Math.min(limit, players.size()));
    }

    /**
     * Format play time as human-readable string.
     */
    public static String formatPlayTime(long seconds) {
        if (seconds < 60) {
            return seconds + "s";
        }
        
        long minutes = seconds / 60;
        if (minutes < 60) {
            return minutes + "m";
        }
        
        long hours = minutes / 60;
        minutes = minutes % 60;
        if (hours < 24) {
            return hours + "h " + minutes + "m";
        }
        
        long days = hours / 24;
        hours = hours % 24;
        return days + "d " + hours + "h";
    }

    /**
     * Start periodic play time flushing to protect against crash data loss.
     * Snapshots each online player's session time, persists it, and resets the session timer.
     */
    public void startPeriodicSave() {
        stopPeriodicSave();
        
        int intervalMinutes = configManager.getConfig().playTimeRewards.periodicSaveMinutes;
        if (intervalMinutes <= 0) {
            return;
        }
        
        periodicSaveScheduler = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "EliteEssentials-PeriodicPlayTimeSave");
            t.setDaemon(true);
            return t;
        });
        
        periodicSaveScheduler.scheduleAtFixedRate(this::flushPlayTime,
                intervalMinutes, intervalMinutes, TimeUnit.MINUTES);
        
        logger.info("Periodic play time save started (every " + intervalMinutes + " minutes)");
    }
    
    /**
     * Stop the periodic save scheduler.
     */
    public void stopPeriodicSave() {
        if (periodicSaveScheduler != null) {
            periodicSaveScheduler.shutdown();
            try {
                if (!periodicSaveScheduler.awaitTermination(5, TimeUnit.SECONDS)) {
                    periodicSaveScheduler.shutdownNow();
                }
            } catch (InterruptedException e) {
                periodicSaveScheduler.shutdownNow();
                Thread.currentThread().interrupt();
            }
            periodicSaveScheduler = null;
        }
    }
    
    /**
     * Flush accumulated session play time for all online players to disk.
     * Resets session start times so the next flush only captures the delta.
     */
    private void flushPlayTime() {
        try {
            long now = System.currentTimeMillis();
            int flushed = 0;
            
            for (Map.Entry<UUID, Long> entry : sessionStartTimes.entrySet()) {
                UUID playerId = entry.getKey();
                long sessionStart = entry.getValue();
                long sessionSeconds = (now - sessionStart) / 1000;
                
                if (sessionSeconds <= 0) {
                    continue;
                }
                
                PlayerFile data = storage.getPlayer(playerId);
                if (data != null) {
                    data.addPlayTime(sessionSeconds);
                    data.updateLastSeen();
                    storage.saveAndMarkDirty(playerId);
                    flushed++;
                }
                
                // Reset session start to now so next flush only captures the delta
                entry.setValue(now);
            }
            
            // Keep reward service session tracking in sync to prevent double-counting
            if (playTimeRewardService != null) {
                playTimeRewardService.resetSessionStarts(now);
            }
            
            if (configManager.isDebugEnabled()) {
                logger.info("[PeriodicSave] Flushed play time for " + flushed + " online player(s)");
            }
        } catch (Exception e) {
            logger.warning("Error during periodic play time save: " + e.getMessage());
        }
    }

    /**
     * Save all player data.
     */
    public void save() {
        storage.saveAll();
    }

    /**
     * Reload player data from disk.
     */
    public void reload() {
        storage.reload();
    }
    
    /**
     * Get the underlying storage (for migration and advanced operations).
     */
    public PlayerFileStorage getStorage() {
        return storage;
    }
    
    /**
     * Get the player's default group chat preference.
     */
    public String getDefaultGroupChat(UUID playerId) {
        PlayerFile playerFile = storage.getPlayer(playerId);
        return playerFile != null ? playerFile.getDefaultGroupChat() : null;
    }
    
    /**
     * Set the player's default group chat preference.
     */
    public void setDefaultGroupChat(UUID playerId, String chatName) {
        PlayerFile playerFile = storage.getPlayer(playerId);
        if (playerFile != null) {
            playerFile.setDefaultGroupChat(chatName);
            storage.markDirty(playerId);
        }
    }
    
    /**
     * Notify player of balance changes.
     * Supports chat notifications and tooltip notifications.
     * 
     * @param playerId Player UUID
     * @param oldBalance Previous balance
     * @param newBalance New balance
     * @param playerRef Player reference for sending messages (null if not available)
     * @param senderName Name of the sender (player name or "Server" for console)
     */
    public void notifyBalanceChange(UUID playerId, double oldBalance, double newBalance, PlayerRef playerRef, String senderName) {
        String notifyMode = configManager.getConfig().economy.playerBalanceChangeNotify;
        
        if ("none".equals(notifyMode)) {
            return;
        }
        
        // Get player data
        PlayerFile playerData = storage.getPlayer(playerId);
        if (playerData == null) {
            return;
        }
        
        String playerName = playerData.getName();
        String currencyName = configManager.getConfig().economy.currencyNamePlural;
        String currencySymbol = configManager.getConfig().economy.currencySymbol;
        
        // Format amounts
        String oldFormatted = String.format("%s%.2f", currencySymbol, oldBalance);
        String newFormatted = String.format("%s%.2f", currencySymbol, newBalance);
        double diff = newBalance - oldBalance;
        String diffFormatted = String.format("%s%.2f", currencySymbol, Math.abs(diff));
        
        // Determine change type
        String changeType = diff > 0 ? "added" : "removed";
        String changeColor = diff > 0 ? "#55FF55" : "#FF5555";
        
        if ("chat".equals(notifyMode) || "chat_global".equals(notifyMode)) {
            boolean broadcastToAll = "chat_global".equals(notifyMode) 
                || configManager.getConfig().economy.playerBalanceChangeNotifyGlobal;
            
            String targetName = broadcastToAll ? playerName + "'s" : "your";
            String message = configManager.getMessage("balanceChangeNotify",
                "sender", senderName != null ? senderName : "Unknown",
                "target", targetName,
                "oldBalance", oldFormatted,
                "newBalance", newFormatted,
                "amount", diffFormatted,
                "changeType", changeType,
                "currency", currencyName);
            
            if (broadcastToAll) {
                // Broadcast notification to ALL players on server
                for (PlayerRef p : com.hypixel.hytale.server.core.universe.Universe.get().getPlayers()) {
                    p.sendMessage(MessageFormatter.formatWithFallback(message, changeColor));
                }
            } else {
                // Send chat notification to affected player only
                if (playerRef != null && playerRef.isValid()) {
                    playerRef.sendMessage(MessageFormatter.formatWithFallback(message, changeColor));
                }
            }
        } else if ("tooltip".equals(notifyMode)) {
            // Tooltip notification - store in player data for UI to read
            // The UI system can read this and display it in the tooltip
            playerData.setBalanceChangeNotification(oldBalance, newBalance, diff);
            storage.markDirty(playerId);
        }
    }
    
    /**
     * Notify player of balance changes (convenience method without sender name).
     * Uses "Server" as sender when playerRef is null (console), player name otherwise.
     */
    public void notifyBalanceChange(UUID playerId, double oldBalance, double newBalance, PlayerRef playerRef) {
        String senderName = playerRef != null && playerRef.isValid() ? playerRef.getUsername() : "Server";
        notifyBalanceChange(playerId, oldBalance, newBalance, playerRef, senderName);
    }
}
