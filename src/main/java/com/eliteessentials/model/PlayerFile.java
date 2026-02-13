package com.eliteessentials.model;

import java.util.*;

/**
 * Unified player data file stored as players/{uuid}.json.
 * Contains all per-player data: homes, back history, kit claims, economy, etc.
 */
public class PlayerFile {
    
    // Core identity
    private UUID uuid;
    private String name;
    
    // Timestamps
    private long firstJoin;
    private long lastSeen;
    
    // Economy & stats
    private long playTime;  // Total play time in seconds
    private double wallet;
    
    // Admin state
    private boolean vanished;  // Whether player is in vanish mode
    
    // Homes: name -> Home
    private Map<String, Home> homes = new LinkedHashMap<>();
    
    // Back location history (most recent first)
    private List<Location> backHistory = new ArrayList<>();
    
    // Kit claims (one-time kits that have been claimed)
    private Set<String> kitClaims = new HashSet<>();
    
    // Kit cooldowns: kitId -> last use timestamp
    private Map<String, Long> kitCooldowns = new HashMap<>();
    
    // Playtime reward claims
    private PlaytimeClaims playtimeClaims = new PlaytimeClaims();
    
    // Ignored players (UUIDs of players whose messages are hidden)
    private Set<UUID> ignoredPlayers = new HashSet<>();
    
    // Mail inbox
    private List<MailMessage> mailbox = new ArrayList<>();
    
    public PlayerFile() {
        // For Gson deserialization
    }
    
    public PlayerFile(UUID uuid, String name) {
        this.uuid = uuid;
        this.name = name;
        this.firstJoin = System.currentTimeMillis();
        this.lastSeen = System.currentTimeMillis();
        this.playTime = 0;
        this.wallet = 0.0;
        this.vanished = false;
    }
    
    // ==================== Core Identity ====================
    
    public UUID getUuid() {
        return uuid;
    }
    
    public void setUuid(UUID uuid) {
        this.uuid = uuid;
    }
    
    public String getName() {
        return name;
    }
    
    public void setName(String name) {
        this.name = name;
    }
    
    // ==================== Timestamps ====================
    
    public long getFirstJoin() {
        return firstJoin;
    }
    
    public void setFirstJoin(long firstJoin) {
        this.firstJoin = firstJoin;
    }
    
    public long getLastSeen() {
        return lastSeen;
    }
    
    public void setLastSeen(long lastSeen) {
        this.lastSeen = lastSeen;
    }
    
    public void updateLastSeen() {
        this.lastSeen = System.currentTimeMillis();
    }
    
    // ==================== Economy & Stats ====================
    
    public long getPlayTime() {
        return playTime;
    }
    
    public void setPlayTime(long playTime) {
        this.playTime = playTime;
    }
    
    public void addPlayTime(long seconds) {
        this.playTime += seconds;
    }
    
    public double getWallet() {
        return wallet;
    }
    
    public void setWallet(double wallet) {
        this.wallet = wallet;
    }
    
    public boolean modifyWallet(double amount) {
        double newBalance = this.wallet + amount;
        if (newBalance < 0) {
            return false;
        }
        this.wallet = newBalance;
        return true;
    }
    
    // ==================== Admin State ====================
    
    public boolean isVanished() {
        return vanished;
    }
    
    public void setVanished(boolean vanished) {
        this.vanished = vanished;
    }
    
    // ==================== Homes ====================
    
    public Map<String, Home> getHomes() {
        return homes;
    }
    
    public void setHomes(Map<String, Home> homes) {
        this.homes = homes != null ? homes : new LinkedHashMap<>();
    }
    
    public Optional<Home> getHome(String name) {
        return Optional.ofNullable(homes.get(name.toLowerCase()));
    }
    
    public void setHome(Home home) {
        homes.put(home.getName().toLowerCase(), home);
    }
    
    public boolean deleteHome(String name) {
        return homes.remove(name.toLowerCase()) != null;
    }
    
    public boolean hasHome(String name) {
        return homes.containsKey(name.toLowerCase());
    }
    
    public int getHomeCount() {
        return homes.size();
    }
    
    public Set<String> getHomeNames() {
        return new HashSet<>(homes.keySet());
    }
    
    // ==================== Back History ====================
    
    public List<Location> getBackHistory() {
        return backHistory;
    }
    
    public void setBackHistory(List<Location> backHistory) {
        this.backHistory = backHistory != null ? backHistory : new ArrayList<>();
    }
    
    public void pushBackLocation(Location location, int maxHistory) {
        if (location == null) return;
        backHistory.add(0, location.clone());
        while (backHistory.size() > maxHistory) {
            backHistory.remove(backHistory.size() - 1);
        }
    }
    
