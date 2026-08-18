package com.n4monitor.tdengine;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.n4monitor.alarm.dto.AlarmRecordDto;
import com.n4monitor.alarm.model.PointAlarmState;
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
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

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
 * Concurrency model: JDBC connections are not thread-safe, so the long-lived
 * write connection is owned exclusively by the single writer thread below;
 * read queries open short-lived connections of their own and never touch it.
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

    /** Bounded so a slow or unreachable TDengine cannot grow the queue without limit. */
    private static final int WRITE_QUEUE_CAPACITY = 1000;

    private final ExecutorService asyncExecutor = new ThreadPoolExecutor(
            1, 1, 0L, TimeUnit.MILLISECONDS,
            new ArrayBlockingQueue<>(WRITE_QUEUE_CAPACITY),
            r -> {
                Thread t = new Thread(r, "tdengine-async-writer");
                t.setDaemon(true);
                return t;
            },
            (r, executor) -> log.error(
                    "TDengine write queue is full (capacity {}), dropping a write task; "
                            + "TDengine is falling behind or unreachable", WRITE_QUEUE_CAPACITY));

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
                initSupertable(tdengineConfig.getSupertable(), "Snapshot");
                initSupertable(tdengineConfig.getRealtimeSupertable(), "Realtime");
                initialized = true;
                log.info("TDengine database and supertables initialized");
            }
            return true;
        } catch (Exception e) {
            log.error("Failed to connect to TDengine", e);
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
     * Create a supertable if not exists. Both supertables share one schema:
     * the snapshot table (latest state per point, 5s cadence) and the realtime
     * table (one row per alarm lifecycle event) differ only by name.
     * <p>
     * Schema has been slimmed to match the new Kafka message format (only
     * clientId/path/timestamp/value + resolved type). Removed columns:
     * unit, max_val, min_val, ip_addr, port_num, other; removed tags: name,
     * display_name (path is now the only tag and the unique point key).
     * Added column: alarm_stage_message (alarm state transition label).
     */
    private void initSupertable(String supertable, String label) throws SQLException {
        String sql = "CREATE STABLE IF NOT EXISTS " + supertable + " ("
                + "ts TIMESTAMP, "
                + "data_timestamp TIMESTAMP, "
                + "point_value NCHAR(64), "
                + "point_type NCHAR(32), "
                + "alarm_level NCHAR(32), "
                + "alarm_severity NCHAR(32), "
                + "alarm_message NCHAR(512), "
                + "time_delay NCHAR(32), "
                + "deadband NCHAR(64), "
                + "ack_by NCHAR(32), "
                + "alarm_status NCHAR(32), "
                + "alarm_source NCHAR(256), "
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
            log.info("{} supertable '{}' created/verified", label, supertable);
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
                writeAlarmRow(tdengineConfig.getSupertable(), "", state);
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

        if (failCount > 0) {
            log.error("TDengine snapshot sync had failures: {} succeeded, {} failed", successCount, failCount);
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
                    log.error("TDengine not available, dropping realtime alarm: path={}, stage={}",
                            state.getMqttPath(), state.getStage());
                    return;
                }
                writeAlarmRow(tdengineConfig.getRealtimeSupertable(), "_rt", state);
                log.info("Wrote realtime alarm to TDengine: path={}, stage={}, level={}",
                        state.getMqttPath(), state.getStage(), state.getLevel());
            } catch (Exception e) {
                log.error("Failed to write realtime alarm for path: {}, stage: {}",
                        state.getMqttPath(), state.getStage(), e);
            }
        });
    }

    /**
     * Write a single PointAlarmState row into one of the two supertables
     * (shared schema; they differ only by supertable name and subtable suffix).
     *
     * @param supertable     target supertable name
     * @param subtableSuffix appended to the sanitized path to build the
     *                       per-point subtable name ("" for snapshot, "_rt" for realtime)
     */
    private void writeAlarmRow(String supertable, String subtableSuffix, PointAlarmState state) throws SQLException {
        String path = state.getMqttPath();
        String subtable = sanitizeTableName(path) + subtableSuffix;

        // TAGS(path) + 17 VALUES columns
        String sql = "INSERT INTO " + subtable + " USING " + supertable
                + " TAGS (?) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)";

        try (PreparedStatement pstmt = connection.prepareStatement(sql)) {
            // TAG
            pstmt.setString(1, path);

            // VALUES
            // Timestamps are bound as raw epoch-millis longs (setLong) instead of
            // java.sql.Timestamp (setTimestamp). Under the REST driver (jdbc:TAOS-RS)
            // setTimestamp serializes the value to a naive datetime string using the
            // JVM local timezone, while the TDengine server parses it as UTC - an
            // 8-hour mismatch when the two zones differ. Passing the millis directly
            // lets TDengine interpret it as an absolute UTC instant, timezone-safe.
            pstmt.setLong(2, System.currentTimeMillis());
            Long dataTimestamp = state.getTimestamp();
            pstmt.setLong(3, dataTimestamp != null ? dataTimestamp : System.currentTimeMillis());
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
            // alarm_start_time is NULL for non-alarm rows (no alarm has begun).
            // Use setNull so TDengine stores a true NULL instead of epoch 0
            // (which displays as 1970-01-01). Non-null values still use setLong
            // for timezone-safe epoch-millis binding.
            if (alarmStart != null) {
                pstmt.setLong(18, alarmStart);
            } else {
                pstmt.setNull(18, Types.TIMESTAMP);
            }

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

        String sql = "SELECT data_timestamp, alarm_level, alarm_severity, alarm_source, " +
                "alarm_message, alarm_location, alarm_status, ack_by, alarm_duration, " +
                "alarm_stage, alarm_stage_message, alarm_start_time, path FROM " + tdengineConfig.getRealtimeSupertable() + " " +
                // ts (write moment) is a stable tiebreaker: when two records share the same
                // data_timestamp (e.g. an End + Start written in the same tick during a level
                // escalation), ts breaks the tie by insertion order so the later-written
                // Start always sorts above the earlier-written End.
                "ORDER BY data_timestamp DESC, ts DESC LIMIT 200";

        try (Connection readConn = openReadConnection();
             Statement stmt = readConn.createStatement();
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
     * Open a short-lived connection for read-only queries. Reads never share
     * the writer's long-lived connection: JDBC connections are not thread-safe
     * and the writer thread owns its connection exclusively.
     */
    private Connection openReadConnection() throws SQLException {
        return DriverManager.getConnection(
                tdengineConfig.getUrl(), tdengineConfig.getUsername(), tdengineConfig.getPassword());
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
