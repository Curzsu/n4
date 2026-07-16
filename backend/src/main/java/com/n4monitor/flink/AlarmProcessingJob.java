package com.n4monitor.flink;

import com.n4monitor.alarm.model.AlarmRule;
import com.n4monitor.alarm.model.PointAlarmState;
import com.n4monitor.alarm.model.PointData;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.flink.api.common.eventtime.WatermarkStrategy;
import org.apache.flink.api.common.functions.RichMapFunction;
import org.apache.flink.configuration.Configuration;
import org.apache.flink.streaming.api.datastream.DataStream;
import org.apache.flink.streaming.api.environment.StreamExecutionEnvironment;
import org.apache.flink.connector.kafka.source.KafkaSource;
import org.apache.flink.connector.kafka.source.enumerator.initializer.OffsetsInitializer;
import org.apache.flink.api.common.serialization.SimpleStringSchema;
import redis.clients.jedis.Jedis;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.*;

/**
 * Flink alarm processing job.
 * <p>
 * Consumes device point data from Kafka, loads alarm rules from Redis,
 * evaluates each message in real-time, and writes latest state to Redis.
 * <p>
 * Alarm evaluation uses simple threshold comparison:
 * - Boolean rules: value equals configured alarm value
 * - Numeric rules: value >= threshold for High/High-High, value <= threshold for Low/Low-Low
 * <p>
 * When an alarm is triggered, the state is published to notification and realtime channels.
 * When no alarm is triggered, only the latest state is written to Redis without publishing.
 * <p>
 * Redis keys and channel names are passed in via {@link #run} so they can be configured
 * externally (e.g. from application.yaml).
 * <p>
 * Can run standalone via main method, or auto-started by FlinkJobRunner with Spring Boot.
 */
public class AlarmProcessingJob {

    /** Default Redis key for alarm rules */
    private static final String DEFAULT_RULES_KEY = "alarm:rules";

    /** Default Redis key for latest point state */
    private static final String DEFAULT_LATEST_KEY = "alarm:latest";

    /** Default Redis key for active alarm records (with ack state) */
    private static final String DEFAULT_ACTIVE_KEY = "alarm:active";

    /** Default Redis channel for WebSocket alarm notifications */
    private static final String DEFAULT_NOTIFICATIONS_CHANNEL = "alarm:notifications";

    /** Default Redis channel for real-time alarm persistence */
    private static final String DEFAULT_REALTIME_CHANNEL = "alarm:realtime";

    /**
     * Standalone entry point (IDE or command line).
     */
    public static void main(String[] args) throws Exception {
        run("47.121.30.136:9092", "obix-mqtt-points",
            "n4-flink-alarm-processor", "localhost", 6379,
            DEFAULT_RULES_KEY, DEFAULT_LATEST_KEY, DEFAULT_ACTIVE_KEY,
            DEFAULT_NOTIFICATIONS_CHANNEL, DEFAULT_REALTIME_CHANNEL);
    }

    /**
     * Start Flink job (blocking method, should be called in a separate thread).
     *
     * @param kafkaBroker          Kafka address
     * @param kafkaTopic           Kafka topic
     * @param groupId              Consumer group ID
     * @param redisHost            Redis address
     * @param redisPort            Redis port
     * @param rulesRedisKey        Redis hash key for alarm rules
     * @param latestRedisKey       Redis hash key for latest point state (real-time values)
     * @param activeRedisKey       Redis hash key for active alarm records (with ack state)
     * @param notificationChannel  Redis pub/sub channel for WebSocket notifications
     * @param realtimeChannel      Redis pub/sub channel for realtime TDengine persistence
     */
    public static void run(String kafkaBroker, String kafkaTopic,
                           String groupId, String redisHost, int redisPort,
                           String rulesRedisKey, String latestRedisKey, String activeRedisKey,
                           String notificationChannel, String realtimeChannel) throws Exception {

        StreamExecutionEnvironment env = StreamExecutionEnvironment.getExecutionEnvironment();
        env.setParallelism(1);

        KafkaSource<String> kafkaSource = KafkaSource.<String>builder()
                .setBootstrapServers(kafkaBroker)
                .setGroupId(groupId)
                .setTopics(kafkaTopic)
                .setValueOnlyDeserializer(new SimpleStringSchema())
                .setStartingOffsets(OffsetsInitializer.latest())
                .build();

        DataStream<String> kafkaStream = env.fromSource(
                kafkaSource,
                WatermarkStrategy.forBoundedOutOfOrderness(Duration.ofSeconds(3)),
                "kafka-alarm-source"
        );

        DataStream<String> alarmStream = kafkaStream.map(
                new AlarmProcessFunction(redisHost, redisPort, rulesRedisKey,
                        latestRedisKey, activeRedisKey, notificationChannel, realtimeChannel));

        alarmStream.print();

        env.execute("N4-Monitor Alarm Processing Job");
    }

