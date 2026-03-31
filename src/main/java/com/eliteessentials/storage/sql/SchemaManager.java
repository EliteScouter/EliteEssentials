package com.eliteessentials.storage.sql;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.logging.Logger;

/**
 * Manages SQL database schema creation and migrations.
 * Creates all required tables on first startup and applies incremental
 * schema migrations when the plugin version changes.
 */
public class SchemaManager {

    private static final Logger logger = Logger.getLogger("EliteEssentials");
    private static final int CURRENT_SCHEMA_VERSION = 3;

    /**
     * Initialize the database schema. Creates all tables if they don't exist
     * and applies any pending migrations.
     *
     * @param conn        a live JDBC connection
     * @param tablePrefix the table name prefix (e.g. "ee_")
     * @throws SQLException if table creation or migration fails
     */
    public void initialize(Connection conn, String tablePrefix) throws SQLException {
        createTables(conn, tablePrefix);

        int currentVersion = getCurrentVersion(conn, tablePrefix);
        if (currentVersion == 0) {
            // Fresh install — stamp version 1
            insertSchemaVersion(conn, tablePrefix, CURRENT_SCHEMA_VERSION);
            logger.info("[SchemaManager] Created schema version " + CURRENT_SCHEMA_VERSION);
        } else if (currentVersion < CURRENT_SCHEMA_VERSION) {
            migrate(conn, tablePrefix, currentVersion, CURRENT_SCHEMA_VERSION);
            logger.info("[SchemaManager] Migrated schema from v" + currentVersion + " to v" + CURRENT_SCHEMA_VERSION);
        } else {
            logger.info("[SchemaManager] Schema is up to date (v" + currentVersion + ")");
        }
    }

    /**
     * Read the current schema version from the metadata table.
     * Returns 0 if the table is empty (fresh install).
     */
    public int getCurrentVersion(Connection conn, String tablePrefix) throws SQLException {
        String sql = "SELECT MAX(version) FROM " + tablePrefix + "schema_version";
        try (Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery(sql)) {
            if (rs.next()) {
                int version = rs.getInt(1);
                return rs.wasNull() ? 0 : version;
            }
        }
        return 0;
    }

    /**
     * Apply incremental schema migrations from {@code fromVersion} to {@code toVersion}.
     * Each migration step runs inside its own transaction so a failure rolls back
     * only the failing step.
     */
    public void migrate(Connection conn, String tablePrefix, int fromVersion, int toVersion) throws SQLException {
        boolean originalAutoCommit = conn.getAutoCommit();
        for (int v = fromVersion + 1; v <= toVersion; v++) {
            try {
                conn.setAutoCommit(false);
                applyMigration(conn, tablePrefix, v);
                insertSchemaVersion(conn, tablePrefix, v);
                conn.commit();
                logger.info("[SchemaManager] Applied migration to v" + v);
            } catch (SQLException e) {
                conn.rollback();
                logger.severe("[SchemaManager] Migration to v" + v + " failed, rolled back: " + e.getMessage());
                throw e;
            } finally {
                conn.setAutoCommit(originalAutoCommit);
            }
        }
    }

    // ==================== Private helpers ====================

    private void applyMigration(Connection conn, String tablePrefix, int version) throws SQLException {
        // Future migrations go here as case statements.
        // Example:
        // case 2: applyV2Migration(conn, tablePrefix); break;
        switch (version) {
            case 2:
                // Add activity_log table
                try (Statement stmt = conn.createStatement()) {
                    stmt.executeUpdate("CREATE TABLE IF NOT EXISTS " + tablePrefix + "activity_log ("
                            + "id         INT          AUTO_INCREMENT PRIMARY KEY,"
                            + "type       VARCHAR(32)  NOT NULL,"
                            + "admin      VARCHAR(64)  NOT NULL,"
                            + "target     VARCHAR(64)  NOT NULL,"
                            + "detail     TEXT,"
                            + "timestamp  BIGINT       NOT NULL"
                            + ")");
                    stmt.executeUpdate("CREATE INDEX IF NOT EXISTS idx_" + tablePrefix + "activity_type ON "
                            + tablePrefix + "activity_log(type)");
                    stmt.executeUpdate("CREATE INDEX IF NOT EXISTS idx_" + tablePrefix + "activity_ts ON "
                            + tablePrefix + "activity_log(timestamp)");
                }
                break;
            case 3:
                // Add player_warps table
                try (Statement stmt = conn.createStatement()) {
                    stmt.executeUpdate("CREATE TABLE IF NOT EXISTS " + tablePrefix + "player_warps ("
                            + "name        VARCHAR(64)  PRIMARY KEY,"
                            + "world       VARCHAR(128) NOT NULL,"
                            + "x           DOUBLE       NOT NULL,"
                            + "y           DOUBLE       NOT NULL,"
                            + "z           DOUBLE       NOT NULL,"
                            + "yaw         FLOAT        NOT NULL DEFAULT 0,"
                            + "pitch       FLOAT        NOT NULL DEFAULT 0,"
                            + "owner_uuid  VARCHAR(36)  NOT NULL,"
                            + "owner_name  VARCHAR(64)  NOT NULL,"
                            + "visibility  VARCHAR(16)  NOT NULL DEFAULT 'PUBLIC',"
                            + "description TEXT,"
                            + "created_at  BIGINT       NOT NULL DEFAULT 0"
                            + ")");
                    stmt.executeUpdate("CREATE INDEX IF NOT EXISTS idx_" + tablePrefix + "pwarp_owner ON "
                            + tablePrefix + "player_warps(owner_uuid)");
                    stmt.executeUpdate("CREATE INDEX IF NOT EXISTS idx_" + tablePrefix + "pwarp_vis ON "
                            + tablePrefix + "player_warps(visibility)");
                }
                break;
            default:
                break;
        }
    }

