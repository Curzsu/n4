package com.n4monitor.flink;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.n4monitor.alarm.model.PointAlarmState;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import redis.clients.jedis.Jedis;

/**
 * All Redis reads/writes of the alarm pipeline: alarm:latest, alarm:active and
 * the two pub/sub channels. Extracted verbatim from AlarmProcessFunction; key
 * names, JSON shapes (including timestamp overrides) and log messages are
 * unchanged.
 */
final class RedisStateStore {

    private static final Logger log = LoggerFactory.getLogger(RedisStateStore.class);

    private final ObjectMapper objectMapper;
    private final String latestRedisKey;
    private final String activeRedisKey;
    private final String notificationChannel;
    private final String realtimeChannel;

    RedisStateStore(ObjectMapper objectMapper, String latestRedisKey, String activeRedisKey,
                    String notificationChannel, String realtimeChannel) {
        this.objectMapper = objectMapper;
        this.latestRedisKey = latestRedisKey;
        this.activeRedisKey = activeRedisKey;
        this.notificationChannel = notificationChannel;
        this.realtimeChannel = realtimeChannel;
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
     * Publish to the notification channel (frontend WebSocket).
     * The timestamp is forced to the alarm's historical start time so the
     * frontend always shows when the alarm began (start/continue/end alike).
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
     * Publish to the realtime channel (TDengine persistence).
     * The timestamp is forced to the current data tick's timestamp
     * (point.getTimestamp()), so each lifecycle event (start/continue/end/escalation)
     * is recorded at the moment it was observed - regardless of whether the state
     * object is the fresh current tick or a history record read back from Redis.
     * Accepts a nullable Long to avoid NPE from auto-unboxing when a Kafka message
     * lacks a timestamp; null falls back to the state's own timestamp.
     */
    void publishRealTimeChannel(Jedis jedis, PointAlarmState state, Long dataTimestamp) {
        try {
            String json = serializeWithTimestamp(state, dataTimestamp);
            jedis.publish(realtimeChannel, json);
            log.debug("PUB-REALTIME {} stage={} -> {}", state.getMqttPath(), state.getStage(), realtimeChannel);
        } catch (Exception e) {
            log.warn("Failed to publish realtime for {}: {}", state.getMqttPath(), e.getMessage());
        }
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