    /**
     * Flink MapFunction: load rules from Redis, evaluate each Kafka message,
     * output to console and write latest state to Redis.
     */
    public static class AlarmProcessFunction extends RichMapFunction<String, String> {

        private final String redisHost;
        private final int redisPort;
        private final String rulesRedisKey;
        private final String latestRedisKey;
        private final String activeRedisKey;
        private final String notificationChannel;
        private final String realtimeChannel;

        private transient Jedis jedis;
        private transient Map<String, List<AlarmRule>> rulesCache;
        private transient ObjectMapper objectMapper;

        private static final DateTimeFormatter TS_FORMAT =
                DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss.SSS");

        private static final List<String> NUMERIC_PRIORITY_ORDER =
                List.of("High-High", "High", "Low-Low", "Low");

        public AlarmProcessFunction(String redisHost, int redisPort,
                                    String rulesRedisKey, String latestRedisKey, String activeRedisKey,
                                    String notificationChannel, String realtimeChannel) {
            this.redisHost = redisHost;
            this.redisPort = redisPort;
            this.rulesRedisKey = rulesRedisKey;
            this.latestRedisKey = latestRedisKey;
            this.activeRedisKey = activeRedisKey;
            this.notificationChannel = notificationChannel;
            this.realtimeChannel = realtimeChannel;
        }

        @Override
        public void open(Configuration parameters) {
            jedis = new Jedis(redisHost, redisPort);
            objectMapper = new ObjectMapper();
            rulesCache = new HashMap<>();

            // Load alarm rules from Redis
            Map<String, String> raw = jedis.hgetAll(rulesRedisKey);
            for (Map.Entry<String, String> entry : raw.entrySet()) {
                try {
                    List<AlarmRule> rules = objectMapper.readValue(
                            entry.getValue(),
                            objectMapper.getTypeFactory().constructCollectionType(List.class, AlarmRule.class)
                    );
                    rulesCache.put(entry.getKey(), rules);
                } catch (Exception e) {
                    System.err.println("[FLINK] Failed to deserialize rules: " + entry.getKey());
                }
            }

            System.out.println("[FLINK] Alarm processor started, loaded " + rulesCache.size() + " point rules");
            System.out.println("[FLINK] Rules Redis key: " + rulesRedisKey);
            System.out.println("[FLINK] Latest state Redis key: " + latestRedisKey);
            System.out.println("[FLINK] Active alarm Redis key: " + activeRedisKey);
        }

