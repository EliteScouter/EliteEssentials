package com.eliteessentials.storage.sql;

import com.eliteessentials.model.Location;
import com.eliteessentials.model.PlayerWarp;
import com.eliteessentials.storage.PlayerWarpStorageProvider;
import com.zaxxer.hikari.HikariDataSource;

import java.sql.*;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Logger;

/**
 * SQL-backed implementation of {@link PlayerWarpStorageProvider}.
 * Maintains in-memory cache populated on {@link #load()}.
 */
public class SqlPlayerWarpStorage implements PlayerWarpStorageProvider {

    private static final Logger logger = Logger.getLogger("EliteEssentials");

    private final HikariDataSource dataSource;
    private final String prefix;
    private final boolean isMySQL;
    private final ConcurrentHashMap<String, PlayerWarp> warps = new ConcurrentHashMap<>();

    public SqlPlayerWarpStorage(HikariDataSource dataSource, String tablePrefix, boolean isMySQL) {
        this.dataSource = dataSource;
        this.prefix = tablePrefix;
        this.isMySQL = isMySQL;
    }

    @Override
    public void load() {
        warps.clear();
        String sql = "SELECT * FROM " + prefix + "player_warps";
        try (Connection conn = dataSource.getConnection();
             Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery(sql)) {
            while (rs.next()) {
                PlayerWarp warp = new PlayerWarp();
                warp.setName(rs.getString("name"));
                warp.setLocation(new Location(
                        rs.getString("world"),
                        rs.getDouble("x"), rs.getDouble("y"), rs.getDouble("z"),
                        rs.getFloat("yaw"), rs.getFloat("pitch")
                ));
                warp.setOwnerId(UUID.fromString(rs.getString("owner_uuid")));
                warp.setOwnerName(rs.getString("owner_name"));
                warp.setVisibility(PlayerWarp.Visibility.valueOf(rs.getString("visibility")));
                warp.setDescription(rs.getString("description"));
                warp.setCreatedAt(rs.getLong("created_at"));
                warps.put(warp.getName().toLowerCase(), warp);
            }
            logger.info("[SqlPlayerWarpStorage] Loaded " + warps.size() + " player warps from SQL.");
        } catch (SQLException e) {
            logger.severe("[SqlPlayerWarpStorage] Failed to load player warps: " + e.getMessage());
        }
    }

    @Override
    public void save() {
        // Individual operations already persist to DB; full save is a no-op
    }

    @Override
    public void shutdown() {
        // Connection pool managed by StorageFactory
    }

    @Override
    public Map<String, PlayerWarp> getAllWarps() {
        return new HashMap<>(warps);
    }

    @Override
    public Optional<PlayerWarp> getWarp(String name) {
        return Optional.ofNullable(warps.get(name.toLowerCase()));
    }

    @Override
    public void setWarp(PlayerWarp warp) {
        warps.put(warp.getName().toLowerCase(), warp);
        upsertToDb(warp);
    }

    @Override
    public boolean deleteWarp(String name) {
        PlayerWarp removed = warps.remove(name.toLowerCase());
        if (removed != null) {
            deleteFromDb(name.toLowerCase());
            return true;
        }
        return false;
    }

    @Override
    public boolean hasWarp(String name) {
        return warps.containsKey(name.toLowerCase());
    }

    @Override
    public List<PlayerWarp> getWarpsByOwner(UUID ownerId) {
        List<PlayerWarp> result = new ArrayList<>();
        for (PlayerWarp warp : warps.values()) {
            if (ownerId.equals(warp.getOwnerId())) {
                result.add(warp);
            }
        }
        return result;
    }

    @Override
    public int getWarpCountByOwner(UUID ownerId) {
        int count = 0;
        for (PlayerWarp warp : warps.values()) {
            if (ownerId.equals(warp.getOwnerId())) {
                count++;
            }
        }
        return count;
    }

    @Override
    public List<PlayerWarp> getPublicWarps() {
        List<PlayerWarp> result = new ArrayList<>();
        for (PlayerWarp warp : warps.values()) {
            if (warp.isPublic()) {
                result.add(warp);
            }
        }
        return result;
    }

    @Override
    public List<PlayerWarp> getAccessibleWarps(UUID playerId) {
        List<PlayerWarp> result = new ArrayList<>();
        for (PlayerWarp warp : warps.values()) {
            if (warp.canAccess(playerId)) {
                result.add(warp);
            }
        }
        return result;
    }

    // ==================== Private DB helpers ====================

    private void upsertToDb(PlayerWarp warp) {
        String sql;
        if (isMySQL) {
            sql = "INSERT INTO " + prefix + "player_warps (name, world, x, y, z, yaw, pitch, owner_uuid, owner_name, visibility, description, created_at) "
                    + "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?) "
                    + "ON DUPLICATE KEY UPDATE world=VALUES(world), x=VALUES(x), y=VALUES(y), z=VALUES(z), "
                    + "yaw=VALUES(yaw), pitch=VALUES(pitch), owner_uuid=VALUES(owner_uuid), owner_name=VALUES(owner_name), "
                    + "visibility=VALUES(visibility), description=VALUES(description), created_at=VALUES(created_at)";
        } else {
            sql = "MERGE INTO " + prefix + "player_warps (name, world, x, y, z, yaw, pitch, owner_uuid, owner_name, visibility, description, created_at) "
                    + "KEY (name) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)";
        }
        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            setParams(ps, warp);
            ps.executeUpdate();
        } catch (SQLException e) {
            logger.severe("[SqlPlayerWarpStorage] Failed to upsert player warp '" + warp.getName() + "': " + e.getMessage());
        }
    }

    private void deleteFromDb(String name) {
        String sql = "DELETE FROM " + prefix + "player_warps WHERE name = ?";
        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, name);
            ps.executeUpdate();
        } catch (SQLException e) {
            logger.severe("[SqlPlayerWarpStorage] Failed to delete player warp '" + name + "': " + e.getMessage());
        }
    }

    private void setParams(PreparedStatement ps, PlayerWarp warp) throws SQLException {
        Location loc = warp.getLocation();
        ps.setString(1, warp.getName().toLowerCase());
        ps.setString(2, loc.getWorld());
        ps.setDouble(3, loc.getX());
        ps.setDouble(4, loc.getY());
        ps.setDouble(5, loc.getZ());
        ps.setFloat(6, loc.getYaw());
        ps.setFloat(7, loc.getPitch());
        ps.setString(8, warp.getOwnerId().toString());
        ps.setString(9, warp.getOwnerName());
        ps.setString(10, warp.getVisibility().name());
        ps.setString(11, warp.getDescription());
        ps.setLong(12, warp.getCreatedAt());
    }
}