    private void insertSchemaVersion(Connection conn, String tablePrefix, int version) throws SQLException {
        String sql = "INSERT INTO " + tablePrefix + "schema_version (version, applied_at) VALUES (?, ?)";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, version);
            ps.setLong(2, System.currentTimeMillis());
            ps.executeUpdate();
        }
    }

    private void createTables(Connection conn, String tablePrefix) throws SQLException {
        try (Statement stmt = conn.createStatement()) {
            // Players
            stmt.executeUpdate("CREATE TABLE IF NOT EXISTS " + tablePrefix + "players ("
                    + "uuid         VARCHAR(36)  PRIMARY KEY,"
                    + "name         VARCHAR(64)  NOT NULL,"
                    + "nickname     VARCHAR(64),"
                    + "first_join   BIGINT       NOT NULL DEFAULT 0,"
                    + "last_seen    BIGINT       NOT NULL DEFAULT 0,"
                    + "play_time    BIGINT       NOT NULL DEFAULT 0,"
                    + "wallet       DOUBLE       NOT NULL DEFAULT 0.0,"
                    + "vanished     BOOLEAN      NOT NULL DEFAULT FALSE,"
                    + "default_group_chat VARCHAR(64)"
                    + ")");
            stmt.executeUpdate("CREATE INDEX IF NOT EXISTS idx_" + tablePrefix + "players_name ON "
                    + tablePrefix + "players(name)");

            // Homes
            stmt.executeUpdate("CREATE TABLE IF NOT EXISTS " + tablePrefix + "homes ("
                    + "uuid       VARCHAR(36)  NOT NULL,"
                    + "name       VARCHAR(64)  NOT NULL,"
                    + "world      VARCHAR(128) NOT NULL,"
                    + "x          DOUBLE       NOT NULL,"
                    + "y          DOUBLE       NOT NULL,"
                    + "z          DOUBLE       NOT NULL,"
                    + "yaw        FLOAT        NOT NULL DEFAULT 0,"
                    + "pitch      FLOAT        NOT NULL DEFAULT 0,"
                    + "created_at BIGINT       NOT NULL DEFAULT 0,"
                    + "PRIMARY KEY (uuid, name),"
                    + "FOREIGN KEY (uuid) REFERENCES " + tablePrefix + "players(uuid) ON DELETE CASCADE"
                    + ")");

            // Back history
            stmt.executeUpdate("CREATE TABLE IF NOT EXISTS " + tablePrefix + "back_history ("
                    + "id         INT          AUTO_INCREMENT PRIMARY KEY,"
                    + "uuid       VARCHAR(36)  NOT NULL,"
                    + "position   INT          NOT NULL,"
                    + "world      VARCHAR(128) NOT NULL,"
                    + "x          DOUBLE       NOT NULL,"
                    + "y          DOUBLE       NOT NULL,"
                    + "z          DOUBLE       NOT NULL,"
                    + "yaw        FLOAT        NOT NULL DEFAULT 0,"
                    + "pitch      FLOAT        NOT NULL DEFAULT 0,"
                    + "FOREIGN KEY (uuid) REFERENCES " + tablePrefix + "players(uuid) ON DELETE CASCADE"
                    + ")");
            stmt.executeUpdate("CREATE INDEX IF NOT EXISTS idx_" + tablePrefix + "back_uuid ON "
                    + tablePrefix + "back_history(uuid)");

            // Kit claims
            stmt.executeUpdate("CREATE TABLE IF NOT EXISTS " + tablePrefix + "kit_claims ("
                    + "uuid    VARCHAR(36)  NOT NULL,"
                    + "kit_id  VARCHAR(64)  NOT NULL,"
                    + "PRIMARY KEY (uuid, kit_id),"
                    + "FOREIGN KEY (uuid) REFERENCES " + tablePrefix + "players(uuid) ON DELETE CASCADE"
                    + ")");

            // Kit cooldowns
            stmt.executeUpdate("CREATE TABLE IF NOT EXISTS " + tablePrefix + "kit_cooldowns ("
                    + "uuid       VARCHAR(36)  NOT NULL,"
                    + "kit_id     VARCHAR(64)  NOT NULL,"
                    + "last_used  BIGINT       NOT NULL,"
                    + "PRIMARY KEY (uuid, kit_id),"
                    + "FOREIGN KEY (uuid) REFERENCES " + tablePrefix + "players(uuid) ON DELETE CASCADE"
                    + ")");

            // Playtime claims
            stmt.executeUpdate("CREATE TABLE IF NOT EXISTS " + tablePrefix + "playtime_claims ("
                    + "uuid       VARCHAR(36)  NOT NULL,"
                    + "reward_id  VARCHAR(64)  NOT NULL,"
                    + "type       VARCHAR(16)  NOT NULL,"
                    + "count      INT          NOT NULL DEFAULT 1,"
                    + "PRIMARY KEY (uuid, reward_id),"
                    + "FOREIGN KEY (uuid) REFERENCES " + tablePrefix + "players(uuid) ON DELETE CASCADE"
                    + ")");

            // Ignored players
            stmt.executeUpdate("CREATE TABLE IF NOT EXISTS " + tablePrefix + "ignored_players ("
                    + "uuid          VARCHAR(36) NOT NULL,"
                    + "ignored_uuid  VARCHAR(36) NOT NULL,"
                    + "PRIMARY KEY (uuid, ignored_uuid),"
                    + "FOREIGN KEY (uuid) REFERENCES " + tablePrefix + "players(uuid) ON DELETE CASCADE"
                    + ")");

            // Mailbox
            stmt.executeUpdate("CREATE TABLE IF NOT EXISTS " + tablePrefix + "mailbox ("
                    + "id         INT          AUTO_INCREMENT PRIMARY KEY,"
                    + "uuid       VARCHAR(36)  NOT NULL,"
                    + "message_id VARCHAR(16)  NOT NULL,"
                    + "sender_uuid VARCHAR(36),"
                    + "sender     VARCHAR(64)  NOT NULL,"
                    + "message    TEXT         NOT NULL,"
                    + "sent_at    BIGINT       NOT NULL,"
                    + "is_read    BOOLEAN      NOT NULL DEFAULT FALSE,"
                    + "FOREIGN KEY (uuid) REFERENCES " + tablePrefix + "players(uuid) ON DELETE CASCADE"
                    + ")");
            stmt.executeUpdate("CREATE INDEX IF NOT EXISTS idx_" + tablePrefix + "mailbox_uuid ON "
                    + tablePrefix + "mailbox(uuid)");

            // IP history
            stmt.executeUpdate("CREATE TABLE IF NOT EXISTS " + tablePrefix + "ip_history ("
                    + "uuid       VARCHAR(36)  NOT NULL,"
                    + "ip         VARCHAR(45)  NOT NULL,"
                    + "last_used  BIGINT       NOT NULL,"
                    + "PRIMARY KEY (uuid, ip),"
                    + "FOREIGN KEY (uuid) REFERENCES " + tablePrefix + "players(uuid) ON DELETE CASCADE"
                    + ")");

            // Balance change notifications
            stmt.executeUpdate("CREATE TABLE IF NOT EXISTS " + tablePrefix + "balance_notifications ("
                    + "uuid         VARCHAR(36) PRIMARY KEY,"
                    + "old_balance  DOUBLE      NOT NULL,"
                    + "new_balance  DOUBLE      NOT NULL,"
                    + "diff         DOUBLE      NOT NULL,"
                    + "FOREIGN KEY (uuid) REFERENCES " + tablePrefix + "players(uuid) ON DELETE CASCADE"
                    + ")");

            // Warps
            stmt.executeUpdate("CREATE TABLE IF NOT EXISTS " + tablePrefix + "warps ("
                    + "name        VARCHAR(64)  PRIMARY KEY,"
                    + "world       VARCHAR(128) NOT NULL,"
                    + "x           DOUBLE       NOT NULL,"
                    + "y           DOUBLE       NOT NULL,"
                    + "z           DOUBLE       NOT NULL,"
                    + "yaw         FLOAT        NOT NULL DEFAULT 0,"
                    + "pitch       FLOAT        NOT NULL DEFAULT 0,"
                    + "permission  VARCHAR(16)  NOT NULL DEFAULT 'ALL',"
                    + "created_by  VARCHAR(64),"
                    + "created_at  BIGINT       NOT NULL DEFAULT 0,"
                    + "description TEXT"
                    + ")");

            // Spawns
            stmt.executeUpdate("CREATE TABLE IF NOT EXISTS " + tablePrefix + "spawns ("
                    + "world      VARCHAR(128) NOT NULL,"
                    + "name       VARCHAR(64)  NOT NULL,"
                    + "is_primary BOOLEAN      NOT NULL DEFAULT FALSE,"
                    + "protection BOOLEAN      NOT NULL DEFAULT TRUE,"
                    + "x          DOUBLE       NOT NULL,"
                    + "y          DOUBLE       NOT NULL,"
                    + "z          DOUBLE       NOT NULL,"
                    + "yaw        FLOAT        NOT NULL DEFAULT 0,"
                    + "pitch      FLOAT        NOT NULL DEFAULT 0,"
                    + "PRIMARY KEY (world, name)"
                    + ")");

            // First-join spawn (singleton row — application always uses id=1)
            stmt.executeUpdate("CREATE TABLE IF NOT EXISTS " + tablePrefix + "first_join_spawn ("
                    + "id         INT          PRIMARY KEY,"
                    + "world      VARCHAR(128) NOT NULL,"
                    + "x          DOUBLE       NOT NULL,"
                    + "y          DOUBLE       NOT NULL,"
                    + "z          DOUBLE       NOT NULL,"
                    + "yaw        FLOAT        NOT NULL DEFAULT 0,"
                    + "pitch      FLOAT        NOT NULL DEFAULT 0"
                    + ")");

            // Schema version
            stmt.executeUpdate("CREATE TABLE IF NOT EXISTS " + tablePrefix + "schema_version ("
                    + "version    INT          NOT NULL,"
                    + "applied_at BIGINT       NOT NULL"
                    + ")");

            // Activity log
            stmt.executeUpdate("CREATE TABLE IF NOT EXISTS " + tablePrefix + "activity_log ("
                    + "id         INT          AUTO_INCREMENT PRIMARY KEY,"
                    + "type       VARCHAR(32)  NOT NULL,"
                    + "admin      VARCHAR(64)  NOT NULL,"
                    + "target     VARCHAR(64)  NOT NULL,"
                    + "detail     TEXT,"
                    + "timestamp  BIGINT       NOT NULL"
                    + ")");
            stmt.executeUpdate("CREATE INDEX IF NOT EXISTS idx_" + tablePrefix + "activity_type ON "
                    + tablePrefix + "activity_log(type)");
            stmt.executeUpdate("CREATE INDEX IF NOT EXISTS idx_" + tablePrefix + "activity_ts ON "
                    + tablePrefix + "activity_log(timestamp)");

            // Player warps
            stmt.executeUpdate("CREATE TABLE IF NOT EXISTS " + tablePrefix + "player_warps ("
                    + "name        VARCHAR(64)  PRIMARY KEY,"
                    + "world       VARCHAR(128) NOT NULL,"
                    + "x           DOUBLE       NOT NULL,"
                    + "y           DOUBLE       NOT NULL,"
                    + "z           DOUBLE       NOT NULL,"
                    + "yaw         FLOAT        NOT NULL DEFAULT 0,"
                    + "pitch       FLOAT        NOT NULL DEFAULT 0,"
                    + "owner_uuid  VARCHAR(36)  NOT NULL,"
                    + "owner_name  VARCHAR(64)  NOT NULL,"
                    + "visibility  VARCHAR(16)  NOT NULL DEFAULT 'PUBLIC',"
                    + "description TEXT,"
                    + "created_at  BIGINT       NOT NULL DEFAULT 0"
                    + ")");
            stmt.executeUpdate("CREATE INDEX IF NOT EXISTS idx_" + tablePrefix + "pwarp_owner ON "
                    + tablePrefix + "player_warps(owner_uuid)");
            stmt.executeUpdate("CREATE INDEX IF NOT EXISTS idx_" + tablePrefix + "pwarp_vis ON "
                    + tablePrefix + "player_warps(visibility)");
        }
    }
}