        @Override
        public String map(String message) throws Exception {
            if (rulesCache == null || rulesCache.isEmpty()) return "[SKIP] Rules not loaded";

            // 1. Parse Kafka message
            PointData point;
            try {
                point = objectMapper.readValue(message, PointData.class);
            } catch (Exception e) {
                return "[SKIP] JSON parse failed";
            }

            String path = point.getPath();
            if (path == null) return "[SKIP] path is null";

            // 2. Find rules and evaluate
            List<AlarmRule> pointRules = rulesCache.get(path);
            if (pointRules == null || pointRules.isEmpty()) return "[SKIP] Rules not found: " + path;

            AlarmRule triggered = evaluate(point.getValue(), pointRules);
            boolean isAlarm = triggered != null;
            String ts = formatTimestamp(point.getTimestamp());

            // 3. Build current state and resolve stage / duration against active history
            //    alarm:latest  -> real-time point value (always overwritten, for TDengine sync)
            //    alarm:active  -> alarm record + ack state (only changes on alarm/recovery/ack)
            PointAlarmState current = buildState(point, triggered);
            PointAlarmState history = readActive(path);   // alarm record (may be null/normal/frozen)
            long intervalSeconds = computeIntervalSeconds(history, current);

            // 4. Always write the real-time value to alarm:latest (preserves original semantics)
            writeLatestState(current);

            // 5. Resolve alarm lifecycle and update alarm:active accordingly
            if (history == null || isBlank(history.getLevel())) {
                // No active alarm record (or last record was non-alarm)
                if (isAlarm) {
                    // Fresh alarm start
                    current.setStage("start");
                    current.setDuration("0");
                    current.setAckBy("-");
                    current.setStatus("Unack");
                    writeActiveState(current);
                    publishAlarm(current);
                }
                // else: still normal, nothing to do in alarm:active
            } else if ("end".equals(history.getStage())) {
                // History is a frozen (recovered) alarm record
                if (isAlarm) {
                    // Re-alarmed after recovery: new start, drop the frozen record
                    current.setStage("start");
                    current.setDuration("0");
                    current.setAckBy("-");
                    current.setStatus("Unack");
                    writeActiveState(current);
                    publishAlarm(current);
                }
                // else: still normal after recovery -> idempotent skip (keep frozen record as-is)
            } else {
                // History is an ongoing alarm (stage start/continue)
                if (isAlarm) {
                    if (history.getLevel().equals(current.getLevel())) {
                        // Same level continues: preserve ack state so it survives across ticks
                        current.setStage("continue");
                        current.setDuration(String.valueOf(intervalSeconds));
                        current.setStatus(history.getStatus());
                        current.setAckBy(history.getAckBy());
                    } else {
                        // Level escalated (e.g. High -> High-High): close old phase, start new
                        current.setStage("start");
                        current.setDuration("0");
                        current.setAckBy("-");
                        current.setStatus("Unack");
                        history.setStage("end");
                        history.setDuration(String.valueOf(intervalSeconds));
                        publishRealTimeChannel(history);
                    }
                    writeActiveState(current);
                    publishAlarm(current);
                } else {
                    // Alarm recovered
                    if ("Ack".equals(history.getStatus())) {
                        // Already acknowledged + recovered -> remove from active, signal frontend.
                        // Persist the end record to TDengine first (publishRealTimeChannel sets stage/duration
                        // and serializes), then broadcast a cleared signal to the frontend.
                        removeActive(path);
                        history.setStage("end");
                        history.setDuration(String.valueOf(intervalSeconds));
                        publishRealTimeChannel(history);
                        publishNotificationChannel(history);
                    } else {
                        // Not yet acknowledged -> freeze the alarm record with final duration
                        history.setStage("end");
                        history.setDuration(String.valueOf(intervalSeconds));
                        writeActiveState(history);
                        publishAlarm(history);   // broadcast frozen record (frontend freezes duration)
                    }
                }
            }

            // 6. Console output
            if (isAlarm) {
                String color = switch (triggered.getSeverity()) {
                    case "Critical" -> "\033[91m";
                    case "Major"    -> "\033[93m";
                    case "Minor"    -> "\033[33m";
                    case "Warning"  -> "\033[94m";
                    default         -> "";
                };
                return String.format("%s[%s] [%8s] [%-8s] %-40s | value=%s | level=%s | %s\033[0m",
                        color, ts, triggered.getSeverity(), current.getStage(), point.getName(),
                        point.getValue(), triggered.getLevel(), triggered.getMessage());
            } else {
                return String.format("\033[92m[%s] [%8s] %-40s | value=%s | normal\033[0m",
                        ts, "NORMAL", point.getName(), point.getValue());
            }
        }

