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
import java.util.concurrent.ThreadFactory;
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
 * Driver: REST mode ({@code jdbc:TAOS-RS}) via taos-jdbcdriver - no native client
 * library required. WebSocket mode ({@code jdbc:TAOS-WS}) stays available by URL
 * change only, but measured slower for this workload (the WS fetch protocol adds
 * round trips per query and plain text SQL gains nothing from WS binary binding);
 * revisit it together with a STMT2 prepared-statement migration.
 * Schema bootstrap: a fresh (or dropped) database cannot be created through a
 * full-URL connection - WS refuses the connection itself and REST cannot reach
 * the database through its URL scope - so the database and supertables are
 * created once per process over a db-less connection with fully qualified DDL
 * before the first full-URL connect.
 * <p>
 * Concurrency model: two independent write lanes, each a single-writer
 * executor owning its own long-lived JDBC connection ({@link WriteChannel}).
 * Bulk event flushes can therefore never delay or displace alarm lifecycle
 * writes, and one lane's connection failure cannot poison the other. Read
 * queries borrow connections from a small dedicated HikariCP pool and never
 * touch either write lane. Read failures deliberately do NOT close write
 * connections - reads and writes fail independently.
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

    /**
     * Serializes the one-time schema bootstrap (CREATE DATABASE / STABLE) when
     * both write lanes initialize concurrently; the DDL itself is idempotent.
     */
    private static final Object SCHEMA_LOCK = new Object();

    /**
     * Schema bootstrap flag. The target database may not exist yet when a lane
     * first connects (fresh install, dropped database), and both driver modes
     * refuse to work against a missing URL database: WS fails the connection
     * itself, REST silently cannot create through a URL-scoped connection. The
     * bootstrap therefore runs once over a db-less connection with fully
     * qualified DDL before the first full-URL connect.
     */
    private volatile boolean schemaReady;

    /** Bounded queue of pending realtime alarm writes; overflow drops with an error log. */
    private static final int REALTIME_QUEUE_CAPACITY = 1000;

    /**
     * Pending flush tasks. Deliberately tiny: each queued task would re-drain
     * the buffer into memory, and the frames' true shock absorber is the
     * bounded {@link #persistenceBuffer}; overflow just skips a tick.
     */
    private static final int EVENT_QUEUE_CAPACITY = 2;

    /**
     * Bounded buffer of pending full-detail frames awaiting the next batch flush.
     * Large enough to absorb a burst between flush ticks; a full buffer drops
     * new frames (with a warning) rather than growing without limit.
     */
    private static final int PERSISTENCE_BUFFER_CAPACITY = 5000;

    /** Frames published by Flink to {@code alarm:persist}, pending batch flush. */
    private final ArrayBlockingQueue<PointAlarmState> persistenceBuffer =
            new ArrayBlockingQueue<>(PERSISTENCE_BUFFER_CAPACITY);

    /** Bulk event flushes; slow when TDengine lags, but must not block alarms. */
    private final ExecutorService eventExecutor = new ThreadPoolExecutor(
            1, 1, 0L, TimeUnit.MILLISECONDS,
            new ArrayBlockingQueue<>(EVENT_QUEUE_CAPACITY),
            namedDaemonFactory("tdengine-event-writer"),
            (r, executor) -> log.warn(
                    "TDengine event flush is still busy, skipping this flush tick; "
                            + "frames stay buffered (capacity {})", PERSISTENCE_BUFFER_CAPACITY));

    /** Alarm lifecycle writes; small volume, latency-sensitive, must not be displaced. */
    private final ExecutorService realtimeExecutor = new ThreadPoolExecutor(
            1, 1, 0L, TimeUnit.MILLISECONDS,
            new ArrayBlockingQueue<>(REALTIME_QUEUE_CAPACITY),
            namedDaemonFactory("tdengine-realtime-writer"),
            (r, executor) -> log.error(
                    "TDengine realtime write queue is full (capacity {}), dropping an alarm "
                            + "lifecycle write; TDengine is falling behind or unreachable",
                    REALTIME_QUEUE_CAPACITY));

    private final WriteChannel eventChannel = new WriteChannel("event");
    private final WriteChannel realtimeChannel = new WriteChannel("realtime");

    /** Small read pool; created lazily so a missing TDengine does not slow startup. */
    private volatile HikariDataSource readPool;

    private static ThreadFactory namedDaemonFactory(String name) {
        return r -> {
            Thread t = new Thread(r, name);
            t.setDaemon(true);
            return t;
        };
    }

    /**
     * One dedicated write lane: a single-writer executor thread owning a
     * long-lived JDBC connection. JDBC connections are not thread-safe, so the
     * connection is used exclusively by the lane's own thread.
     */
    private final class WriteChannel {
        private final String name;
        private Connection connection;

        private WriteChannel(String name) {
            this.name = name;
        }

        /**
         * Ensure the lane connection is alive; reconnect if needed. Before the
         * first connect the schema is bootstrapped over a db-less connection
         * (see {@link #schemaReady}), because both driver modes cannot operate
         * against the missing URL database.
         *
         * @return true if the connection is usable
         */
        private synchronized boolean ensureConnected() {
            try {
                if (connection != null && !connection.isClosed()) {
                    return true;
                }
            } catch (SQLException e) {
                log.warn("TDengine connection check failed on lane '{}', will reconnect", name, e);
            }

            try {
                ensureSchema();
                log.info("Connecting to TDengine on lane '{}': {}", name, tdengineConfig.getUrl());
                connection = DriverManager.getConnection(tdengineConfig.getUrl(),
                        tdengineConfig.getUsername(), tdengineConfig.getPassword());
                log.info("Connected to TDengine on lane '{}'", name);
                return true;
            } catch (Exception e) {
                log.error("Failed to connect to TDengine on lane '{}'", name, e);
                connection = null;
                // A "database not exist" failure means the schema vanished (or was
                // dropped underneath a running instance); re-bootstrap next attempt.
                if (String.valueOf(e.getMessage()).toLowerCase().contains("database not exist")) {
                    schemaReady = false;
                }
                return false;
            }
        }

        private int executeUpdate(String sql) throws SQLException {
            try (Statement stmt = connection.createStatement()) {
                return stmt.executeUpdate(sql);
            }
        }

        private synchronized void closeQuietly() {
            if (connection != null) {
                try {
                    connection.close();
                } catch (SQLException ignored) {
                }
                connection = null;
            }
        }
    }

    /**
     * Create the database and both supertables once per process, over a
     * db-less connection using fully qualified names. Both driver modes cannot
     * do this through the full URL while the database is still missing.
     */
    private void ensureSchema() throws SQLException {
        synchronized (SCHEMA_LOCK) {
            if (schemaReady) {
                return;
            }
            String bootstrapUrl = bootstrapUrl(tdengineConfig.getUrl());
            String database = tdengineConfig.getDatabase();
            log.info("Bootstrapping TDengine schema for '{}' via {}", database, bootstrapUrl);
            try (Connection boot = DriverManager.getConnection(bootstrapUrl,
                    tdengineConfig.getUsername(), tdengineConfig.getPassword())) {
                initDatabase(boot);
                initSupertable(boot, database + "." + tdengineConfig.getRealtimeSupertable(), "Realtime");
                initSupertable(boot, database + "." + tdengineConfig.getEventSupertable(), "Event");
            }
            schemaReady = true;
        }
    }

    /**
     * Strip the default-database path segment from a taos-jdbcdriver URL, e.g.
     * {@code jdbc:TAOS-WS://host:6041/n4?x=1} -> {@code jdbc:TAOS-WS://host:6041?x=1}.
     * URLs without a database path are returned unchanged.
     */
    static String bootstrapUrl(String url) {
        int schemeEnd = url.indexOf("://");
        if (schemeEnd < 0) {
            return url;
        }
        String prefix = url.substring(0, schemeEnd + 3);
        String rest = url.substring(prefix.length());
        int slash = rest.indexOf('/');
        if (slash < 0 || slash == rest.length() - 1) {
            return url;
        }
        String query = "";
        int queryStart = rest.indexOf('?');
        if (queryStart >= 0) {
            query = rest.substring(queryStart);
            rest = rest.substring(0, queryStart);
            slash = rest.indexOf('/');
            if (slash < 0 || slash == rest.length() - 1) {
                return url;
            }
        }
        return prefix + rest.substring(0, slash) + query;
    }

    /**
     * Create database if not exists, on the given lane connection.
     */
    private void initDatabase(Connection connection) throws SQLException {
        String database = tdengineConfig.getDatabase();
        // BUFFER is the per-vnode write memory pool in MB. This CREATE only
        // applies to a fresh database (IF NOT EXISTS never re-applies to an
        // existing one - resize those with ALTER DATABASE). KEEP 1095 = 3
        // years of retention; BUFFER 256 aligns with the TDengine 3.4 default.
        // No USE here: the bootstrap runs on a db-less connection with fully
        // qualified supertable names, and lane connections get the database
        // context from the URL path.
        String sql = "CREATE DATABASE IF NOT EXISTS " + database + " KEEP 1095 DURATION 10 BUFFER 256";
        try (Statement stmt = connection.createStatement()) {
            stmt.execute(sql);
            log.info("Database '{}' created/verified", database);
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
    private void initSupertable(Connection connection, String supertable, String label) throws SQLException {
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
        // Drain inside the writer task so a skipped tick loses nothing: frames
        // simply stay in the bounded buffer until a later tick. Connect before
        // draining so a TDengine outage does not empty the buffer into the void.
        eventExecutor.submit(() -> {
            if (!eventChannel.ensureConnected()) {
                log.error("TDengine not available, leaving {} frame(s) buffered",
                        persistenceBuffer.size());
                return;
            }
            List<PointAlarmState> batch = new ArrayList<>(PERSISTENCE_BUFFER_CAPACITY);
            persistenceBuffer.drainTo(batch);
            if (batch.isEmpty()) {
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
            try {
                written += eventChannel.executeUpdate(chunk.sql());
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
                if (!eventChannel.ensureConnected()) {
                    failed++;
                    continue;
                }
                writeSingleRow(eventChannel, supertable, subtableSuffix, state);
            } catch (SQLException e) {
                failed++;
                log.error("SQL error writing event path '{}': {}", state.getMqttPath(), e.getMessage());
                eventChannel.closeQuietly();
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
        realtimeExecutor.submit(() -> {
            try {
                if (!realtimeChannel.ensureConnected()) {
                    log.error("TDengine not available, dropping realtime alarm: path={}, stage={}",
                            state.getMqttPath(), state.getStage());
                    return;
                }
                writeSingleRow(realtimeChannel, tdengineConfig.getRealtimeSupertable(), "_rt", state);
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
    private void writeSingleRow(WriteChannel channel, String supertable, String subtableSuffix,
                                PointAlarmState state) throws SQLException {
        List<TdengineSql.Chunk> chunks = TdengineSql.buildChunks(supertable, subtableSuffix, List.of(state));
        channel.executeUpdate(chunks.get(0).sql());
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
            // Deliberately no write-lane close here: reads own their pooled
            // connections and must never tear down a writer's connection.
            log.error("Failed to query alarm history from TDengine", e);
        }

        log.info("Queried {} alarm history records from TDengine", result.size());
        return result;
    }

    /** Max rows per event history page; the frontend asks for smaller pages. */
    private static final int EVENT_PAGE_MAX_ROWS = 500;
    private static final int EVENT_PAGE_DEFAULT_ROWS = 50;

    /**
     * Query one page of full-detail records from the alarm_event supertable,
     * newest first. Scroll pagination uses a snapshot upper bound plus offset:
     * the frontend pins {@code topTs} to the first page's newest write
     * timestamp and passes it back on every later page, so records written
     * meanwhile (which would otherwise shift the ordering and duplicate rows)
     * are excluded and OFFSET stays stable. OFFSET is used instead of a
     * {@code ts < cursor} keyset cursor because bursty batch writes put dozens
     * of rows on the same millisecond - a ts cursor would silently skip rows
     * at page borders. Measured cost is flat on 680k rows / 2700 subtables
     * (OFFSET 10000 ≈ 0.3s). Scroll depth is still bounded by the recent-write
     * window ({@code query-window-days}).
     */
    public List<AlarmRecordDto> queryAlarmEvents(Long topTs, Integer offset, Integer limit) {
        int pageLimit = limit == null ? EVENT_PAGE_DEFAULT_ROWS
                : Math.max(1, Math.min(limit, EVENT_PAGE_MAX_ROWS));
        int pageOffset = offset == null ? 0 : Math.max(0, offset);

        StringBuilder where = new StringBuilder();
        int windowDays = tdengineConfig.getQueryWindowDays();
        if (windowDays > 0) {
            where.append(" WHERE ts > NOW() - ").append(windowDays).append('d');
        }
        if (topTs != null) {
            where.append(where.length() == 0 ? " WHERE" : " AND").append(" ts <= ").append(topTs);
        }

        String sql = "SELECT ts, data_timestamp, point_value, alarm_level, alarm_severity, alarm_source, " +
                "alarm_message, alarm_location, alarm_status, ack_by, alarm_duration, " +
                "alarm_stage, alarm_stage_message, alarm_start_time, path FROM " + tdengineConfig.getEventSupertable() +
                where +
                // Same ordering contract as queryClearedAlarms: indexed primary ts,
                // later-written rows first, data_timestamp breaks full ties.
                " ORDER BY ts DESC, data_timestamp DESC LIMIT " + pageLimit + " OFFSET " + pageOffset;

        List<AlarmRecordDto> result = new ArrayList<>();

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
                Timestamp writeTs = rs.getTimestamp("ts");
                if (writeTs != null) {
                    dto.setTs(writeTs.getTime());
                }
                dto.setPath(rs.getString("path"));
                result.add(dto);
            }
        } catch (SQLException e) {
            // Same as queryClearedAlarms: never tear down a write lane.
            log.error("Failed to query alarm events from TDengine", e);
        }

        log.info("Queried {} alarm event records from TDengine (limit {}, offset {})",
                result.size(), pageLimit, pageOffset);
        return result;
    }

    /**
     * Borrow a read connection from the dedicated read pool. Reads never share
     * a write lane's long-lived connection: JDBC connections are not
     * thread-safe and each writer thread owns its connection exclusively.
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
        // Avoid isValid() support differences of the taos-jdbcdriver modes.
        cfg.setConnectionTestQuery("SELECT SERVER_VERSION()");
        // Do not probe TDengine at pool creation; queries fail gracefully instead.
        cfg.setInitializationFailTimeout(-1);
        return new HikariDataSource(cfg);
    }

    /**
     * Cleanup resources on shutdown.
     */
    @PreDestroy
    public void destroy() {
        realtimeExecutor.shutdownNow();
        eventExecutor.shutdownNow();
        realtimeChannel.closeQuietly();
        eventChannel.closeQuietly();
        if (readPool != null) {
            readPool.close();
        }
        log.info("TDengine sync service shut down");
    }
}
