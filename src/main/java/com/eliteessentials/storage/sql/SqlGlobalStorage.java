package com.eliteessentials.storage.sql;

import com.eliteessentials.model.Location;
import com.eliteessentials.model.Warp;
import com.eliteessentials.storage.GlobalStorageProvider;
import com.eliteessentials.storage.SpawnStorage;
import com.zaxxer.hikari.HikariDataSource;

import java.sql.*;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Logger;

/**
 * SQL-backed implementation of {@link GlobalStorageProvider}.
 * Handles warps, spawns, and first-join spawn persistence.
 * Maintains in-memory caches populated on {@link #load()}.
 */
public class SqlGlobalStorage implements GlobalStorageProvider {

    private static final Logger logger = Logger.getLogger("EliteEssentials");

    private final HikariDataSource dataSource;
    private final String prefix;
    private final boolean isMySQL;

    // In-memory caches
    private final ConcurrentHashMap<String, Warp> warps = new ConcurrentHashMap<>();
    private Map<String, List<SpawnStorage.SpawnData>> spawns = new ConcurrentHashMap<>();
    private SpawnStorage.SpawnData firstJoinSpawn;

    public SqlGlobalStorage(HikariDataSource dataSource, String tablePrefix, boolean isMySQL) {
        this.dataSource = dataSource;
        this.prefix = tablePrefix;
        this.isMySQL = isMySQL;
    }

    // ==================== Lifecycle ====================

    @Override
    public void load() {
        loadWarpsFromDb();
        loadSpawns();
    }

    @Override
    public void save() {
        saveWarpsToDb();
        saveSpawns();
    }

    @Override
    public void shutdown() {
        // No-op — the connection pool is closed by StorageFactory
    }

    // ==================== Warp operations ====================

    @Override
    public Map<String, Warp> getAllWarps() {
        return new HashMap<>(warps);
    }

    @Override
    public Optional<Warp> getWarp(String name) {
        return Optional.ofNullable(warps.get(name.toLowerCase()));
    }

    @Override
    public void setWarp(Warp warp) {
        warps.put(warp.getName().toLowerCase(), warp);
        upsertWarpToDb(warp);
    }

    @Override
    public boolean deleteWarp(String name) {
        Warp removed = warps.remove(name.toLowerCase());
        if (removed != null) {
            deleteWarpFromDb(name.toLowerCase());
            return true;
        }
        return false;
    }

    @Override
    public boolean hasWarp(String name) {
        return warps.containsKey(name.toLowerCase());
    }

    @Override
    public Set<String> getWarpNames() {
        return new HashSet<>(warps.keySet());
    }

    @Override
    public int getWarpCount() {
        return warps.size();
    }

    // ==================== Spawn operations ====================

    @Override
    public void loadSpawns() {
        loadSpawnsFromDb();
        loadFirstJoinSpawnFromDb();
    }

    @Override
    public void saveSpawns() {
        saveSpawnsToDb();
        saveFirstJoinSpawnToDb();
    }

    @Override
    public Map<String, List<SpawnStorage.SpawnData>> getAllSpawns() {
        return new HashMap<>(spawns);
    }

    @Override
    public void setAllSpawns(Map<String, List<SpawnStorage.SpawnData>> newSpawns) {
        this.spawns = new ConcurrentHashMap<>(newSpawns);
        saveSpawnsToDb();
    }

    @Override
    public SpawnStorage.SpawnData getFirstJoinSpawn() {
        return firstJoinSpawn;
    }

    @Override
    public void saveFirstJoinSpawn(SpawnStorage.SpawnData spawn) {
        this.firstJoinSpawn = spawn;
        saveFirstJoinSpawnToDb();
    }

    @Override
    public void deleteFirstJoinSpawn() {
        this.firstJoinSpawn = null;
        try (Connection conn = dataSource.getConnection();
             Statement stmt = conn.createStatement()) {
            stmt.executeUpdate("DELETE FROM " + prefix + "first_join_spawn");
        } catch (SQLException e) {
            logger.severe("[SqlGlobalStorage] Failed to delete first-join spawn: " + e.getMessage());
        }
    }

    // ==================== Private DB helpers — Warps ====================

