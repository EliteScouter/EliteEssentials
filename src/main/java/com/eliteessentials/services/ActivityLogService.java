package com.eliteessentials.services;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;
import com.zaxxer.hikari.HikariDataSource;

import java.io.*;
import java.lang.reflect.Type;
import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.*;
import java.util.concurrent.ConcurrentLinkedDeque;
import java.util.logging.Logger;
import java.util.stream.Collectors;

/**
 * Persistent audit log for admin actions (bans, mutes, warns, kicks, etc.).
 * Supports both JSON file storage and SQL database storage.
 */
public class ActivityLogService {

    private static final Logger logger = Logger.getLogger("EliteEssentials");
    private static final int MAX_ENTRIES = 200;
    private static final Gson gson = new GsonBuilder().setPrettyPrinting().create();

    private static ActivityLogService instance;

    private final Deque<LogEntry> entries = new ConcurrentLinkedDeque<>();

    // JSON storage
    private final File dataFile;
    private final Object fileLock = new Object();

    // SQL storage (null when using JSON)
    private final HikariDataSource dataSource;
    private final String tablePrefix;

    /**
     * JSON-only constructor.
     */
    public ActivityLogService(File dataFolder) {
        this.dataFile = new File(dataFolder, "activity_log.json");
        this.dataSource = null;
        this.tablePrefix = null;
        load();
        instance = this;
    }

    /**
     * SQL-backed constructor. Falls back to JSON on SQL errors.
     */
    public ActivityLogService(File dataFolder, HikariDataSource dataSource, String tablePrefix) {
        this.dataFile = new File(dataFolder, "activity_log.json");
        this.dataSource = dataSource;
        this.tablePrefix = tablePrefix;
        load();
        instance = this;
    }

    public static ActivityLogService get() {
        return instance;
    }

    private boolean useSql() {
        return dataSource != null && !dataSource.isClosed();
    }

    /**
     * Record an admin action.
     */
    public void log(String type, String admin, String target, String detail) {
        LogEntry entry = new LogEntry(type, admin, target, detail, System.currentTimeMillis());
        entries.addFirst(entry);
        while (entries.size() > MAX_ENTRIES) {
            entries.removeLast();
        }

        if (useSql()) {
            insertSql(entry);
        } else {
            saveJson();
        }
    }

    public List<LogEntry> getAll() {
        return new ArrayList<>(entries);
    }

    public List<LogEntry> getByType(String type) {
        if (type == null || type.isEmpty() || "all".equalsIgnoreCase(type)) {
            return getAll();
        }
        String upper = type.toUpperCase();
        return entries.stream()
            .filter(e -> e.type.toUpperCase().contains(upper))
            .collect(Collectors.toList());
    }

    public int getCount() {
        return entries.size();
    }

    public void reload() {
        load();
    }

    // ==================== Load ====================

    private void load() {
        entries.clear();
        if (useSql()) {
            loadSql();
        } else {
            loadJson();
        }
    }

    // ==================== JSON ====================

    private void loadJson() {
        if (!dataFile.exists()) {
            return;
        }
        try (Reader reader = new InputStreamReader(
                new FileInputStream(dataFile), StandardCharsets.UTF_8)) {
            Type listType = new TypeToken<List<LogEntry>>(){}.getType();
            List<LogEntry> loaded = gson.fromJson(reader, listType);
            if (loaded != null) {
                entries.addAll(loaded);
            }
        } catch (Exception e) {
            logger.severe("Failed to load activity_log.json: " + e.getMessage());
        }
    }

    private void saveJson() {
        synchronized (fileLock) {
            try (Writer writer = new OutputStreamWriter(
                    new FileOutputStream(dataFile), StandardCharsets.UTF_8)) {
                gson.toJson(new ArrayList<>(entries), writer);
            } catch (Exception e) {
                logger.severe("Failed to save activity_log.json: " + e.getMessage());
            }
        }
    }

    // ==================== SQL ====================

    private void loadSql() {
        String sql = "SELECT type, admin, target, detail, timestamp FROM " + tablePrefix
                + "activity_log ORDER BY timestamp DESC LIMIT " + MAX_ENTRIES;
        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                entries.addLast(new LogEntry(
                    rs.getString("type"),
                    rs.getString("admin"),
                    rs.getString("target"),
                    rs.getString("detail"),
                    rs.getLong("timestamp")
                ));
            }
            logger.info("[ActivityLogService] Loaded " + entries.size() + " activity log entries from SQL.");
        } catch (SQLException e) {
            logger.severe("Failed to load activity log from SQL: " + e.getMessage());
        }
    }

    private void insertSql(LogEntry entry) {
        String sql = "INSERT INTO " + tablePrefix + "activity_log (type, admin, target, detail, timestamp) VALUES (?, ?, ?, ?, ?)";
        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, entry.type);
            ps.setString(2, entry.admin);
            ps.setString(3, entry.target);
            ps.setString(4, entry.detail);
            ps.setLong(5, entry.timestamp);
            ps.executeUpdate();
        } catch (SQLException e) {
            logger.severe("Failed to insert activity log entry to SQL: " + e.getMessage());
        }

        // Trim old entries beyond MAX_ENTRIES
        String trimSql = "DELETE FROM " + tablePrefix + "activity_log WHERE id NOT IN ("
                + "SELECT id FROM (SELECT id FROM " + tablePrefix + "activity_log ORDER BY timestamp DESC LIMIT " + MAX_ENTRIES + ") tmp)";
        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement(trimSql)) {
            ps.executeUpdate();
        } catch (SQLException e) {
            // Non-critical — just log it
            logger.warning("Failed to trim old activity log entries: " + e.getMessage());
        }
    }

    public static class LogEntry {
        public final String type;
        public final String admin;
        public final String target;
        public final String detail;
        public final long timestamp;

        public LogEntry(String type, String admin, String target, String detail, long timestamp) {
            this.type = type;
            this.admin = admin;
            this.target = target;
            this.detail = detail;
            this.timestamp = timestamp;
        }
    }
}
