package com.n4monitor.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.n4monitor.alarm.model.PointAlarmState;
import com.n4monitor.config.TdengineConfig;
import com.n4monitor.dto.AlarmRecordDto;
import jakarta.annotation.PreDestroy;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.sql.*;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * TDengine data synchronization service.
 * <p>
 * Provides two persistence paths:
 * <ol>
 *   <li>Periodic snapshot: reads latest point data from Redis (alarm:latest)
 *       and writes to TDengine supertable {@code alarm} every N seconds.
 *   <li>Real-time alarm records: writes confirmed alarm records to supertable
 *       {@code alarm_realtime} immediately when Flink publishes them.
 * </ol>
 * <p>
 * Uses REST mode (jdbc:TAOS-RS) so no native TDengine client is required.
 * Connection is established lazily on the first access.
 * <p>
 * Subtable naming: each point has its own subtable named by the sanitized path field.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class TdengineSyncService {

    @Value("${alarm.redis.latest-key}")
    private String latestRedisKey;

    private final TdengineConfig tdengineConfig;
    private final StringRedisTemplate redisTemplate;
    private final ObjectMapper objectMapper;

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
                initRealtimeSupertable();
                initialized = true;
                log.info("TDengine database and supertables initialized");
            }
            return true;
        } catch (Exception e) {
            log.error("Failed to connect to TDengine: {}", e.getMessage());
            connection = null;
            return false;
        }
    }

    /**
     * Create database if not exists.
     */
    private void initDatabase() throws SQLException {
        String database = tdengineConfig.getDatabase();
        String sql = "CREATE DATABASE IF NOT EXISTS " + database + " KEEP 365 DURATION 10 BUFFER 16";
        try (Statement stmt = connection.createStatement()) {
            stmt.execute(sql);
            log.info("Database '{}' created/verified", database);
        }
        try (Statement stmt = connection.createStatement()) {
            stmt.execute("USE " + database);
        }
    }

    /**
     * Create snapshot supertable if not exists.
     * <p>
     * Schema has been slimmed to match the new Kafka message format (only
     * clientId/path/timestamp/value + resolved type). Removed columns:
     * unit, max_val, min_val, ip_addr, port_num, other; removed tags: name,
     * display_name (path is now the only tag and the unique point key).
     * Added column: alarm_stage_message (alarm state transition label).
     */
    private void initSupertable() throws SQLException {
        String supertable = tdengineConfig.getSupertable();
        String sql = "CREATE STABLE IF NOT EXISTS " + supertable + " ("
                + "ts TIMESTAMP, "
                + "data_timestamp TIMESTAMP, "
                + "point_value NCHAR(64), "
                + "point_type NCHAR(32), "
                + "alarm_level NCHAR(32), "
                + "alarm_severity NCHAR(32), "
                + "alarm_message NCHAR(256), "
                + "time_delay NCHAR(32), "
                + "deadband NCHAR(64), "
                + "ack_by NCHAR(32), "
                + "alarm_status NCHAR(32), "
                + "alarm_source NCHAR(64), "
                + "alarm_location NCHAR(128), "
                + "alarm_stage NCHAR(16), "
                + "alarm_stage_message NCHAR(64), "
                + "alarm_duration NCHAR(32), "
                + "alarm_start_time TIMESTAMP"
                + ") TAGS ("
                + "path NCHAR(256)"
                + ")";
        try (Statement stmt = connection.createStatement()) {
            stmt.execute(sql);
            log.info("Snapshot supertable '{}' created/verified", supertable);
        }
    }

    /**
     * Create real-time alarm supertable if not exists.
     */
    private void initRealtimeSupertable() throws SQLException {
        String supertable = tdengineConfig.getRealtimeSupertable();
        String sql = "CREATE STABLE IF NOT EXISTS " + supertable + " ("
                + "ts TIMESTAMP, "
                + "data_timestamp TIMESTAMP, "
                + "point_value NCHAR(64), "
                + "point_type NCHAR(32), "
                + "alarm_level NCHAR(32), "
                + "alarm_severity NCHAR(32), "
                + "alarm_message NCHAR(256), "
                + "time_delay NCHAR(32), "
                + "deadband NCHAR(64), "
                + "ack_by NCHAR(32), "
                + "alarm_status NCHAR(32), "
                + "alarm_source NCHAR(64), "
                + "alarm_location NCHAR(128), "
                + "alarm_stage NCHAR(16), "
                + "alarm_stage_message NCHAR(64), "
                + "alarm_duration NCHAR(32), "
                + "alarm_start_time TIMESTAMP"
                + ") TAGS ("
                + "path NCHAR(256)"
                + ")";
        try (Statement stmt = connection.createStatement()) {
            stmt.execute(sql);
            log.info("Realtime supertable '{}' created/verified", supertable);
        }
    }

    /**
     * Scheduled task: sync data from Redis to TDengine periodically.
     * Interval is configured via tdengine.sync-fixed-rate-ms.
     */
    @Scheduled(fixedRateString = "${tdengine.sync-fixed-rate-ms:5000}",
            initialDelayString = "${tdengine.sync-initial-delay-ms:15000}")
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
     * Read latest data from Redis and write to TDengine snapshot supertable.
     */
    private void doSyncData() {
        if (!ensureConnected()) {
            log.debug("TDengine not available, skipping sync cycle");
            return;
        }

        Map<Object, Object> rawData;
        try {
            rawData = redisTemplate.opsForHash().entries(latestRedisKey);
        } catch (Exception e) {
            log.warn("Failed to read from Redis key '{}': {}", latestRedisKey, e.getMessage());
            return;
        }

        if (rawData == null || rawData.isEmpty()) {
            log.debug("No data in Redis key '{}'", latestRedisKey);
            return;
        }

        log.debug("Read {} entries from Redis", rawData.size());

        int successCount = 0;
        int failCount = 0;

        for (Map.Entry<Object, Object> entry : rawData.entrySet()) {
            String path = (String) entry.getKey();
            String json = (String) entry.getValue();

            try {
                PointAlarmState state = objectMapper.readValue(json, new TypeReference<>() {});
                writeToSnapshotSupertable(state);
                successCount++;
            } catch (SQLException e) {
                failCount++;
                log.error("SQL error writing path '{}': {}", path, e.getMessage());
                closeConnection();
                break;
            } catch (Exception e) {
                failCount++;
                log.error("Failed to write data for path: {}", path, e);
            }
        }

        if (successCount > 0) {
            log.info("Synced {} entries to TDengine snapshot (failed: {})", successCount, failCount);
        }
    }

    /**
     * Write a single real-time alarm record to TDengine.
     * Called from the Redis pub/sub listener when Flink confirms an alarm.
     */
    public void writeRealtimeAlarm(PointAlarmState state) {
        asyncExecutor.submit(() -> {
            try {
                if (!ensureConnected()) {
                    log.warn("TDengine not available, dropping realtime alarm: {}", state.getPath());
                    return;
                }
                writeToRealtimeSupertable(state);
                log.debug("Wrote realtime alarm to TDengine: {}", state.getPath());
            } catch (Exception e) {
                log.error("Failed to write realtime alarm for path: {}", state.getPath(), e);
            }
        });
    }

    /**
     * Write a single PointAlarmState to the snapshot supertable.
     */
    private void writeToSnapshotSupertable(PointAlarmState state) throws SQLException {
        String supertable = tdengineConfig.getSupertable();
        String path = state.getPath();
        String subtable = sanitizeTableName(path);

        // TAGS(path) + 17 VALUES columns
        String sql = "INSERT INTO " + subtable + " USING " + supertable
                + " TAGS (?) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)";

        try (PreparedStatement pstmt = connection.prepareStatement(sql)) {
            // TAG
            pstmt.setString(1, path);

            // VALUES
            pstmt.setTimestamp(2, new Timestamp(System.currentTimeMillis()));
            Long dataTimestamp = state.getTimestamp();
            pstmt.setTimestamp(3, dataTimestamp != null ? new Timestamp(dataTimestamp) : new Timestamp(System.currentTimeMillis()));
            pstmt.setString(4, state.getValue());
            pstmt.setString(5, state.getType());
            pstmt.setString(6, state.getLevel());
            pstmt.setString(7, state.getSeverity());
            pstmt.setString(8, state.getMessage());
            pstmt.setString(9, state.getTimeDelay() != null ? String.valueOf(state.getTimeDelay()) : null);
            pstmt.setString(10, state.getDeadband());
            pstmt.setString(11, state.getAckBy());
            pstmt.setString(12, state.getStatus());
            pstmt.setString(13, state.getSource());
            pstmt.setString(14, state.getLocation());
            pstmt.setString(15, state.getStage());
            pstmt.setString(16, state.getStageMessage());
            pstmt.setString(17, state.getDuration());
            Long alarmStart = state.getAlarmStartTime();
            pstmt.setTimestamp(18, alarmStart != null ? new Timestamp(alarmStart) : null);

            pstmt.executeUpdate();
        }
    }

    /**
     * Write a single confirmed alarm record to the realtime supertable.
     */
    private void writeToRealtimeSupertable(PointAlarmState state) throws SQLException {
        String supertable = tdengineConfig.getRealtimeSupertable();
        String path = state.getPath();
        String subtable = sanitizeTableName(path) + "_rt";

        // TAGS(path) + 17 VALUES columns
        String sql = "INSERT INTO " + subtable + " USING " + supertable
                + " TAGS (?) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)";

        try (PreparedStatement pstmt = connection.prepareStatement(sql)) {
            // TAG
            pstmt.setString(1, path);

            // VALUES
            pstmt.setTimestamp(2, new Timestamp(System.currentTimeMillis()));
            Long dataTimestamp = state.getTimestamp();
            pstmt.setTimestamp(3, dataTimestamp != null ? new Timestamp(dataTimestamp) : new Timestamp(System.currentTimeMillis()));
            pstmt.setString(4, state.getValue());
            pstmt.setString(5, state.getType());
            pstmt.setString(6, state.getLevel());
            pstmt.setString(7, state.getSeverity());
            pstmt.setString(8, state.getMessage());
            pstmt.setString(9, state.getTimeDelay() != null ? String.valueOf(state.getTimeDelay()) : null);
            pstmt.setString(10, state.getDeadband());
            pstmt.setString(11, state.getAckBy());
            pstmt.setString(12, state.getStatus());
            pstmt.setString(13, state.getSource());
            pstmt.setString(14, state.getLocation());
            pstmt.setString(15, state.getStage());
            pstmt.setString(16, state.getStageMessage());
            pstmt.setString(17, state.getDuration());
            Long alarmStart = state.getAlarmStartTime();
            pstmt.setTimestamp(18, alarmStart != null ? new Timestamp(alarmStart) : null);

            pstmt.executeUpdate();
        }
    }

    // ═══════════════════════════════════════════════════════════════════════
    //  Query methods (read from TDengine)
    // ═══════════════════════════════════════════════════════════════════════

    private static final DateTimeFormatter TD_TIME_FORMAT =
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    /**
     * Query all alarm records from the alarm_realtime supertable.
     * Returns the most recent 200 records, ordered by data_timestamp descending
     * (newest first), mirroring the Active Alarms default ordering.
     */
    public List<AlarmRecordDto> queryClearedAlarms() {
        List<AlarmRecordDto> result = new ArrayList<>();
        if (!ensureConnected()) {
            log.warn("TDengine not available, cannot query alarm history");
            return result;
        }

        String sql = "SELECT data_timestamp, alarm_level, alarm_severity, alarm_source, " +
                "alarm_message, alarm_location, alarm_status, ack_by, alarm_duration, " +
                "alarm_stage, alarm_stage_message, alarm_start_time, path FROM alarm_realtime " +
                "ORDER BY data_timestamp DESC LIMIT 200";

        try (Statement stmt = connection.createStatement();
             ResultSet rs = stmt.executeQuery(sql)) {
            while (rs.next()) {
                AlarmRecordDto dto = new AlarmRecordDto();
                Timestamp dataTs = rs.getTimestamp("data_timestamp");
                if (dataTs != null) {
                    dto.setTime(LocalDateTime.ofInstant(
                            Instant.ofEpochMilli(dataTs.getTime()), ZoneId.systemDefault())
                            .format(TD_TIME_FORMAT));
                } else {
                    dto.setTime("-");
                }
                dto.setLevel(rs.getString("alarm_level"));
                dto.setSeverity(rs.getString("alarm_severity"));
                dto.setSource(rs.getString("alarm_source"));
                dto.setMessage(rs.getString("alarm_message"));
                dto.setLocation(rs.getString("alarm_location"));
                dto.setStatus(rs.getString("alarm_status"));
                dto.setAckBy(rs.getString("ack_by"));
                dto.setDuration(rs.getString("alarm_duration"));
                dto.setStage(rs.getString("alarm_stage"));
                dto.setStageMessage(rs.getString("alarm_stage_message"));
                Timestamp startTs = rs.getTimestamp("alarm_start_time");
                if (startTs != null) {
                    dto.setAlarmStartTime(LocalDateTime.ofInstant(
                            Instant.ofEpochMilli(startTs.getTime()), ZoneId.systemDefault())
                            .format(TD_TIME_FORMAT));
                } else {
                    dto.setAlarmStartTime("-");
                }
                dto.setPath(rs.getString("path"));
                result.add(dto);
            }
        } catch (SQLException e) {
            log.error("Failed to query alarm history from TDengine", e);
            closeConnection();
        }

        log.info("Queried {} alarm history records from TDengine", result.size());
        return result;
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
     * Close current connection quietly.
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
     * Cleanup resources on shutdown.
     */
    @PreDestroy
    public void destroy() {
        asyncExecutor.shutdownNow();
        closeConnection();
        log.info("TDengine sync service shut down");
    }
}
