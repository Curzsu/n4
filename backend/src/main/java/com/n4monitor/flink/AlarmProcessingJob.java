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
 * Consumes device point data from Kafka (obix-mqtt-points),
 * loads alarm rules from Redis, evaluates each message in real-time,
 * outputs to console and writes latest state to Redis (alarm:latest).
 * <p>
 * Can run standalone via main method, or auto-started by FlinkJobRunner with Spring Boot.
 */
public class AlarmProcessingJob {

    /** Redis key for alarm rules */
    private static final String REDIS_RULES_KEY = "alarm:rules";

    /** Redis key for latest point state */
    private static final String REDIS_LATEST_KEY = "alarm:latest";

    /**
     * Standalone entry point (IDE or command line).
     */
    public static void main(String[] args) throws Exception {
        run("47.121.30.136:9092", "obix-mqtt-points",
            "n4-flink-alarm-processor", "localhost", 6379);
    }

    /**
     * Start Flink job (blocking method, should be called in a separate thread).
     *
     * @param kafkaBroker Kafka address
     * @param kafkaTopic  Kafka topic
     * @param groupId     Consumer group ID
     * @param redisHost   Redis address
     * @param redisPort   Redis port
     */
    public static void run(String kafkaBroker, String kafkaTopic,
                           String groupId, String redisHost,
                           int redisPort) throws Exception {

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
                new AlarmProcessFunction(redisHost, redisPort));

        alarmStream.print();

        env.execute("N4-Monitor Alarm Processing Job");
    }

    /**
     * Flink ProcessFunction: load rules from Redis, evaluate each Kafka message,
     * output to console and write latest state to Redis.
     */
    public static class AlarmProcessFunction extends RichMapFunction<String, String> {

        private final String redisHost;
        private final int redisPort;

        private transient Jedis jedis;
        private transient Map<String, List<AlarmRule>> rulesCache;
        private transient ObjectMapper objectMapper;

        private static final DateTimeFormatter TS_FORMAT =
                DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss.SSS");

        private static final List<String> PRIORITY_ORDER =
                List.of("High-High", "High", "Low-Low", "Low");

        public AlarmProcessFunction(String redisHost, int redisPort) {
            this.redisHost = redisHost;
            this.redisPort = redisPort;
        }

        @Override
        public void open(Configuration parameters) {
            jedis = new Jedis(redisHost, redisPort);
            objectMapper = new ObjectMapper();
            rulesCache = new HashMap<>();

            // Load alarm rules from Redis
            Map<String, String> raw = jedis.hgetAll(REDIS_RULES_KEY);
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
            System.out.println("[FLINK] Latest state will be written to Redis key: " + REDIS_LATEST_KEY);
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
            PointAlarmState state = new PointAlarmState();
            // Copy all PointData fields
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
            // Alarm evaluation result
            if (triggered != null) {
                state.setLevel(triggered.getLevel());
                state.setSeverity(triggered.getSeverity());
                state.setMessage(triggered.getMessage());
            } else {
                state.setLevel("Normal");
//                state.setSeverity("Normal");
//                state.setMessage("within normal range");
            }
            // Additional fields
            state.setAckBy("-");
            state.setStatus("Unack");
            state.setSource("");
            state.setLocation("");

            // Write to Redis hash: alarm:latest, field=path, value=JSON
            // Also publish to Redis channel to notify WebSocket push
            try {
                String json = objectMapper.writeValueAsString(state);
                jedis.hset(REDIS_LATEST_KEY, path, json);
                jedis.publish("alarm:notifications", json);
            } catch (Exception e) {
                System.err.println("[FLINK] Failed to write latest state to Redis: " + path + " - " + e.getMessage());
            }

            // 4. Console output
            if (triggered != null) {
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

        // ── Threshold evaluation ────────────────────────────────────────────────────

        private AlarmRule evaluate(String pointValue, List<AlarmRule> rules) {
            Double numVal = null;
            try { numVal = Double.parseDouble(pointValue); } catch (Exception ignored) {}

            List<AlarmRule> sorted = rules.stream()
                    .filter(r -> PRIORITY_ORDER.contains(r.getLevel()))
                    .filter(r -> r.getSeverity() != null && r.getValue() != null)
                    .sorted(Comparator.comparingInt(r -> PRIORITY_ORDER.indexOf(r.getLevel())))
                    .toList();

            for (AlarmRule rule : sorted) {
                if (numVal != null) {
                    try {
                        double threshold = Double.parseDouble(rule.getValue());
                        boolean triggered = switch (rule.getLevel()) {
                            case "High", "High-High" -> numVal >= threshold;
                            case "Low", "Low-Low" -> numVal <= threshold;
                            default -> false;
                        };
                        if (triggered) return rule;
                    } catch (NumberFormatException ignored) {}
                } else {
                    if (pointValue != null && pointValue.equals(rule.getValue())) return rule;
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
