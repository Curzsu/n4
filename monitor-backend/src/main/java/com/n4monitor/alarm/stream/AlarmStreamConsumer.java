package com.n4monitor.alarm.stream;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.n4monitor.alarm.model.PointAlarmState;
import com.n4monitor.tdengine.TdengineSyncService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.SmartLifecycle;
import org.springframework.data.redis.connection.stream.Consumer;
import org.springframework.data.redis.connection.stream.MapRecord;
import org.springframework.data.redis.connection.stream.ReadOffset;
import org.springframework.data.redis.connection.stream.StreamOffset;
import org.springframework.data.redis.connection.stream.StreamReadOptions;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Reliable consumer for the two alarm persistence Streams that replaced the
 * former {@code alarm:realtime} / {@code alarm:persist} Pub/Sub channels.
 * <p>
 * Why Streams: Pub/Sub is fire-and-forget - a message published while the
 * backend is down, restarting or mid-reconnect is gone forever, which silently
 * punched holes into the TDengine audit history (alarm lifecycle records and
 * full-detail point frames). Streams persist every entry in Redis until the
 * consumer acknowledges it after the TDengine write succeeded, giving
 * at-least-once delivery across backend restarts.
 * <p>
 * Delivery contract per lane (one dedicated thread per stream):
 * <ol>
 *   <li>Drain the consumer's own PEL first (XREADGROUP id=0): entries that
 *       were read but not acknowledged - e.g. written by the previous process
 *       run that crashed mid-batch - are redelivered in order.</li>
 *   <li>Then read new entries (XREADGROUP id=&gt;).</li>
 *   <li>Ack only after the TDengine write returned success. A failed write
 *       leaves the entry (and everything behind it, preserving order) in the
 *       PEL and it is retried on the next tick, indefinitely: an audit record
 *       is never given up. TDengine's primary-key upsert on (subtable, ts)
 *       makes these replays idempotent.</li>
 *   <li>A record whose JSON cannot be parsed is acknowledged immediately and
 *       its payload logged at ERROR: it can never become a row, and blocking
 *       the lane forever on it would starve every record behind it.</li>
 * </ol>
 * Requires a single backend instance per group with a FIXED consumer name:
 * restart recovery replays the named consumer's own PEL. Run two instances
 * against one stream only if per-instance names plus XAUTOCLAIM are added.
 * <p>
 * Producer-side trimming: the Flink job (and {@link AlarmStreamPublisher})
 * XADD with approximate MAXLEN, so a long backend outage grows the stream up
 * to the configured cap and then evicts the oldest entries - bounded Redis
 * memory instead of Pub/Sub's unbounded data loss.
 * <p>
 * The {@code alarm:notifications} channel deliberately stays on Pub/Sub
 * (ephemeral UI push handled by {@code RedisPubSubConfig}): replaying stale
 * notifications after downtime has no value, so Streams would only add memory
 * pressure there.
 */
@Slf4j
@Component
public class AlarmStreamConsumer implements SmartLifecycle {

    private final StringRedisTemplate redisTemplate;
    private final TdengineSyncService tdengineSyncService;
    private final ObjectMapper objectMapper;

    private final String realtimeStream;
    private final String persistenceStream;
    private final String group;
    private final String consumerName;
    private final long pollIntervalMs;
    private final int batchSize;

    private final AtomicBoolean running = new AtomicBoolean(false);
    /** Streams whose consumer group is confirmed to exist (lazily created). */
    private final Set<String> groupsReady = new HashSet<>();
    private Thread realtimeThread;
    private Thread persistThread;

    public AlarmStreamConsumer(StringRedisTemplate redisTemplate,
                               TdengineSyncService tdengineSyncService,
                               ObjectMapper objectMapper,
                               @Value("${alarm.redis.realtime-stream}") String realtimeStream,
                               @Value("${alarm.redis.persistence-stream}") String persistenceStream,
                               @Value("${alarm.redis.stream-group:n4-backend}") String group,
                               @Value("${alarm.redis.stream-consumer:n4-backend}") String consumerName,
                               @Value("${alarm.redis.stream-poll-interval-ms:500}") long pollIntervalMs,
                               @Value("${alarm.redis.stream-batch-size:500}") int batchSize) {
        this.redisTemplate = redisTemplate;
        this.tdengineSyncService = tdengineSyncService;
        this.objectMapper = objectMapper;
        this.realtimeStream = realtimeStream;
        this.persistenceStream = persistenceStream;
        this.group = group;
        this.consumerName = consumerName;
        this.pollIntervalMs = pollIntervalMs;
        this.batchSize = batchSize;
    }

