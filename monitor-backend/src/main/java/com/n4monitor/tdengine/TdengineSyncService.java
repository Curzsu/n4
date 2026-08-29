package com.n4monitor.tdengine;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.n4monitor.alarm.dto.AlarmRecordDto;
import com.n4monitor.alarm.model.PointAlarmState;
import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import jakarta.annotation.PreDestroy;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.sql.*;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

/**
 * TDengine data synchronization service.
 * <p>
 * Provides two persistence paths:
 * <ol>
 *   <li>Full-detail event persistence: Flink publishes every processed point
 *       frame to Redis channel {@code alarm:persist}; the backend listener
 *       enqueues frames into a bounded buffer which is drained every second
 *       and written as chunked multi-row INSERT statements to supertable
 *       {@code alarm_event}.
 *   <li>Real-time alarm records: writes confirmed alarm lifecycle events to
 *       supertable {@code alarm_realtime} immediately when Flink publishes them.
 * </ol>
 * <p>
 * Uses REST mode (jdbc:TAOS-RS) so no native TDengine client is required.
 * Connection is established lazily on the first access.
 * <p>
 * Concurrency model: JDBC connections are not thread-safe, so the long-lived
 * write connection is owned exclusively by the single writer thread below;
 * read queries borrow connections from a small dedicated HikariCP pool and
 * never touch the writer connection. Read failures deliberately do NOT close
 * the writer connection - reads and writes fail independently.
 * <p>
 * Writes are batched: frames are grouped by point subtable into chunked
 * multi-table INSERT statements (see {@link TdengineSql}), so a burst
 * translates into a handful of round trips instead of one per frame. A
 * statement-level failure falls back to row-by-row writes for that chunk only,
 * so one poison row cannot silently drop the frames around it.
 * <p>
 * Subtable naming: each point has its own subtable named by the sanitized path field.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class TdengineSyncService {

    private final TdengineConfig tdengineConfig;
    private final ObjectMapper objectMapper;

    private volatile Connection connection;
    private volatile boolean initialized = false;

    /** Small read pool; created lazily so a missing TDengine does not slow startup. */
    private volatile HikariDataSource readPool;

    /** Bounded so a slow or unreachable TDengine cannot grow the queue without limit. */
    private static final int WRITE_QUEUE_CAPACITY = 1000;

    /**
     * Bounded buffer of pending full-detail frames awaiting the next batch flush.
     * Large enough to absorb a burst between flush ticks; a full buffer drops
     * new frames (with a warning) rather than growing without limit.
     */
    private static final int PERSISTENCE_BUFFER_CAPACITY = 5000;

    /** Frames published by Flink to {@code alarm:persist}, pending batch flush. */
    private final ArrayBlockingQueue<PointAlarmState> persistenceBuffer =
            new ArrayBlockingQueue<>(PERSISTENCE_BUFFER_CAPACITY);

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
                initSupertable(tdengineConfig.getRealtimeSupertable(), "Realtime");
                initSupertable(tdengineConfig.getEventSupertable(), "Event");
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
     * the realtime table (one row per alarm lifecycle event) and the event
     * table (one row per processed point frame) differ only by name.
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
     * Enqueue one processed point frame for full-detail persistence.
     * Called from {@code AlarmPersistenceListener} (Redis pub/sub {@code alarm:persist})
     * and from {@code AlarmService} when a user acknowledges an alarm.
     * Frames are batched and flushed every second by {@link #flushPersistenceBatch()}.
     */
    public void enqueuePersistence(PointAlarmState state) {
        if (state == null || state.getMqttPath() == null) {
            return;
        }
        if (!persistenceBuffer.offer(state)) {
            log.warn("Persistence buffer is full (capacity {}), dropping a frame for path={}; "
                            + "TDengine is falling behind or unreachable",
                    PERSISTENCE_BUFFER_CAPACITY, state.getMqttPath());
        }
    }

    /**
     * Periodic flush of buffered full-detail frames into the {@code alarm_event}
     * supertable. Runs every second; a flush with nothing buffered is a no-op
     * (no SQL issued), so idle points cost nothing.
     */
    @Scheduled(fixedRateString = "${tdengine.event-flush-rate-ms:1000}",
            initialDelayString = "${tdengine.event-flush-initial-delay-ms:3000}")
    public void flushPersistenceBatch() {
        if (persistenceBuffer.isEmpty()) {
            return;
        }
        List<PointAlarmState> batch = new ArrayList<>(PERSISTENCE_BUFFER_CAPACITY);
        persistenceBuffer.drainTo(batch);
        if (batch.isEmpty()) {
            return;
        }
        asyncExecutor.submit(() -> {
            if (!ensureConnected()) {
                log.error("TDengine not available, dropping a persistence batch of {} frames",
                        batch.size());
                return;
            }
            writeEventBatch(batch, tdengineConfig.getEventSupertable(), "_ev");
        });
    }

    /**
     * Write a drained buffer batch to one supertable using chunked multi-row
     * INSERT statements. On a statement-level SQL failure only that chunk falls
     * back to row-by-row writes, so one malformed row cannot silently drop the
     * frames of neighbouring chunks (and already-written chunks are not rewritten).
     */
    private void writeEventBatch(List<PointAlarmState> batch, String supertable, String subtableSuffix) {
        List<TdengineSql.Chunk> chunks = TdengineSql.buildChunks(supertable, subtableSuffix, batch);
        int written = 0;
        int failed = 0;
        for (TdengineSql.Chunk chunk : chunks) {
            try (Statement stmt = connection.createStatement()) {
                written += stmt.executeUpdate(chunk.sql());
            } catch (SQLException e) {
                log.warn("Batched insert of {} rows failed, falling back to row-by-row: {}",
                        chunk.rows().size(), e.getMessage());
                failed += writeRowsIndividually(chunk.rows(), supertable, subtableSuffix);
            }
        }
        log.debug("Flushed persistence batch: {} rows written, {} failed via {} chunk(s)",
                written, failed, chunks.size());
    }

    /**
     * Row-by-row fallback for a failed chunk. Reconnects lazily so a dead
     * connection does not fail every remaining row.
     *
     * @return number of failed rows
     */
    private int writeRowsIndividually(List<PointAlarmState> rows, String supertable, String subtableSuffix) {
        int failed = 0;
        for (PointAlarmState state : rows) {
            try {
                if (!ensureConnected()) {
                    failed++;
                    continue;
                }
                writeSingleRow(supertable, subtableSuffix, state);
            } catch (SQLException e) {
                failed++;
                log.error("SQL error writing event path '{}': {}", state.getMqttPath(), e.getMessage());
                closeConnection();
            } catch (Exception e) {
                failed++;
                log.error("Failed to write event for path: {}", state.getMqttPath(), e);
            }
        }
        return failed;
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
                writeSingleRow(tdengineConfig.getRealtimeSupertable(), "_rt", state);
                log.info("Wrote realtime alarm to TDengine: path={}, stage={}, level={}",
                        state.getMqttPath(), state.getStage(), state.getLevel());
            } catch (Exception e) {
                log.error("Failed to write realtime alarm for path: {}, stage: {}",
                        state.getMqttPath(), state.getStage(), e);
            }
        });
    }

    /**
     * Write one row into a supertable as a single-table single-row INSERT
     * (shared SQL builder with the batched path).
     */
    private void writeSingleRow(String supertable, String subtableSuffix, PointAlarmState state) throws SQLException {
        List<TdengineSql.Chunk> chunks = TdengineSql.buildChunks(supertable, subtableSuffix, List.of(state));
        try (Statement stmt = connection.createStatement()) {
            stmt.executeUpdate(chunks.get(0).sql());
        }
    }

    // ═══════════════════════════════════════════════════════════════════════
    //  Query methods (read from TDengine)
    // ═══════════════════════════════════════════════════════════════════════

    private static final DateTimeFormatter TD_TIME_FORMAT =
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    /**
     * Recent-write window filter for history queries. Rows are ordered by the
     * write timestamp (the indexed primary ts), so pruning by ts keeps the scan
     * inside the window instead of the full KEEP range. 0 disables the filter.
     */
    private String recentWindowClause() {
        int windowDays = tdengineConfig.getQueryWindowDays();
        if (windowDays <= 0) {
            return "";
        }
        return " WHERE ts > NOW() - " + windowDays + "d";
    }

    /**
     * Query all alarm records from the alarm_realtime supertable.
     * Returns the most recent 200 records, ordered by the write timestamp
     * descending (newest first), mirroring the Active Alarms default ordering.
     */
    public List<AlarmRecordDto> queryClearedAlarms() {
        List<AlarmRecordDto> result = new ArrayList<>();

        String sql = "SELECT data_timestamp, alarm_level, alarm_severity, alarm_source, " +
                "alarm_message, alarm_location, alarm_status, ack_by, alarm_duration, " +
                "alarm_stage, alarm_stage_message, alarm_start_time, path FROM " + tdengineConfig.getRealtimeSupertable() +
                recentWindowClause() +
                // Order by the write timestamp: the indexed primary ts, and the same
                // insertion-order tiebreaker this query previously used as secondary
                // key. When two records share the same data_timestamp (e.g. an End +
                // Start written in the same tick during a level escalation), the
                // later-written Start always sorts above the earlier-written End.
                // data_timestamp stays as a secondary key for fully tied rows.
                " ORDER BY ts DESC, data_timestamp DESC LIMIT 200";

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
            // Deliberately no closeConnection() here: reads own their pooled
            // connections and must never tear down the writer's connection.
            log.error("Failed to query alarm history from TDengine", e);
        }

        log.info("Queried {} alarm history records from TDengine", result.size());
        return result;
    }

    /**
     * Query all full-detail records from the alarm_event supertable.
     * Uses the same query shape as {@link #queryClearedAlarms()} but targets the
     * event table (one row per processed point frame) instead of alarm_realtime.
     * Returns the most recent 200 records, newest first.
     */
    public List<AlarmRecordDto> queryAlarmEvents() {
        List<AlarmRecordDto> result = new ArrayList<>();

        String sql = "SELECT data_timestamp, point_value, alarm_level, alarm_severity, alarm_source, " +
                "alarm_message, alarm_location, alarm_status, ack_by, alarm_duration, " +
                "alarm_stage, alarm_stage_message, alarm_start_time, path FROM " + tdengineConfig.getEventSupertable() +
                recentWindowClause() +
                " ORDER BY ts DESC, data_timestamp DESC LIMIT 200";

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
                dto.setValue(rs.getString("point_value"));
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
            // Same as queryClearedAlarms: never tear down the writer connection.
            log.error("Failed to query alarm events from TDengine", e);
        }

        log.info("Queried {} alarm event records from TDengine", result.size());
        return result;
    }

    /**
     * Borrow a read connection from the dedicated read pool. Reads never share
     * the writer's long-lived connection: JDBC connections are not thread-safe
     * and the writer thread owns its connection exclusively.
     */
    private Connection openReadConnection() throws SQLException {
        HikariDataSource pool = readPool;
        if (pool == null) {
            synchronized (this) {
                if (readPool == null) {
                    readPool = buildReadPool();
                }
                pool = readPool;
            }
        }
        return pool.getConnection();
    }

    private HikariDataSource buildReadPool() {
        HikariConfig cfg = new HikariConfig();
        cfg.setPoolName("tdengine-read");
        cfg.setJdbcUrl(tdengineConfig.getUrl());
        cfg.setUsername(tdengineConfig.getUsername());
        cfg.setPassword(tdengineConfig.getPassword());
        // Reads come in short bursts from HTTP threads; a small pool caps the
        // concurrent query load TDengine sees during write bursts.
        cfg.setMaximumPoolSize(4);
        cfg.setMinimumIdle(0);
        cfg.setConnectionTimeout(5_000);
        cfg.setIdleTimeout(60_000);
        cfg.setMaxLifetime(300_000);
        // Avoid isValid() support differences of the REST driver.
        cfg.setConnectionTestQuery("SELECT SERVER_VERSION()");
        // Do not probe TDengine at pool creation; queries fail gracefully instead.
        cfg.setInitializationFailTimeout(-1);
        return new HikariDataSource(cfg);
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
        if (readPool != null) {
            readPool.close();
        }
        log.info("TDengine sync service shut down");
    }
}
