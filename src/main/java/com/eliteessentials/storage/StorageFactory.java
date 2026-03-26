package com.eliteessentials.storage;

import com.eliteessentials.config.PluginConfig;
import com.eliteessentials.storage.sql.SchemaManager;
import com.eliteessentials.storage.sql.SqlGlobalStorage;
import com.eliteessentials.storage.sql.SqlPlayerStorage;
import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;

import java.io.File;
import java.sql.Connection;
import java.util.logging.Logger;

/**
 * Creates the appropriate storage providers based on configuration.
 * Manages the shared HikariCP connection pool for SQL backends.
 */
public class StorageFactory {

    private static final Logger logger = Logger.getLogger("EliteEssentials");

    private HikariDataSource dataSource;

    /**
     * Create a PlayerStorageProvider based on the configured storage type.
     */
    public PlayerStorageProvider createPlayerStorage(PluginConfig.StorageConfig config, File dataFolder) {
        String type = normalizeType(config);

        switch (type) {
            case "h2":
            case "mysql":
                try {
                    ensureDataSource(config, dataFolder, type);
                    return new SqlPlayerStorage(dataSource, getTablePrefix(config), "mysql".equals(type));
                } catch (Exception e) {
                    logger.severe("Failed to initialize SQL player storage: " + e.getMessage());
                    logger.severe("Falling back to JSON storage.");
                    shutdownPool();
                    return new PlayerFileStorage(dataFolder);
                }

            case "json":
                return new PlayerFileStorage(dataFolder);

            default:
                logger.severe("Unrecognized storageType '" + config.storageType + "', falling back to JSON.");
                return new PlayerFileStorage(dataFolder);
        }
    }

    /**
     * Create a GlobalStorageProvider based on the configured storage type.
     */
    public GlobalStorageProvider createGlobalStorage(PluginConfig.StorageConfig config, File dataFolder) {
        String type = normalizeType(config);

        switch (type) {
            case "h2":
            case "mysql":
                if (dataSource == null) {
                    // Player storage init failed and closed the pool — fall back
                    logger.severe("No active connection pool for global storage, falling back to JSON.");
                    return new WarpStorage(dataFolder);
                }
                try {
                    return new SqlGlobalStorage(dataSource, getTablePrefix(config), "mysql".equals(type));
                } catch (Exception e) {
                    logger.severe("Failed to initialize SQL global storage: " + e.getMessage());
                    logger.severe("Falling back to JSON storage.");
                    return new WarpStorage(dataFolder);
                }

            case "json":
                return new WarpStorage(dataFolder);

            default:
                return new WarpStorage(dataFolder);
        }
    }

    /**
     * Shut down the HikariCP connection pool gracefully.
     * Waits up to 30 seconds for active queries to complete.
     */
    public void shutdownPool() {
        if (dataSource != null && !dataSource.isClosed()) {
            logger.info("Closing database connection pool...");
            dataSource.close();
            logger.info("Database connection pool closed.");
        }
        dataSource = null;
    }

    /**
     * @return true if a SQL connection pool is active
     */
    public boolean isSqlActive() {
        return dataSource != null && !dataSource.isClosed();
    }

    /**
     * @return the shared HikariDataSource, or null if SQL is not active
     */
    public HikariDataSource getDataSource() {
        return dataSource;
    }

    // ==================== Internal ====================

    /**
     * Lazily create the shared HikariDataSource if not already initialized.
     * Also runs SchemaManager to create/migrate tables.
     */
    private void ensureDataSource(PluginConfig.StorageConfig config, File dataFolder, String type) throws Exception {
        if (dataSource != null && !dataSource.isClosed()) {
            return;
        }

        HikariConfig hikari = new HikariConfig();
        hikari.setPoolName("EliteEssentials-HikariPool");

        if ("h2".equals(type)) {
            String dbPath = new File(dataFolder, "eliteessentials").getAbsolutePath();
            String jdbcUrl = "jdbc:h2:file:" + dbPath + ";MODE=MySQL;DB_CLOSE_DELAY=-1";
            hikari.setDriverClassName("org.h2.Driver");
            hikari.setJdbcUrl(jdbcUrl);
            hikari.setUsername("sa");
            hikari.setPassword("");
            logger.info("Initializing H2 database at: " + dbPath);
        } else {
            // MySQL / MariaDB
            PluginConfig.StorageConfig.MysqlConfig mysql = config.mysql;
            hikari.setDriverClassName("com.mysql.cj.jdbc.Driver");
            hikari.setJdbcUrl("jdbc:mysql://" + mysql.host + ":" + mysql.port + "/" + mysql.database
                    + "?useSSL=false&allowPublicKeyRetrieval=true&characterEncoding=utf8");
            hikari.setUsername(mysql.username);
            hikari.setPassword(mysql.password);
            logger.info("Initializing MySQL connection to " + mysql.host + ":" + mysql.port + "/" + mysql.database);
        }

        // Connection pool settings
        PluginConfig.StorageConfig.ConnectionPoolConfig pool =
                config.mysql != null && config.mysql.connectionPool != null
                        ? config.mysql.connectionPool : new PluginConfig.StorageConfig.ConnectionPoolConfig();
        hikari.setMaximumPoolSize(pool.maximumPoolSize);
        hikari.setMinimumIdle(pool.minimumIdle);
        hikari.setConnectionTimeout(pool.connectionTimeout);
        hikari.setMaxLifetime(1800000); // 30 minutes
        hikari.setIdleTimeout(600000);  // 10 minutes

        // HikariCP is relocated to com.eliteessentials.libs.hikari, so its internal
        // classloader can't find JDBC drivers in their original packages. We fix this
        // by temporarily setting the thread context classloader to the plugin's classloader,
        // which has visibility to both relocated and non-relocated classes.
        // This must wrap the HikariDataSource constructor — that's where driver loading happens.
        ClassLoader originalCL = Thread.currentThread().getContextClassLoader();
        try {
            Thread.currentThread().setContextClassLoader(this.getClass().getClassLoader());
            dataSource = new HikariDataSource(hikari);
        } finally {
            Thread.currentThread().setContextClassLoader(originalCL);
        }

        // Run schema creation / migration
        SchemaManager schemaManager = new SchemaManager();
        try (Connection conn = dataSource.getConnection()) {
            schemaManager.initialize(conn, getTablePrefix(config));
        } catch (Exception e) {
            logger.severe("Schema initialization failed: " + e.getMessage());
            shutdownPool();
            throw e;
        }

        logger.info("SQL storage initialized successfully (" + type + ").");
    }

    private String getTablePrefix(PluginConfig.StorageConfig config) {
        return config.mysql != null && config.mysql.tablePrefix != null
                ? config.mysql.tablePrefix : "ee_";
    }

    private String normalizeType(PluginConfig.StorageConfig config) {
        return config.storageType != null ? config.storageType.toLowerCase().trim() : "json";
    }
}