    private void loadWarpsFromDb() {
        warps.clear();
        String sql = "SELECT * FROM " + prefix + "warps";
        try (Connection conn = dataSource.getConnection();
             Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery(sql)) {
            while (rs.next()) {
                Warp warp = new Warp();
                warp.setName(rs.getString("name"));
                warp.setLocation(new Location(
                        rs.getString("world"),
                        rs.getDouble("x"), rs.getDouble("y"), rs.getDouble("z"),
                        rs.getFloat("yaw"), rs.getFloat("pitch")
                ));
                warp.setPermission(Warp.Permission.valueOf(rs.getString("permission")));
                warp.setCreatedBy(rs.getString("created_by"));
                warp.setCreatedAt(rs.getLong("created_at"));
                warp.setDescription(rs.getString("description"));
                warps.put(warp.getName().toLowerCase(), warp);
            }
            logger.info("[SqlGlobalStorage] Loaded " + warps.size() + " warps from SQL.");
        } catch (SQLException e) {
            logger.severe("[SqlGlobalStorage] Failed to load warps: " + e.getMessage());
        }
    }

    private void saveWarpsToDb() {
        try (Connection conn = dataSource.getConnection()) {
            // Clear and re-insert all warps
            try (Statement stmt = conn.createStatement()) {
                stmt.executeUpdate("DELETE FROM " + prefix + "warps");
            }
            if (warps.isEmpty()) return;

            String sql = "INSERT INTO " + prefix + "warps (name, world, x, y, z, yaw, pitch, permission, created_by, created_at, description) "
                    + "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)";
            try (PreparedStatement ps = conn.prepareStatement(sql)) {
                for (Warp warp : warps.values()) {
                    setWarpParams(ps, warp);
                    ps.addBatch();
                }
                ps.executeBatch();
            }
        } catch (SQLException e) {
            logger.severe("[SqlGlobalStorage] Failed to save warps: " + e.getMessage());
        }
    }