    @Override
    public void start() {
        if (!running.compareAndSet(false, true)) {
            return;
        }
        realtimeThread = new Thread(() -> consumeLoop(realtimeStream, true), "alarm-stream-realtime");
        persistThread = new Thread(() -> consumeLoop(persistenceStream, false), "alarm-stream-persist");
        realtimeThread.setDaemon(true);
        persistThread.setDaemon(true);
        realtimeThread.start();
        persistThread.start();
        log.info("Alarm stream consumers started: group='{}' consumer='{}' streams=[{}, {}] "
                        + "pollIntervalMs={} batchSize={}",
                group, consumerName, realtimeStream, persistenceStream, pollIntervalMs, batchSize);
    }

    @Override
    public void stop() {
        if (!running.compareAndSet(true, false)) {
            return;
        }
        for (Thread t : List.of(realtimeThread, persistThread)) {
            if (t == null) {
                continue;
            }
            t.interrupt();
            try {
                t.join(5_000);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }
        log.info("Alarm stream consumers stopped");
    }

    @Override
    public boolean isRunning() {
        return running.get();
    }

    /**
     * One lane's poll loop: PEL-first drain, then new entries. A cycle that
     * moved nothing sleeps one poll interval; a cycle whose write FAILED also
     * sleeps (negative signal) so a TDengine outage cannot turn into a hot
     * reconnect loop. Never throws - a Redis or TDengine outage just keeps
     * the lane retrying (audit records are never abandoned).
     */
    private void consumeLoop(String streamKey, boolean realtimeLane) {
        long idleTicks = 0;
        while (running.get()) {
            try {
                int handled = readAndProcess(streamKey, ReadOffset.from("0"), realtimeLane)
                        + readAndProcess(streamKey, ReadOffset.lastConsumed(), realtimeLane);
                if (handled > 0) {
                    idleTicks = 0;
                    continue;
                }
                if (handled < 0) {
                    sleepQuietly(Math.max(pollIntervalMs, 1_000));
                    continue;
                }
                logPendingDepthPeriodically(streamKey, ++idleTicks);
                sleepQuietly(pollIntervalMs);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return;
            } catch (Exception e) {
                // A deleted group/stream (NOGROUP) must not wedge the lane:
                // forget the cached "group ready" flag so it is recreated.
                if (String.valueOf(e.getMessage()).contains("NOGROUP")) {
                    groupsReady.remove(streamKey);
                }
                log.warn("Alarm stream lane '{}' poll failed, will retry: {}", streamKey, e.getMessage());
                sleepQuietly(Math.max(pollIntervalMs, 1_000));
            }
        }
    }

    private int readAndProcess(String streamKey, ReadOffset offset, boolean realtimeLane) throws Exception {
        ensureGroup(streamKey);
        StreamReadOptions options = StreamReadOptions.empty().count(batchSize);
        List<MapRecord<String, Object, Object>> records = redisTemplate.opsForStream()
                .read(Consumer.from(group, consumerName), options,
                        StreamOffset.create(streamKey, offset));
        if (records == null || records.isEmpty()) {
            return 0;
        }
        return realtimeLane ? handleRealtime(streamKey, records) : handlePersist(streamKey, records);
    }

    /**
     * Realtime lane: write each lifecycle record, ack on success; stop at the
     * first failed write so ordering is preserved and the failed record plus
     * everything behind it stay pending for the next tick.
     *
     * @return acked record count, or -1 when a write failed (lane backs off)
     */
    int handleRealtime(String streamKey, List<MapRecord<String, Object, Object>> records) {
        int handled = 0;
        for (MapRecord<String, Object, Object> record : records) {
            PointAlarmState state = parseOrSkip(streamKey, record);
            if (state == null) {
                ack(streamKey, record);
                handled++;
                continue;
            }
            if (tdengineSyncService.writeRealtimeAlarm(state)) {
                ack(streamKey, record);
                handled++;
            } else {
                log.warn("Realtime stream '{}': TDengine write failed for {} (id={}), "
                                + "record and followers stay pending for retry",
                        streamKey, state.getMqttPath(), record.getId());
                return -1;
            }
        }
        return handled;
    }

    /**
     * Persist lane: parse the whole batch, bulk-write once, ack the batch only
     * after the write reports every row stored. A failed (or partially failed)
     * batch is retried whole - TDengine's (subtable, ts) upsert makes the
     * rewrite of already-stored rows idempotent.
     *
     * @return handled record count, or -1 when the write failed (lane backs off)
     */
    int handlePersist(String streamKey, List<MapRecord<String, Object, Object>> records) {
        List<MapRecord<String, Object, Object>> writable = new ArrayList<>(records.size());
        List<PointAlarmState> batch = new ArrayList<>(records.size());
        for (MapRecord<String, Object, Object> record : records) {
            PointAlarmState state = parseOrSkip(streamKey, record);
            if (state == null) {
                ack(streamKey, record);
                continue;
            }
            writable.add(record);
            batch.add(state);
        }
        if (batch.isEmpty()) {
            return records.size();
        }
        if (tdengineSyncService.writeEventFrames(batch)) {
            for (MapRecord<String, Object, Object> record : writable) {
                ack(streamKey, record);
            }
            return records.size();
        }
        log.warn("Persist stream '{}': batch of {} frame(s) not fully written, "
                        + "stays pending and will be retried (at-least-once)",
                streamKey, batch.size());
        return -1;
    }

    /**
     * Parse a stream entry's {@code data} field. Unparseable payloads are
     * logged with their full content (the only remaining audit trail for them)
     * and reported as null so the caller acknowledges and skips them - one
     * malformed record must not block the lane forever.
     */
    private PointAlarmState parseOrSkip(String streamKey, MapRecord<String, Object, Object> record) {
        Object data = record.getValue() == null ? null : record.getValue().get("data");
        if (data == null) {
            log.error("Stream '{}' record {} has no data field: {}", streamKey, record.getId(),
                    record.getValue());
            return null;
        }
        try {
            PointAlarmState state = objectMapper.readValue(data.toString(), PointAlarmState.class);
            if (state.getMqttPath() == null) {
                log.error("Stream '{}' record {} has null path, skipping: {}",
                        streamKey, record.getId(), data);
                return null;
            }
            return state;
        } catch (Exception e) {
            log.error("Stream '{}' record {} is unparseable, skipping (payload: {})",
                    streamKey, record.getId(), data, e);
            return null;
        }
    }

    private void ack(String streamKey, MapRecord<String, Object, Object> record) {
        redisTemplate.opsForStream().acknowledge(streamKey, group, record.getId().getValue());
    }

    /**
     * Create the consumer group at stream start (MKSTREAM, reading from the
     * beginning). BUSYGROUP means it already exists - the normal case after
     * the first deploy. The group reads from id=0 so even entries appended
     * before the group existed are consumed (matters when the group was
     * deleted or the stream pre-existed the upgrade).
     */
    private void ensureGroup(String streamKey) throws Exception {
        if (groupsReady.contains(streamKey)) {
            return;
        }
        try {
            redisTemplate.opsForStream().createGroup(streamKey, ReadOffset.from("0"), group);
            log.info("Created consumer group '{}' on stream '{}' (reading from 0)", group, streamKey);
        } catch (Exception e) {
            if (!String.valueOf(e.getMessage()).contains("BUSYGROUP")) {
                throw e;
            }
        }
        groupsReady.add(streamKey);
    }

    /**
     * Every ~30s of idle time, log the group's pending depth so a growing
     * backlog (TDengine outage, retry loop) is visible in the logs.
     */
    private void logPendingDepthPeriodically(String streamKey, long idleTicks) {
        if (idleTicks * pollIntervalMs < 30_000 || idleTicks * pollIntervalMs % 30_000 >= pollIntervalMs) {
            return;
        }
        try {
            var info = redisTemplate.opsForStream().pending(streamKey, group);
            long total = info == null ? 0 : info.getTotalPendingMessages();
            if (total > 0) {
                log.warn("Stream '{}' has {} pending entr(ies) in group '{}' - "
                        + "a write batch is being retried or records await processing",
                        streamKey, total, group);
            }
        } catch (Exception e) {
            log.debug("Pending check on '{}' failed: {}", streamKey, e.getMessage());
        }
    }

    private void sleepQuietly(long ms) {
        try {
            Thread.sleep(ms);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
