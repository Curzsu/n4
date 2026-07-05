package com.n4monitor.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.n4monitor.alarm.model.PointAlarmState;
import com.n4monitor.config.TdengineConfig;
import jakarta.annotation.PreDestroy;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.sql.*;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * TDengine data synchronization service
 * <p>
 * Periodically reads latest point data from Redis (alarm:latest)
 * and writes to TDengine database asynchronously.
 * <p>
 * Uses REST mode (jdbc:TAOS-RS) so no native TDengine client is required.
 * Connection is established lazily on the first sync attempt.
 * <p>
 * Supertable: alarm
 * Subtable: each point has its own subtable named by path field
 * Tags: name, displayName, path
 * Columns: value, type, unit, max, min, ip, port, other, timestamp,
 *          level, severity, message, ackBy, status, source, location
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class TdengineSyncService {

    private static final String REDIS_LATEST_KEY = "alarm:latest";

    private final TdengineConfig tdengineConfig;
    private final StringRedisTemplate redisTemplate;
    private final ObjectMapper objectMapper = new ObjectMapper();

    private volatile Connection connection;
    private volatile boolean initialized = false;

    private final ExecutorService asyncExecutor = Executors.newSingleThreadExecutor(r -> {
        Thread t = new Thread(r, "tdengine-async-writer");
        t.setDaemon(true);
        return t;
    });

    /**
     * Ensure connection is alive; reconnect if needed.
     *
     * @return true if connection is usable
     */
    private synchronized boolean ensureConnected() {
        try {
            if (connection != null && !connection.isClosed()) {
                return true;
            }
        } catch (SQLException e) {
            log.warn("TDengine connection check failed, will reconnect", e);
        }

        try {
            String url = tdengineConfig.getUrl();
            String username = tdengineConfig.getUsername();
            String password = tdengineConfig.getPassword();

            log.info("Connecting to TDengine (REST): {}", url);
            connection = DriverManager.getConnection(url, username, password);
            log.info("Connected to TDengine successfully");

            if (!initialized) {
                initDatabase();
                initSupertable();
                initialized = true;
                log.info("TDengine database and supertable initialized");
            }
            return true;
        } catch (Exception e) {
            log.error("Failed to connect to TDengine: {}", e.getMessage());
            connection = null;
            return false;
        }
    }

    /**
     * Create database if not exists
     */
    private void initDatabase() throws SQLException {
        String database = tdengineConfig.getDatabase();
        String sql = "CREATE DATABASE IF NOT EXISTS " + database + " KEEP 365 DURATION 10 BUFFER 16";
        try (Statement stmt = connection.createStatement()) {
            stmt.execute(sql);
            log.info("Database '{}' created/verified", database);
        }
        // Switch to the database
        try (Statement stmt = connection.createStatement()) {
            stmt.execute("USE " + database);
        }
    }

    /**
     * Create supertable if not exists
     */
    private void initSupertable() throws SQLException {
        String supertable = tdengineConfig.getSupertable();
        String sql = "CREATE STABLE IF NOT EXISTS " + supertable + " ("
                + "ts TIMESTAMP, "
                + "point_value NCHAR(64), "
                + "point_type NCHAR(32), "
                + "unit NCHAR(16), "
                + "max_val NCHAR(64), "
                + "min_val NCHAR(64), "
                + "ip_addr NCHAR(64), "
                + "port_num NCHAR(16), "
                + "other NCHAR(256), "
                + "alarm_level NCHAR(32), "
                + "alarm_severity NCHAR(32), "
                + "alarm_message NCHAR(256), "
                + "ack_by NCHAR(32), "
                + "alarm_status NCHAR(32), "
                + "alarm_source NCHAR(64), "
                + "alarm_location NCHAR(128)"
                + ") TAGS ("
                + "name NCHAR(128), "
                + "display_name NCHAR(128), "
                + "path NCHAR(256)"
                + ")";
        try (Statement stmt = connection.createStatement()) {
            stmt.execute(sql);
            log.info("Supertable '{}' created/verified", supertable);
        }
    }

    /**
     * Scheduled task: sync data from Redis to TDengine every 5 seconds.
     * Submits work to async executor so the scheduler thread is never blocked.
     */
    @Scheduled(fixedRate = 5000, initialDelay = 15000)
    public void syncData() {
        asyncExecutor.submit(() -> {
            try {
                doSyncData();
            } catch (Exception e) {
                log.error("Failed to sync data to TDengine", e);
            }
        });
    }

    /**
     * Read latest data from Redis and write to TDengine
     */
    private void doSyncData() {
        // Lazy connect - don't crash the app if TDengine is unavailable
        if (!ensureConnected()) {
            log.debug("TDengine not available, skipping sync cycle");
            return;
        }

        // 1. Read all data from Redis hash
        Map<Object, Object> rawData;
        try {
            rawData = redisTemplate.opsForHash().entries(REDIS_LATEST_KEY);
        } catch (Exception e) {
            log.warn("Failed to read from Redis key '{}': {}", REDIS_LATEST_KEY, e.getMessage());
            return;
        }

        if (rawData == null || rawData.isEmpty()) {
            log.debug("No data in Redis key '{}'", REDIS_LATEST_KEY);
            return;
        }

        log.debug("Read {} entries from Redis", rawData.size());

        int successCount = 0;
        int failCount = 0;

        // 2. Process each entry
        for (Map.Entry<Object, Object> entry : rawData.entrySet()) {
            String path = (String) entry.getKey();
            String json = (String) entry.getValue();

            try {
                PointAlarmState state = objectMapper.readValue(json, new TypeReference<>() {});
                writeToTdengine(state);
                successCount++;
            } catch (SQLException e) {
                failCount++;
                // Connection lost, will reconnect on next cycle
                log.error("SQL error writing path '{}': {}", path, e.getMessage());
                closeConnection();
                break;
            } catch (Exception e) {
                failCount++;
                log.error("Failed to write data for path: {}", path, e);
            }
        }

        if (successCount > 0) {
            log.info("Synced {} entries to TDengine (failed: {})", successCount, failCount);
        }
    }

    /**
     * Write a single PointAlarmState to TDengine.
     * Uses INSERT ... USING STABLE TAGS syntax for automatic subtable creation.
     */
    private void writeToTdengine(PointAlarmState state) throws SQLException {
        String supertable = tdengineConfig.getSupertable();
        String path = state.getPath();
        String subtable = sanitizeTableName(path);

        String sql = "INSERT INTO " + subtable + " USING " + supertable
                + " TAGS (?, ?, ?) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)";

        try (PreparedStatement pstmt = connection.prepareStatement(sql)) {
            // Tags (3)
            pstmt.setString(1, state.getName());
            pstmt.setString(2, state.getDisplayName());
            pstmt.setString(3, path);

            // Columns (16)
            pstmt.setTimestamp(4, state.getTimestamp() != null
                    ? new Timestamp(state.getTimestamp()) : new Timestamp(System.currentTimeMillis()));
            pstmt.setString(5, state.getValue());
            pstmt.setString(6, state.getType());
            pstmt.setString(7, state.getUnit());
            pstmt.setString(8, state.getMax());
            pstmt.setString(9, state.getMin());
            pstmt.setString(10, state.getIp());
            pstmt.setString(11, state.getPort());
            pstmt.setString(12, state.getOther());
            pstmt.setString(13, state.getLevel());
            pstmt.setString(14, state.getSeverity());
            pstmt.setString(15, state.getMessage());
            pstmt.setString(16, state.getAckBy());
            pstmt.setString(17, state.getStatus());
            pstmt.setString(18, state.getSource());
            pstmt.setString(19, state.getLocation());

            pstmt.executeUpdate();
        }
    }

    /**
     * Sanitize path to create a valid TDengine table name.
     * Replace special characters with underscores.
     */
    private String sanitizeTableName(String path) {
        if (path == null || path.isEmpty()) {
            return "unknown_point";
        }
        return path.replaceAll("[^a-zA-Z0-9_]", "_");
    }

    /**
     * Close current connection quietly
     */
    private void closeConnection() {
        if (connection != null) {
            try {
                connection.close();
            } catch (SQLException ignored) {
            }
            connection = null;
        }
    }

    /**
     * Cleanup resources on shutdown
     */
    @PreDestroy
    public void destroy() {
        asyncExecutor.shutdownNow();
        closeConnection();
        log.info("TDengine sync service shut down");
    }
}
