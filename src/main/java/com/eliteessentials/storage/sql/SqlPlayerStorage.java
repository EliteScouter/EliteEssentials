package com.eliteessentials.storage.sql;

import com.eliteessentials.model.*;
import com.eliteessentials.storage.PlayerStorageProvider;
import com.zaxxer.hikari.HikariDataSource;

import java.io.File;
import java.sql.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.logging.Logger;

/**
 * SQL-backed implementation of {@link PlayerStorageProvider}.
 * Maintains an in-memory cache of online players (matching the JSON storage behavior)
 * and flushes dirty entries to the database on a background thread.
 */
public class SqlPlayerStorage implements PlayerStorageProvider {

    private static final Logger logger = Logger.getLogger("EliteEssentials");

    private final HikariDataSource dataSource;
    private final String prefix;
    private final boolean mysqlMode;

    // In-memory cache (online players)
    private final ConcurrentHashMap<UUID, PlayerFile> cache = new ConcurrentHashMap<>();
    // Lowercase name -> UUID index
    private final ConcurrentHashMap<String, UUID> nameIndex = new ConcurrentHashMap<>();
    // Dirty tracking
    private final Set<UUID> dirtyPlayers = ConcurrentHashMap.newKeySet();

    // Background flush executor
    private final ScheduledExecutorService flushExecutor;