        @Override
        public void close() {
            if (jedis != null) jedis.close();
        }

        // ── State construction ──────────────────────────────────────────────────────

        private PointAlarmState buildState(PointData point, AlarmRule triggered) {
            PointAlarmState state = new PointAlarmState();
            state.setName(point.getName());
            state.setDisplayName(point.getDisplayName());
            state.setPath(point.getPath());
            state.setValue(point.getValue());
            state.setType(point.getType());
            state.setUnit(point.getUnit());
            state.setMax(point.getMax());
            state.setMin(point.getMin());
            state.setIp(point.getIp());
            state.setPort(point.getPort());
            state.setOther(point.getOther());
            state.setTimestamp(point.getTimestamp());

            if (triggered != null) {
                state.setLevel(triggered.getLevel());
                state.setSeverity(triggered.getSeverity());
                state.setMessage(triggered.getMessage());
                state.setHysteresis(triggered.getHysteresis());
            }
            // If not triggered, level/severity/message/hysteresis remain null

            // status/ackBy are set per-case in map() (preserved on continue, reset on start)
            state.setSource(extractSource(point.getPath()));
            state.setLocation(extractLocation(point.getPath()));
            state.setDuration("0");
            state.setStage("");

            return state;
        }

        /**
         * Extract location from path: remove leading slash, split by '/',
         * and return the third segment (index 2).
         */
        private String extractLocation(String path) {
            if (path == null || path.isEmpty()) {
                return "";
            }
            String normalized = path.startsWith("/") ? path.substring(1) : path;
            String[] parts = normalized.split("/");
            return parts.length >= 3 ? parts[2] : "";
        }

        /**
         * Extract source from path: remove leading slash, split by '/',
         * and join the last two segments with '_'.
         */
        private String extractSource(String path) {
            if (path == null || path.isEmpty()) {
                return "";
            }
            String normalized = path.startsWith("/") ? path.substring(1) : path;
            String[] parts = normalized.split("/");
            if (parts.length >= 2) {
                return parts[parts.length - 2] + "/" + parts[parts.length - 1];
            }
            return parts.length == 1 ? parts[0] : "";
        }

        // ── Redis writers ───────────────────────────────────────────────────────────

        private void writeLatestState(PointAlarmState state) {
            try {
                String json = objectMapper.writeValueAsString(state);
                jedis.hset(latestRedisKey, state.getPath(), json);
            } catch (Exception e) {
                System.err.println("[FLINK] Failed to write latest state to Redis: " + state.getPath() + " - " + e.getMessage());
            }
        }

        private void publishAlarm(PointAlarmState state) {
            try {
                String json = objectMapper.writeValueAsString(state);
                jedis.publish(notificationChannel, json);
                jedis.publish(realtimeChannel, json);
            } catch (Exception e) {
                System.err.println("[FLINK] Failed to publish alarm: " + state.getPath() + " - " + e.getMessage());
            }
        }

        // ── Active alarm record helpers (alarm:active) ──────────────────────────────

        private PointAlarmState readActive(String path) {
            try {
                String json = jedis.hget(activeRedisKey, path);
                if (json == null || json.isBlank()) {
                    return null;
                }
                return objectMapper.readValue(json, PointAlarmState.class);
            } catch (Exception e) {
                System.err.println("[FLINK] Failed to read active alarm for path: " + path + " - " + e.getMessage());
                return null;
            }
        }

        private void writeActiveState(PointAlarmState state) {
            try {
                String json = objectMapper.writeValueAsString(state);
                jedis.hset(activeRedisKey, state.getPath(), json);
            } catch (Exception e) {
                System.err.println("[FLINK] Failed to write active alarm to Redis: " + state.getPath() + " - " + e.getMessage());
            }
        }