    public Optional<Location> peekBackLocation() {
        if (backHistory.isEmpty()) return Optional.empty();
        return Optional.of(backHistory.get(0).clone());
    }
    
    public Optional<Location> popBackLocation() {
        if (backHistory.isEmpty()) return Optional.empty();
        return Optional.of(backHistory.remove(0));
    }
    
    public int getBackHistorySize() {
        return backHistory.size();
    }
    
    public void clearBackHistory() {
        backHistory.clear();
    }
    
    // ==================== Kit Claims ====================
    
    public Set<String> getKitClaims() {
        return kitClaims;
    }
    
    public void setKitClaims(Set<String> kitClaims) {
        this.kitClaims = kitClaims != null ? kitClaims : new HashSet<>();
    }
    
    public boolean hasClaimedKit(String kitId) {
        return kitClaims.contains(kitId.toLowerCase());
    }
    
    public void claimKit(String kitId) {
        kitClaims.add(kitId.toLowerCase());
    }
    
    // ==================== Kit Cooldowns ====================
    
    public Map<String, Long> getKitCooldowns() {
        return kitCooldowns;
    }
    
    public void setKitCooldowns(Map<String, Long> kitCooldowns) {
        this.kitCooldowns = kitCooldowns != null ? kitCooldowns : new HashMap<>();
    }
    
    public long getKitLastUsed(String kitId) {
        return kitCooldowns.getOrDefault(kitId.toLowerCase(), 0L);
    }
    
    public void setKitUsed(String kitId) {
        kitCooldowns.put(kitId.toLowerCase(), System.currentTimeMillis());
    }
    
    public void clearKitCooldowns() {
        kitCooldowns.clear();
    }
    
    // ==================== Playtime Claims ====================
    
    public PlaytimeClaims getPlaytimeClaims() {
        if (playtimeClaims == null) {
            playtimeClaims = new PlaytimeClaims();
        }
        return playtimeClaims;
    }
    
    public void setPlaytimeClaims(PlaytimeClaims playtimeClaims) {
        this.playtimeClaims = playtimeClaims != null ? playtimeClaims : new PlaytimeClaims();
    }
    
    public boolean hasClaimedMilestone(String rewardId) {
        return getPlaytimeClaims().claimedMilestones.contains(rewardId);
    }
    
    public void claimMilestone(String rewardId) {
        getPlaytimeClaims().claimedMilestones.add(rewardId);
    }
    
    public int getRepeatableClaimCount(String rewardId) {
        return getPlaytimeClaims().repeatableCounts.getOrDefault(rewardId, 0);
    }
    
    public void incrementRepeatableClaim(String rewardId) {
        PlaytimeClaims claims = getPlaytimeClaims();
        int current = claims.repeatableCounts.getOrDefault(rewardId, 0);
        claims.repeatableCounts.put(rewardId, current + 1);
    }
    
    // ==================== Mailbox ====================
    
    public List<MailMessage> getMailbox() {
        if (mailbox == null) {
            mailbox = new ArrayList<>();
        }
        return mailbox;
    }
    
    public void setMailbox(List<MailMessage> mailbox) {
        this.mailbox = mailbox != null ? mailbox : new ArrayList<>();
    }
    
    public void addMail(MailMessage mail) {
        getMailbox().add(0, mail); // Add to front (newest first)
    }
    
    public int getUnreadMailCount() {
        return (int) getMailbox().stream().filter(m -> !m.isRead()).count();
    }
    
    public void clearMailbox() {
        getMailbox().clear();
    }
    
    // ==================== Ignored Players ====================
    
    public Set<UUID> getIgnoredPlayers() {
        if (ignoredPlayers == null) {
            ignoredPlayers = new HashSet<>();
        }
        return ignoredPlayers;
    }
    
    public void setIgnoredPlayers(Set<UUID> ignoredPlayers) {
        this.ignoredPlayers = ignoredPlayers != null ? ignoredPlayers : new HashSet<>();
    }
    
    public boolean isIgnoring(UUID playerId) {
        return getIgnoredPlayers().contains(playerId);
    }
    
    public boolean addIgnored(UUID playerId) {
        return getIgnoredPlayers().add(playerId);
    }
    
    public boolean removeIgnored(UUID playerId) {
        return getIgnoredPlayers().remove(playerId);
    }
    
    public void clearIgnored() {
        getIgnoredPlayers().clear();
    }
    
    public int getIgnoredCount() {
        return getIgnoredPlayers().size();
    }
    
    // ==================== Inner Classes ====================
    
    /**
     * Tracks playtime reward claims.
     */
    public static class PlaytimeClaims {
        public Set<String> claimedMilestones = new HashSet<>();
        public Map<String, Integer> repeatableCounts = new HashMap<>();
    }
}
