package com.n4monitor.flink;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.n4monitor.alarm.model.PointAlarmState;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import redis.clients.jedis.Jedis;
import redis.clients.jedis.params.XAddParams;

import java.util.Map;

/**
 * All Redis reads/writes of the alarm pipeline: alarm:latest, alarm:active,
 * the notification Pub/Sub channel and the two persistence Streams
 * (realtime, full-detail). Extracted verbatim from AlarmProcessFunction;
 * key names, JSON shapes (including timestamp overrides) and log messages
 * are unchanged.
 * <p>
 * The two persistence paths use Redis Streams instead of Pub/Sub: every
 * entry is stored until consumed and acknowledged, so a backend outage or
 * restart can no longer drop alarm records that must reach TDengine for
 * auditing. Entries are trimmed with approximate MAXLEN so a long backend
 * outage cannot grow the stream without bound - the cap trades the oldest
 * entries for bounded Redis memory and is configurable per stream.
 */
final class RedisStateStore {

    private static final Logger log = LoggerFactory.getLogger(RedisStateStore.class);

    private final ObjectMapper objectMapper;
    private final String latestRedisKey;
    private final String activeRedisKey;
    private final String notificationChannel;
    private final String realtimeStream;
    private final String persistenceStream;
    private final long realtimeStreamMaxlen;
    private final long persistenceStreamMaxlen;

    RedisStateStore(ObjectMapper objectMapper, String latestRedisKey, String activeRedisKey,
                    String notificationChannel, String realtimeStream, String persistenceStream,
                    long realtimeStreamMaxlen, long persistenceStreamMaxlen) {
        this.objectMapper = objectMapper;
        this.latestRedisKey = latestRedisKey;
        this.activeRedisKey = activeRedisKey;
        this.notificationChannel = notificationChannel;
        this.realtimeStream = realtimeStream;
        this.persistenceStream = persistenceStream;
        this.realtimeStreamMaxlen = realtimeStreamMaxlen;
        this.persistenceStreamMaxlen = persistenceStreamMaxlen;
    }

    void writeLatestState(Jedis jedis, PointAlarmState state) {
        try {
            String json = objectMapper.writeValueAsString(state);
            jedis.hset(latestRedisKey, state.getMqttPath(), json);
            log.debug("LATEST {} value={}", state.getMqttPath(), state.getValue());
        } catch (Exception e) {
            log.warn("Failed to write latest state for {}: {}", state.getMqttPath(), e.getMessage());
        }
    }

    /**
     * Publish to the notification channel (frontend WebSocket). The timestamp
     * is forced to the alarm's historical start time so the frontend always
     * shows when the alarm began (start/continue/end alike). Deliberately kept
     * on Pub/Sub: this is an ephemeral UI push, not audit data - a missed push
     * during a backend restart is healed by the next event, while queueing
     * stale notifications for later replay would only confuse the UI.
     */
    void publishNotificationChannel(Jedis jedis, PointAlarmState state) {
        try {
            String json = serializeWithTimestamp(state, state.getAlarmStartTime());
            jedis.publish(notificationChannel, json);
            log.debug("PUB-NOTIFY {} stage={} -> {}", state.getMqttPath(), state.getStage(), notificationChannel);
        } catch (Exception e) {
            log.warn("Failed to publish notification for {}: {}", state.getMqttPath(), e.getMessage());
        }
    }

    /**
     * Append to the realtime stream (TDengine alarm lifecycle persistence).
     * The state's own timestamp is used as the observation time. Callers that
     * publish a history record (e.g. a recovered alarm's end record) must set
     * the desired timestamp on the state before publishing, so the event is
     * recorded at the moment it was observed.
     */
    void publishRealTimeStream(Jedis jedis, PointAlarmState state) {
        try {
            String json = objectMapper.writeValueAsString(state);
            jedis.xadd(realtimeStream, xaddParams(realtimeStreamMaxlen), Map.of("data", json));
            log.debug("XADD-REALTIME {} stage={} -> {}", state.getMqttPath(), state.getStage(), realtimeStream);
        } catch (Exception e) {
            log.warn("Failed to publish realtime for {}: {}", state.getMqttPath(), e.getMessage());
        }
    }

    /**
     * Append a processed data frame to the persistence stream (TDengine
     * full-detail persistence). The frame's own timestamp is kept. For history
     * records (e.g. a recovered alarm's end record) the caller must set the
     * desired observation timestamp on the state before publishing, so the
     * event lands in TDengine at the same moment as its realtime counterpart.
     */
    void publishPersistenceStream(Jedis jedis, PointAlarmState state) {
        try {
            String json = objectMapper.writeValueAsString(state);
            jedis.xadd(persistenceStream, xaddParams(persistenceStreamMaxlen), Map.of("data", json));
            log.debug("XADD-PERSIST {} stage={} -> {}", state.getMqttPath(), state.getStage(), persistenceStream);
        } catch (Exception e) {
            log.warn("Failed to publish persistence for {}: {}", state.getMqttPath(), e.getMessage());
        }
    }

    /**
     * XADD with approximate MAXLEN trimming: while the backend is down the
     * stream grows until the cap, then the oldest entries are evicted - bounded
     * memory at the price of losing the very oldest audit frames after a long
     * outage (vs. unbounded growth or losing everything, as Pub/Sub did).
     */
    private XAddParams xaddParams(long maxlen) {
        return XAddParams.xAddParams().maxLen(maxlen).approximateTrimming();
    }

    /**
     * Write a state to alarm:active with its timestamp forced to the alarm's
     * historical start time (so continue/end records keep the start timestamp).
     */
    void writeActiveState(Jedis jedis, PointAlarmState state) {
        try {
            String json = serializeWithTimestamp(state, state.getAlarmStartTime());
            jedis.hset(activeRedisKey, state.getMqttPath(), json);
            log.debug("ACTIVE {} stage={} level={}", state.getMqttPath(), state.getStage(), state.getLevel());
        } catch (Exception e) {
            log.warn("Failed to write active state for {}: {}", state.getMqttPath(), e.getMessage());
        }
    }

    void removeActive(Jedis jedis, String path) {
        try {
            jedis.hdel(activeRedisKey, path);
            log.debug("ACTIVE-REMOVE {}", path);
        } catch (Exception e) {
            log.warn("Failed to remove active alarm for {}: {}", path, e.getMessage());
        }
    }

    PointAlarmState readActive(Jedis jedis, String path) {
        try {
            String json = jedis.hget(activeRedisKey, path);
            if (json == null || json.isBlank()) return null;
            return objectMapper.readValue(json, PointAlarmState.class);
        } catch (Exception e) {
            log.warn("Failed to read active alarm for {}: {}", path, e.getMessage());
            return null;
        }
    }

    /**
     * Serialize a state with a specific timestamp, without mutating the original.
     * If overrideTs is null, the state's own timestamp is kept.
     */
    private String serializeWithTimestamp(PointAlarmState state, Long overrideTs) throws Exception {
        if (overrideTs == null) {
            return objectMapper.writeValueAsString(state);
        }
        // Clone via JSON round-trip to avoid mutating the shared state object.
        var node = (com.fasterxml.jackson.databind.node.ObjectNode)
                objectMapper.readTree(objectMapper.writeValueAsString(state));
        node.put("timestamp", overrideTs);
        return objectMapper.writeValueAsString(node);
    }
}