    public SqlPlayerStorage(HikariDataSource dataSource, String tablePrefix, boolean mysqlMode) {
        this.dataSource = dataSource;
        this.prefix = tablePrefix;
        this.mysqlMode = mysqlMode;

        // Load name index from DB on startup
        loadNameIndex();

        // Periodic flush every 60 seconds
        this.flushExecutor = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "EliteEssentials-SqlFlush");
            t.setDaemon(true);
            return t;
        });
        flushExecutor.scheduleAtFixedRate(this::saveAllDirty, 60, 60, TimeUnit.SECONDS);
    }

    // ==================== Core player access ====================

    @Override
    public PlayerFile getPlayer(UUID uuid, String name) {
        // In MySQL mode (multi-server), always load fresh from DB on player join
        // to avoid serving stale cached data from another server's writes.
        if (mysqlMode) {
            PlayerFile data = loadFromDb(uuid);
            if (data != null) {
                if (name != null && !name.equals(data.getName())) {
                    data.setName(name);
                    markDirty(uuid);
                }
                cache.put(uuid, data);
                updateNameIndex(uuid, data.getName());
                return data;
            }

            // Create new
            data = new PlayerFile(uuid, name);
            cache.put(uuid, data);
            updateNameIndex(uuid, name);
            markDirty(uuid);
            return data;
        }

        // H2 / single-server mode: use cache-first approach
        PlayerFile data = cache.get(uuid);
        if (data != null) {
            if (name != null && !name.equals(data.getName())) {
                data.setName(name);
                markDirty(uuid);
            }
            return data;
        }

        // Load from DB
        data = loadFromDb(uuid);
        if (data != null) {
            if (name != null && !name.equals(data.getName())) {
                data.setName(name);
                markDirty(uuid);
            }
            cache.put(uuid, data);
            updateNameIndex(uuid, data.getName());
            return data;
        }

        // Create new
        data = new PlayerFile(uuid, name);
        cache.put(uuid, data);
        updateNameIndex(uuid, name);
        markDirty(uuid);
        return data;
    }

    @Override
    public PlayerFile getPlayer(UUID uuid) {
        PlayerFile data = cache.get(uuid);
        if (data != null) return data;

        data = loadFromDb(uuid);
        if (data != null) {
            cache.put(uuid, data);
            return data;
        }
        return null;
    }

    @Override
    public PlayerFile getPlayerByName(String name) {
        UUID uuid = getUuidByName(name).orElse(null);
        if (uuid == null) return null;
        return getPlayer(uuid);
    }

    @Override
    public Optional<UUID> getUuidByName(String name) {
        String lower = name.toLowerCase();
        // Exact match first (in-memory index)
        UUID uuid = nameIndex.get(lower);
        if (uuid != null) return Optional.of(uuid);
        // Exact match from DB
        uuid = lookupUuidByName(name);
        if (uuid != null) return Optional.of(uuid);
        // Fallback: starts-with partial match (matches online NameMatching.DEFAULT behavior)
        for (Map.Entry<String, UUID> entry : nameIndex.entrySet()) {
            if (entry.getKey().startsWith(lower)) {
                return Optional.of(entry.getValue());
            }
        }
        return Optional.empty();
    }

    @Override
    public boolean hasPlayer(UUID uuid) {
        if (cache.containsKey(uuid)) return true;
        String sql = "SELECT 1 FROM " + prefix + "players WHERE uuid = ?";
        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, uuid.toString());
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next();
            }
        } catch (SQLException e) {
            logger.severe("[SqlPlayerStorage] hasPlayer failed for " + uuid + ": " + e.getMessage());
            return false;
        }
    }

    // ==================== Persistence ====================

    @Override
    public void savePlayer(UUID uuid) {
        PlayerFile data = cache.get(uuid);
        if (data == null) return;
        flushExecutor.submit(() -> writePlayerToDb(data));
        dirtyPlayers.remove(uuid);
    }

    @Override
    public void saveAndMarkDirty(UUID uuid) {
        markDirty(uuid);
        savePlayer(uuid);
    }

    @Override
    public void markDirty(UUID uuid) {
        dirtyPlayers.add(uuid);
    }

    @Override
    public void saveAll() {
        for (PlayerFile data : cache.values()) {
            writePlayerToDb(data);
        }
        dirtyPlayers.clear();
    }

    @Override
    public void saveAllDirty() {
        Set<UUID> snapshot = new HashSet<>(dirtyPlayers);
        for (UUID uuid : snapshot) {
            PlayerFile data = cache.get(uuid);
            if (data != null) {
                writePlayerToDb(data);
                dirtyPlayers.remove(uuid);
            }
        }
    }

    // ==================== Cache lifecycle ====================

    @Override
    public void unloadPlayer(UUID uuid) {
        PlayerFile data = cache.get(uuid);
        if (data != null) {
            writePlayerToDb(data);
            dirtyPlayers.remove(uuid);
        }
        cache.remove(uuid);
    }

    @Override
    public Collection<PlayerFile> getCachedPlayers() {
        return Collections.unmodifiableCollection(cache.values());
    }

    // ==================== Queries ====================

    @Override
    public Collection<UUID> getAllPlayerUuids() {
        Set<UUID> uuids = new HashSet<>();
        String sql = "SELECT uuid FROM " + prefix + "players";
        try (Connection conn = dataSource.getConnection();
             Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery(sql)) {
            while (rs.next()) {
                uuids.add(UUID.fromString(rs.getString("uuid")));
            }
        } catch (SQLException e) {
            logger.severe("[SqlPlayerStorage] getAllPlayerUuids failed: " + e.getMessage());
        }
        return uuids;
    }

    @Override
    public List<PlayerFile> getAllPlayersSorted(Comparator<PlayerFile> comparator) {
        List<PlayerFile> all = new ArrayList<>();
        for (UUID uuid : getAllPlayerUuids()) {
            PlayerFile pf = getPlayer(uuid);
            if (pf != null) all.add(pf);
        }
        all.sort(comparator);
        return all;
    }

    @Override
    public List<PlayerFile> getPlayersByWallet() {
        return getAllPlayersSorted(Comparator.comparingDouble(PlayerFile::getWallet).reversed());
    }

    @Override
    public List<PlayerFile> getPlayersByPlayTime() {
        return getAllPlayersSorted(Comparator.comparingLong(PlayerFile::getPlayTime).reversed());
    }

    @Override
    public List<PlayerFile> getPlayersByLastSeen() {
        return getAllPlayersSorted(Comparator.comparingLong(PlayerFile::getLastSeen).reversed());
    }

    @Override
    public int getPlayerCount() {
        String sql = "SELECT COUNT(*) FROM " + prefix + "players";
        try (Connection conn = dataSource.getConnection();
             Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery(sql)) {
            if (rs.next()) return rs.getInt(1);
        } catch (SQLException e) {
            logger.severe("[SqlPlayerStorage] getPlayerCount failed: " + e.getMessage());
        }
        return 0;
    }

    // ==================== Index / Reload ====================

    @Override
    public void reload() {
        // Clear in-memory cache so next access re-reads from DB.
        // This is important after migration — cached players may have stale data.
        cache.clear();
        loadNameIndex();
    }

    // ==================== Migration support ====================

    @Override
    public void savePlayerDirect(PlayerFile data) {
        if (data == null || data.getUuid() == null) return;
        writePlayerToDb(data);
        if (data.getName() != null) {
            updateNameIndex(data.getUuid(), data.getName());
        }
    }

    @Override
    public File getPlayersFolder() {
        // Not applicable for SQL storage, but required by interface for migration
        return null;
    }

    // ==================== Shutdown ====================

    /**
     * Flush all pending writes and shut down the background executor.
     * Called by StorageFactory during plugin disable.
     */
    public void shutdown() {
        flushExecutor.shutdown();
        saveAllDirty();
        try {
            if (!flushExecutor.awaitTermination(10, TimeUnit.SECONDS)) {
                flushExecutor.shutdownNow();
            }
        } catch (InterruptedException e) {
            flushExecutor.shutdownNow();
            Thread.currentThread().interrupt();
        }
    }

    // ==================== Private DB operations ====================

    private void loadNameIndex() {
        nameIndex.clear();
        String sql = "SELECT uuid, name FROM " + prefix + "players";
        try (Connection conn = dataSource.getConnection();
             Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery(sql)) {
            while (rs.next()) {
                UUID uuid = UUID.fromString(rs.getString("uuid"));
                String name = rs.getString("name");
                if (name != null) {
                    nameIndex.put(name.toLowerCase(), uuid);
                }
            }
            logger.info("[SqlPlayerStorage] Loaded name index with " + nameIndex.size() + " entries.");
        } catch (SQLException e) {
            logger.severe("[SqlPlayerStorage] Failed to load name index: " + e.getMessage());
        }
    }

    private void updateNameIndex(UUID uuid, String name) {
        if (name != null) {
            nameIndex.put(name.toLowerCase(), uuid);
        }
    }

    private UUID lookupUuidByName(String name) {
        String sql = "SELECT uuid FROM " + prefix + "players WHERE LOWER(name) = ?";
        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, name.toLowerCase());
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    UUID uuid = UUID.fromString(rs.getString("uuid"));
                    updateNameIndex(uuid, name);
                    return uuid;
                }
            }
        } catch (SQLException e) {
            logger.severe("[SqlPlayerStorage] lookupUuidByName failed for '" + name + "': " + e.getMessage());
        }
        return null;
    }

    /**
     * Load a full PlayerFile from the database (all normalized tables).
     */
    private PlayerFile loadFromDb(UUID uuid) {
        try (Connection conn = dataSource.getConnection()) {
            // Load core player row
            PlayerFile pf = loadPlayerRow(conn, uuid);
            if (pf == null) return null;

            loadHomes(conn, pf);
            loadBackHistory(conn, pf);
            loadKitClaims(conn, pf);
            loadKitCooldowns(conn, pf);
            loadPlaytimeClaims(conn, pf);
            loadIgnoredPlayers(conn, pf);
            loadMailbox(conn, pf);
            loadIpHistory(conn, pf);
            loadBalanceNotification(conn, pf);

            return pf;
        } catch (SQLException e) {
            logger.severe("[SqlPlayerStorage] Failed to load player " + uuid + ": " + e.getMessage());
            return null;
        }
    }

    private PlayerFile loadPlayerRow(Connection conn, UUID uuid) throws SQLException {
        String sql = "SELECT * FROM " + prefix + "players WHERE uuid = ?";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, uuid.toString());
            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) return null;
                PlayerFile pf = new PlayerFile();
                pf.setUuid(UUID.fromString(rs.getString("uuid")));
                pf.setName(rs.getString("name"));
                pf.setNickname(rs.getString("nickname"));
                pf.setFirstJoin(rs.getLong("first_join"));
                pf.setLastSeen(rs.getLong("last_seen"));
                pf.setPlayTime(rs.getLong("play_time"));
                pf.setWallet(rs.getDouble("wallet"));
                pf.setVanished(rs.getBoolean("vanished"));
                pf.setDefaultGroupChat(rs.getString("default_group_chat"));
                return pf;
            }
        }
    }

    private void loadHomes(Connection conn, PlayerFile pf) throws SQLException {
        String sql = "SELECT * FROM " + prefix + "homes WHERE uuid = ?";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, pf.getUuid().toString());
            try (ResultSet rs = ps.executeQuery()) {
                Map<String, Home> homes = new LinkedHashMap<>();
                while (rs.next()) {
                    Home home = new Home();
                    home.setName(rs.getString("name"));
                    home.setLocation(new Location(
                            rs.getString("world"),
                            rs.getDouble("x"), rs.getDouble("y"), rs.getDouble("z"),
                            rs.getFloat("yaw"), rs.getFloat("pitch")
                    ));
                    home.setCreatedAt(rs.getLong("created_at"));
                    homes.put(home.getName(), home);
                }
                pf.setHomes(homes);
            }
        }
    }

    private void loadBackHistory(Connection conn, PlayerFile pf) throws SQLException {
        String sql = "SELECT * FROM " + prefix + "back_history WHERE uuid = ? ORDER BY position ASC";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, pf.getUuid().toString());
            try (ResultSet rs = ps.executeQuery()) {
                List<Location> history = new ArrayList<>();
                while (rs.next()) {
                    history.add(new Location(
                            rs.getString("world"),
                            rs.getDouble("x"), rs.getDouble("y"), rs.getDouble("z"),
                            rs.getFloat("yaw"), rs.getFloat("pitch")
                    ));
                }
                pf.setBackHistory(history);
            }
        }
    }

    private void loadKitClaims(Connection conn, PlayerFile pf) throws SQLException {
        String sql = "SELECT kit_id FROM " + prefix + "kit_claims WHERE uuid = ?";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, pf.getUuid().toString());
            try (ResultSet rs = ps.executeQuery()) {
                Set<String> claims = new HashSet<>();
                while (rs.next()) {
                    claims.add(rs.getString("kit_id"));
                }
                pf.setKitClaims(claims);
            }
        }
    }

    private void loadKitCooldowns(Connection conn, PlayerFile pf) throws SQLException {
        String sql = "SELECT kit_id, last_used FROM " + prefix + "kit_cooldowns WHERE uuid = ?";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, pf.getUuid().toString());
            try (ResultSet rs = ps.executeQuery()) {
                Map<String, Long> cooldowns = new HashMap<>();
                while (rs.next()) {
                    cooldowns.put(rs.getString("kit_id"), rs.getLong("last_used"));
                }
                pf.setKitCooldowns(cooldowns);
            }
        }
    }

    private void loadPlaytimeClaims(Connection conn, PlayerFile pf) throws SQLException {
        String sql = "SELECT reward_id, type, count FROM " + prefix + "playtime_claims WHERE uuid = ?";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, pf.getUuid().toString());
            try (ResultSet rs = ps.executeQuery()) {
                PlayerFile.PlaytimeClaims claims = new PlayerFile.PlaytimeClaims();
                while (rs.next()) {
                    String rewardId = rs.getString("reward_id");
                    String type = rs.getString("type");
                    int count = rs.getInt("count");
                    if ("milestone".equals(type)) {
                        claims.claimedMilestones.add(rewardId);
                    } else if ("repeatable".equals(type)) {
                        claims.repeatableCounts.put(rewardId, count);
                    }
                }
                pf.setPlaytimeClaims(claims);
            }
        }
    }

    private void loadIgnoredPlayers(Connection conn, PlayerFile pf) throws SQLException {
        String sql = "SELECT ignored_uuid FROM " + prefix + "ignored_players WHERE uuid = ?";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, pf.getUuid().toString());
            try (ResultSet rs = ps.executeQuery()) {
                Set<UUID> ignored = new HashSet<>();
                while (rs.next()) {
                    ignored.add(UUID.fromString(rs.getString("ignored_uuid")));
                }
                pf.setIgnoredPlayers(ignored);
            }
        }
    }

    private void loadMailbox(Connection conn, PlayerFile pf) throws SQLException {
        String sql = "SELECT * FROM " + prefix + "mailbox WHERE uuid = ? ORDER BY sent_at ASC";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, pf.getUuid().toString());
            try (ResultSet rs = ps.executeQuery()) {
                List<MailMessage> mail = new ArrayList<>();
                while (rs.next()) {
                    MailMessage msg = new MailMessage();
                    msg.setId(rs.getString("message_id"));
                    String senderUuidStr = rs.getString("sender_uuid");
                    if (senderUuidStr != null) {
                        msg.setSenderUuid(UUID.fromString(senderUuidStr));
                    }
                    msg.setSenderName(rs.getString("sender"));
                    msg.setMessage(rs.getString("message"));
                    msg.setTimestamp(rs.getLong("sent_at"));
                    msg.setRead(rs.getBoolean("is_read"));
                    mail.add(msg);
                }
                pf.setMailbox(mail);
            }
        }
    }

    private void loadIpHistory(Connection conn, PlayerFile pf) throws SQLException {
        String sql = "SELECT ip, last_used FROM " + prefix + "ip_history WHERE uuid = ? ORDER BY last_used DESC";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, pf.getUuid().toString());
            try (ResultSet rs = ps.executeQuery()) {
                List<PlayerFile.IpHistoryEntry> entries = new ArrayList<>();
                while (rs.next()) {
                    entries.add(new PlayerFile.IpHistoryEntry(
                            rs.getString("ip"), rs.getLong("last_used")
                    ));
                }
                pf.setIpHistory(entries);
            }
        }
    }

    private void loadBalanceNotification(Connection conn, PlayerFile pf) throws SQLException {
        String sql = "SELECT old_balance, new_balance, diff FROM " + prefix + "balance_notifications WHERE uuid = ?";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, pf.getUuid().toString());
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    pf.setBalanceChangeNotification(
                            rs.getDouble("old_balance"),
                            rs.getDouble("new_balance"),
                            rs.getDouble("diff")
                    );
                }
            }
        }
    }

    // ==================== Write operations ====================

    /**
     * Upsert a full PlayerFile across all normalized tables within a single transaction.
     */
    private void writePlayerToDb(PlayerFile pf) {
        if (pf == null || pf.getUuid() == null) return;
        String uuidStr = pf.getUuid().toString();

        try (Connection conn = dataSource.getConnection()) {
            boolean originalAutoCommit = conn.getAutoCommit();
            try {
                conn.setAutoCommit(false);

                upsertPlayerRow(conn, pf, uuidStr);
                replaceHomes(conn, pf, uuidStr);
                replaceBackHistory(conn, pf, uuidStr);
                replaceKitClaims(conn, pf, uuidStr);
                replaceKitCooldowns(conn, pf, uuidStr);
                replacePlaytimeClaims(conn, pf, uuidStr);
                replaceIgnoredPlayers(conn, pf, uuidStr);
                replaceMailbox(conn, pf, uuidStr);
                replaceIpHistory(conn, pf, uuidStr);
                replaceBalanceNotification(conn, pf, uuidStr);

                conn.commit();
            } catch (SQLException e) {
                conn.rollback();
                logger.severe("[SqlPlayerStorage] Failed to save player " + uuidStr + ": " + e.getMessage());
            } finally {
                conn.setAutoCommit(originalAutoCommit);
            }
        } catch (SQLException e) {
            logger.severe("[SqlPlayerStorage] Failed to get connection for save " + uuidStr + ": " + e.getMessage());
        }
    }

    private void upsertPlayerRow(Connection conn, PlayerFile pf, String uuidStr) throws SQLException {
        String sql;
        if (mysqlMode) {
            sql = "INSERT INTO " + prefix + "players (uuid, name, nickname, first_join, last_seen, play_time, wallet, vanished, default_group_chat) "
                    + "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?) "
                    + "ON DUPLICATE KEY UPDATE name=VALUES(name), nickname=VALUES(nickname), first_join=VALUES(first_join), "
                    + "last_seen=VALUES(last_seen), play_time=VALUES(play_time), wallet=VALUES(wallet), "
                    + "vanished=VALUES(vanished), default_group_chat=VALUES(default_group_chat)";
        } else {
            sql = "MERGE INTO " + prefix + "players (uuid, name, nickname, first_join, last_seen, play_time, wallet, vanished, default_group_chat) "
                    + "KEY (uuid) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)";
        }
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, uuidStr);
            ps.setString(2, pf.getName());
            ps.setString(3, pf.getNickname());
            ps.setLong(4, pf.getFirstJoin());
            ps.setLong(5, pf.getLastSeen());
            ps.setLong(6, pf.getPlayTime());
            ps.setDouble(7, pf.getWallet());
            ps.setBoolean(8, pf.isVanished());
            ps.setString(9, pf.getDefaultGroupChat());
            ps.executeUpdate();
        }
    }

    private void replaceHomes(Connection conn, PlayerFile pf, String uuidStr) throws SQLException {
        deleteByUuid(conn, prefix + "homes", uuidStr);
        if (pf.getHomes().isEmpty()) return;
        String sql = "INSERT INTO " + prefix + "homes (uuid, name, world, x, y, z, yaw, pitch, created_at) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            for (Home home : pf.getHomes().values()) {
                Location loc = home.getLocation();
                ps.setString(1, uuidStr);
                ps.setString(2, home.getName());
                ps.setString(3, loc.getWorld());
                ps.setDouble(4, loc.getX());
                ps.setDouble(5, loc.getY());
                ps.setDouble(6, loc.getZ());
                ps.setFloat(7, loc.getYaw());
                ps.setFloat(8, loc.getPitch());
                ps.setLong(9, home.getCreatedAt());
                ps.addBatch();
            }
            ps.executeBatch();
        }
    }

    private void replaceBackHistory(Connection conn, PlayerFile pf, String uuidStr) throws SQLException {
        deleteByUuid(conn, prefix + "back_history", uuidStr);
        List<Location> history = pf.getBackHistory();
        if (history.isEmpty()) return;
        String sql = "INSERT INTO " + prefix + "back_history (uuid, position, world, x, y, z, yaw, pitch) VALUES (?, ?, ?, ?, ?, ?, ?, ?)";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            for (int i = 0; i < history.size(); i++) {
                Location loc = history.get(i);
                ps.setString(1, uuidStr);
                ps.setInt(2, i);
                ps.setString(3, loc.getWorld());
                ps.setDouble(4, loc.getX());
                ps.setDouble(5, loc.getY());
                ps.setDouble(6, loc.getZ());
                ps.setFloat(7, loc.getYaw());
                ps.setFloat(8, loc.getPitch());
                ps.addBatch();
            }
            ps.executeBatch();
        }
    }

    private void replaceKitClaims(Connection conn, PlayerFile pf, String uuidStr) throws SQLException {
        deleteByUuid(conn, prefix + "kit_claims", uuidStr);
        if (pf.getKitClaims().isEmpty()) return;
        String sql = "INSERT INTO " + prefix + "kit_claims (uuid, kit_id) VALUES (?, ?)";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            for (String kitId : pf.getKitClaims()) {
                ps.setString(1, uuidStr);
                ps.setString(2, kitId);
                ps.addBatch();
            }
            ps.executeBatch();
        }
    }

    private void replaceKitCooldowns(Connection conn, PlayerFile pf, String uuidStr) throws SQLException {
        deleteByUuid(conn, prefix + "kit_cooldowns", uuidStr);
        if (pf.getKitCooldowns().isEmpty()) return;
        String sql = "INSERT INTO " + prefix + "kit_cooldowns (uuid, kit_id, last_used) VALUES (?, ?, ?)";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            for (Map.Entry<String, Long> entry : pf.getKitCooldowns().entrySet()) {
                ps.setString(1, uuidStr);
                ps.setString(2, entry.getKey());
                ps.setLong(3, entry.getValue());
                ps.addBatch();
            }
            ps.executeBatch();
        }
    }

    private void replacePlaytimeClaims(Connection conn, PlayerFile pf, String uuidStr) throws SQLException {
        deleteByUuid(conn, prefix + "playtime_claims", uuidStr);
        PlayerFile.PlaytimeClaims claims = pf.getPlaytimeClaims();
        if (claims == null) return;
        boolean hasData = (claims.claimedMilestones != null && !claims.claimedMilestones.isEmpty())
                || (claims.repeatableCounts != null && !claims.repeatableCounts.isEmpty());
        if (!hasData) return;

        String sql = "INSERT INTO " + prefix + "playtime_claims (uuid, reward_id, type, count) VALUES (?, ?, ?, ?)";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            if (claims.claimedMilestones != null) {
                for (String rewardId : claims.claimedMilestones) {
                    ps.setString(1, uuidStr);
                    ps.setString(2, rewardId);
                    ps.setString(3, "milestone");
                    ps.setInt(4, 1);
                    ps.addBatch();
                }
            }
            if (claims.repeatableCounts != null) {
                for (Map.Entry<String, Integer> entry : claims.repeatableCounts.entrySet()) {
                    ps.setString(1, uuidStr);
                    ps.setString(2, entry.getKey());
                    ps.setString(3, "repeatable");
                    ps.setInt(4, entry.getValue());
                    ps.addBatch();
                }
            }
            ps.executeBatch();
        }
    }

    private void replaceIgnoredPlayers(Connection conn, PlayerFile pf, String uuidStr) throws SQLException {
        deleteByUuid(conn, prefix + "ignored_players", uuidStr);
        if (pf.getIgnoredPlayers().isEmpty()) return;
        String sql = "INSERT INTO " + prefix + "ignored_players (uuid, ignored_uuid) VALUES (?, ?)";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            for (UUID ignored : pf.getIgnoredPlayers()) {
                ps.setString(1, uuidStr);
                ps.setString(2, ignored.toString());
                ps.addBatch();
            }
            ps.executeBatch();
        }
    }

    private void replaceMailbox(Connection conn, PlayerFile pf, String uuidStr) throws SQLException {
        deleteByUuid(conn, prefix + "mailbox", uuidStr);
        if (pf.getMailbox().isEmpty()) return;
        String sql = "INSERT INTO " + prefix + "mailbox (uuid, message_id, sender_uuid, sender, message, sent_at, is_read) VALUES (?, ?, ?, ?, ?, ?, ?)";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            for (MailMessage msg : pf.getMailbox()) {
                ps.setString(1, uuidStr);
                ps.setString(2, msg.getId());
                ps.setString(3, msg.getSenderUuid() != null ? msg.getSenderUuid().toString() : null);
                ps.setString(4, msg.getSenderName());
                ps.setString(5, msg.getMessage());
                ps.setLong(6, msg.getTimestamp());
                ps.setBoolean(7, msg.isRead());
                ps.addBatch();
            }
            ps.executeBatch();
        }
    }

    private void replaceIpHistory(Connection conn, PlayerFile pf, String uuidStr) throws SQLException {
        deleteByUuid(conn, prefix + "ip_history", uuidStr);
        if (pf.getIpHistory().isEmpty()) return;
        String sql = "INSERT INTO " + prefix + "ip_history (uuid, ip, last_used) VALUES (?, ?, ?)";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            for (PlayerFile.IpHistoryEntry entry : pf.getIpHistory()) {
                ps.setString(1, uuidStr);
                ps.setString(2, entry.ip);
                ps.setLong(3, entry.lastUsed);
                ps.addBatch();
            }
            ps.executeBatch();
        }
    }

    private void replaceBalanceNotification(Connection conn, PlayerFile pf, String uuidStr) throws SQLException {
        deleteByUuid(conn, prefix + "balance_notifications", uuidStr);
        PlayerFile.BalanceChangeNotification notif = pf.getBalanceChangeNotification();
        if (notif == null) return;
        String sql = "INSERT INTO " + prefix + "balance_notifications (uuid, old_balance, new_balance, diff) VALUES (?, ?, ?, ?)";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, uuidStr);
            ps.setDouble(2, notif.oldBalance);
            ps.setDouble(3, notif.newBalance);
            ps.setDouble(4, notif.diff);
            ps.executeUpdate();
        }
    }

    private void deleteByUuid(Connection conn, String table, String uuidStr) throws SQLException {
        String sql = "DELETE FROM " + table + " WHERE uuid = ?";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, uuidStr);
            ps.executeUpdate();
        }
    }
}
