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
            DEFAULT_RULES_KEY, DEFAULT_LATEST_KEY,
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
     * @param latestRedisKey       Redis hash key for latest point state
     * @param notificationChannel  Redis pub/sub channel for WebSocket notifications
     * @param realtimeChannel      Redis pub/sub channel for realtime TDengine persistence
     */
    public static void run(String kafkaBroker, String kafkaTopic,
                           String groupId, String redisHost, int redisPort,
                           String rulesRedisKey, String latestRedisKey,
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
                        latestRedisKey, notificationChannel, realtimeChannel));

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
                                    String rulesRedisKey, String latestRedisKey,
                                    String notificationChannel, String realtimeChannel) {
            this.redisHost = redisHost;
            this.redisPort = redisPort;
            this.rulesRedisKey = rulesRedisKey;
            this.latestRedisKey = latestRedisKey;
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
            String ts = formatTimestamp(point.getTimestamp());

            // 3. Build latest state and write to Redis
            PointAlarmState state = buildState(point, triggered);
            writeLatestState(state);

            // 4. If alarm triggered, publish to notification and realtime channels
            if (triggered != null) {
                publishAlarm(state);

                String color = switch (triggered.getSeverity()) {
                    case "Critical" -> "\033[91m";
                    case "Major"    -> "\033[93m";
                    case "Minor"    -> "\033[33m";
                    case "Warning"  -> "\033[94m";
                    default         -> "";
                };
                return String.format("%s[%s] [%8s] %-40s | value=%s | level=%s | %s\033[0m",
                        color, ts, triggered.getSeverity(), point.getName(),
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

            state.setAckBy("-");
            state.setStatus("Unack");
            state.setSource(extractSource(point.getPath()));
            state.setLocation(extractLocation(point.getPath()));
            state.setDuration("");

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
                return parts[parts.length - 2] + "_" + parts[parts.length - 1];
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