        private void removeActive(String path) {
            try {
                jedis.hdel(activeRedisKey, path);
            } catch (Exception e) {
                System.err.println("[FLINK] Failed to remove active alarm for path: " + path + " - " + e.getMessage());
            }
        }

        /**
         * Broadcast a "cleared" signal for an acknowledged alarm that has recovered.
         * The record carries stage=end + status=Ack + blank level so the frontend
         * can remove it from the alarm list. Only published to the notification
         * channel (no TDengine realtime write needed — the end record was already
         * persisted via publishHistoryEnd when recovery was detected).
         */
        private void publishNotificationChannel(PointAlarmState history) {
            try {
                String json = objectMapper.writeValueAsString(history);
                jedis.publish(notificationChannel, json);
            } catch (Exception e) {
                System.err.println("[FLINK] Failed to publish cleared signal: " + history.getPath() + " - " + e.getMessage());
            }
        }

        private long computeIntervalSeconds(PointAlarmState history, PointAlarmState current) {
            if (history == null || history.getTimestamp() == null || current.getTimestamp() == null) {
                return 0;
            }
            long diff = current.getTimestamp() - history.getTimestamp();
            long baseSeconds = Math.max(0, diff / 1000);

            long historyDuration = 0;
            try {
                String d = history.getDuration();
                if (d != null && !d.isBlank()) {
                    historyDuration = Long.parseLong(d);
                }
            } catch (NumberFormatException ignored) {
            }

            return baseSeconds + historyDuration;
        }

        private void publishRealTimeChannel(PointAlarmState history) {
            try {
                String json = objectMapper.writeValueAsString(history);
                jedis.publish(realtimeChannel, json);
            } catch (Exception e) {
                System.err.println("[FLINK] Failed to publish real-time signal: " + history.getPath() + " - " + e.getMessage());
            }
        }

        private boolean isBlank(String value) {
            return value == null || value.isBlank();
        }

        // ── Threshold evaluation ────────────────────────────────────────────────────

        private AlarmRule evaluate(String pointValue, List<AlarmRule> rules) {
            String normalizedValue = pointValue != null ? pointValue.trim() : null;

            // Boolean rules: trigger when the point value matches the configured alarm value
            for (AlarmRule rule : rules) {
                if ("Boolean".equals(rule.getLevel()) || "BOOL".equals(rule.getPointType())) {
                    if (rule.getValue() != null
                            && normalizedValue != null
                            && normalizedValue.equalsIgnoreCase(rule.getValue())) {
                        return rule;
                    }
                }
            }

            // Numeric rules: simple threshold comparison
            Double numVal = null;
            try {
                numVal = Double.parseDouble(normalizedValue);
            } catch (Exception ignored) {
            }

            if (numVal == null) {
                return null;
            }

            List<AlarmRule> numericRules = rules.stream()
                    .filter(r -> NUMERIC_PRIORITY_ORDER.contains(r.getLevel()))
                    .filter(r -> r.getValue() != null)
                    .sorted(Comparator.comparingInt(r -> NUMERIC_PRIORITY_ORDER.indexOf(r.getLevel())))
                    .toList();

            for (AlarmRule rule : numericRules) {
                try {
                    double threshold = Double.parseDouble(rule.getValue());

                    boolean triggered = switch (rule.getLevel()) {
                        case "High", "High-High" -> numVal >= threshold;
                        case "Low", "Low-Low" -> numVal <= threshold;
                        default -> false;
                    };

                    if (triggered) return rule;
                } catch (NumberFormatException ignored) {
                }
            }

            return null;
        }

        private String formatTimestamp(Long epochMs) {
            if (epochMs == null) return LocalDateTime.now().format(TS_FORMAT);
            return LocalDateTime.ofInstant(Instant.ofEpochMilli(epochMs), ZoneId.systemDefault())
                    .format(TS_FORMAT);
        }
    }
}