    private void upsertWarpToDb(Warp warp) {
        String sql;
        if (isMySQL) {
            sql = "INSERT INTO " + prefix + "warps (name, world, x, y, z, yaw, pitch, permission, created_by, created_at, description) "
                    + "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?) "
                    + "ON DUPLICATE KEY UPDATE world=VALUES(world), x=VALUES(x), y=VALUES(y), z=VALUES(z), "
                    + "yaw=VALUES(yaw), pitch=VALUES(pitch), permission=VALUES(permission), "
                    + "created_by=VALUES(created_by), created_at=VALUES(created_at), description=VALUES(description)";
        } else {
            sql = "INSERT OR REPLACE INTO " + prefix + "warps (name, world, x, y, z, yaw, pitch, permission, created_by, created_at, description) "
                    + "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)";
        }
        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            setWarpParams(ps, warp);
            ps.executeUpdate();
        } catch (SQLException e) {
            logger.severe("[SqlGlobalStorage] Failed to upsert warp '" + warp.getName() + "': " + e.getMessage());
        }
    }

    private void deleteWarpFromDb(String name) {
        String sql = "DELETE FROM " + prefix + "warps WHERE name = ?";
        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, name);
            ps.executeUpdate();
        } catch (SQLException e) {
            logger.severe("[SqlGlobalStorage] Failed to delete warp '" + name + "': " + e.getMessage());
        }
    }

    private void setWarpParams(PreparedStatement ps, Warp warp) throws SQLException {
        Location loc = warp.getLocation();
        ps.setString(1, warp.getName().toLowerCase());
        ps.setString(2, loc.getWorld());
        ps.setDouble(3, loc.getX());
        ps.setDouble(4, loc.getY());
        ps.setDouble(5, loc.getZ());
        ps.setFloat(6, loc.getYaw());
        ps.setFloat(7, loc.getPitch());
        ps.setString(8, warp.getPermission().name());
        ps.setString(9, warp.getCreatedBy());
        ps.setLong(10, warp.getCreatedAt());
        ps.setString(11, warp.getDescription());
    }

    // ==================== Private DB helpers — Spawns ====================

    private void loadSpawnsFromDb() {
        spawns.clear();
        String sql = "SELECT * FROM " + prefix + "spawns";
        try (Connection conn = dataSource.getConnection();
             Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery(sql)) {
            while (rs.next()) {
                SpawnStorage.SpawnData sd = new SpawnStorage.SpawnData();
                sd.world = rs.getString("world");
                sd.name = rs.getString("name");
                sd.primary = rs.getBoolean("is_primary");
                sd.protection = rs.getBoolean("protection");
                sd.x = rs.getDouble("x");
                sd.y = rs.getDouble("y");
                sd.z = rs.getDouble("z");
                sd.yaw = rs.getFloat("yaw");
                sd.pitch = rs.getFloat("pitch");
                spawns.computeIfAbsent(sd.world, k -> new ArrayList<>()).add(sd);
            }
            int total = spawns.values().stream().mapToInt(List::size).sum();
            logger.info("[SqlGlobalStorage] Loaded " + total + " spawn(s) across " + spawns.size() + " world(s).");
        } catch (SQLException e) {
            logger.severe("[SqlGlobalStorage] Failed to load spawns: " + e.getMessage());
        }
    }

    private void loadFirstJoinSpawnFromDb() {
        firstJoinSpawn = null;
        String sql = "SELECT * FROM " + prefix + "first_join_spawn WHERE id = 1";
        try (Connection conn = dataSource.getConnection();
             Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery(sql)) {
            if (rs.next()) {
                SpawnStorage.SpawnData sd = new SpawnStorage.SpawnData();
                sd.world = rs.getString("world");
                sd.x = rs.getDouble("x");
                sd.y = rs.getDouble("y");
                sd.z = rs.getDouble("z");
                sd.yaw = rs.getFloat("yaw");
                sd.pitch = rs.getFloat("pitch");
                firstJoinSpawn = sd;
            }
        } catch (SQLException e) {
            logger.severe("[SqlGlobalStorage] Failed to load first-join spawn: " + e.getMessage());
        }
    }

    private void saveSpawnsToDb() {
        try (Connection conn = dataSource.getConnection()) {
            try (Statement stmt = conn.createStatement()) {
                stmt.executeUpdate("DELETE FROM " + prefix + "spawns");
            }
            if (spawns.isEmpty()) return;

            String sql = "INSERT INTO " + prefix + "spawns (world, name, is_primary, protection, x, y, z, yaw, pitch) "
                    + "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)";
            try (PreparedStatement ps = conn.prepareStatement(sql)) {
                for (List<SpawnStorage.SpawnData> list : spawns.values()) {
                    for (SpawnStorage.SpawnData sd : list) {
                        ps.setString(1, sd.world);
                        ps.setString(2, sd.name);
                        ps.setBoolean(3, sd.primary);
                        ps.setBoolean(4, sd.protection);
                        ps.setDouble(5, sd.x);
                        ps.setDouble(6, sd.y);
                        ps.setDouble(7, sd.z);
                        ps.setFloat(8, sd.yaw);
                        ps.setFloat(9, sd.pitch);
                        ps.addBatch();
                    }
                }
                ps.executeBatch();
            }
        } catch (SQLException e) {
            logger.severe("[SqlGlobalStorage] Failed to save spawns: " + e.getMessage());
        }
    }

    private void saveFirstJoinSpawnToDb() {
        if (firstJoinSpawn == null) return;
        String sql;
        if (isMySQL) {
            sql = "INSERT INTO " + prefix + "first_join_spawn (id, world, x, y, z, yaw, pitch) "
                    + "VALUES (1, ?, ?, ?, ?, ?, ?) "
                    + "ON DUPLICATE KEY UPDATE world=VALUES(world), x=VALUES(x), y=VALUES(y), z=VALUES(z), "
                    + "yaw=VALUES(yaw), pitch=VALUES(pitch)";
        } else {
            sql = "INSERT OR REPLACE INTO " + prefix + "first_join_spawn (id, world, x, y, z, yaw, pitch) "
                    + "VALUES (1, ?, ?, ?, ?, ?, ?)";
        }
        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, firstJoinSpawn.world);
            ps.setDouble(2, firstJoinSpawn.x);
            ps.setDouble(3, firstJoinSpawn.y);
            ps.setDouble(4, firstJoinSpawn.z);
            ps.setFloat(5, firstJoinSpawn.yaw);
            ps.setFloat(6, firstJoinSpawn.pitch);
            ps.executeUpdate();
        } catch (SQLException e) {
            logger.severe("[SqlGlobalStorage] Failed to save first-join spawn: " + e.getMessage());
        }
    }
}
