package com.n4monitor.flink;

import com.n4monitor.alarm.model.PointAlarmState;
import com.n4monitor.alarm.model.PointData;
import com.n4monitor.entity.AlarmRuleEnumEntity;
import com.n4monitor.entity.AlarmRuleNumericEntity;
import com.fasterxml.jackson.core.type.TypeReference;
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
 * Consumes device point data from Kafka, reads alarm rules from Redis (three
 * type-specific hashes keyed by alarm_name), evaluates each message in real-time,
 * and writes latest state to Redis.
 * <p>
 * Rule lookup: extracts pointName from the Kafka message path, constructs
 * alarm_name = "{pointName}_00" (TODO: support multi-rule selection), and
 * queries the Redis hash corresponding to the message's type field
 * (boolean/numeric/enum). String-type messages are skipped.
 */
public class AlarmProcessingJob {

    private static final String DEFAULT_RULES_NUMERIC_KEY = "alarm:rules:numeric";
    private static final String DEFAULT_RULES_ENUM_KEY = "alarm:rules:enum";
    private static final String DEFAULT_LATEST_KEY = "alarm:latest";
    private static final String DEFAULT_ACTIVE_KEY = "alarm:active";
    private static final String DEFAULT_NOTIFICATIONS_CHANNEL = "alarm:notifications";
    private static final String DEFAULT_REALTIME_CHANNEL = "alarm:realtime";

    public static void main(String[] args) throws Exception {
        run("47.121.30.136:9092", "obix-mqtt-points",
            "n4-flink-alarm-processor", "localhost", 6379, "",
            DEFAULT_RULES_NUMERIC_KEY, DEFAULT_RULES_ENUM_KEY,
            DEFAULT_LATEST_KEY, DEFAULT_ACTIVE_KEY,
            DEFAULT_NOTIFICATIONS_CHANNEL, DEFAULT_REALTIME_CHANNEL);
    }

    /**
     * Start Flink job (blocking method, should be called in a separate thread).
     */
    public static void run(String kafkaBroker, String kafkaTopic,
                           String groupId, String redisHost, int redisPort, String redisPassword,
                           String rulesNumericKey, String rulesEnumKey,
                           String latestRedisKey, String activeRedisKey,
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
                new AlarmProcessFunction(redisHost, redisPort,
                redisPassword,
                        rulesNumericKey, rulesEnumKey,
                        latestRedisKey, activeRedisKey, notificationChannel, realtimeChannel));

        alarmStream.print();

        env.execute("N4-Monitor Alarm Processing Job");
    }

    /**
     * Flink MapFunction: reads rules from Redis per-message (so dynamic rule
     * updates are picked up without restart), evaluates, and manages alarm lifecycle.
     */
    public static class AlarmProcessFunction extends RichMapFunction<String, String> {

        private final String redisHost;
        private final int redisPort;
        private final String redisPassword;
        private final String rulesNumericKey;
        private final String rulesEnumKey;
        private final String latestRedisKey;
        private final String activeRedisKey;
        private final String notificationChannel;
        private final String realtimeChannel;

        private transient Jedis jedis;
        private transient ObjectMapper objectMapper;

        private static final DateTimeFormatter TS_FORMAT =
                DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss.SSS");

        public AlarmProcessFunction(String redisHost, int redisPort, String redisPassword,
                                    String rulesNumericKey, String rulesEnumKey,
                                    String latestRedisKey, String activeRedisKey,
                                    String notificationChannel, String realtimeChannel) {
            this.redisHost = redisHost;
            this.redisPort = redisPort;
            this.redisPassword = redisPassword;
            this.rulesNumericKey = rulesNumericKey;
            this.rulesEnumKey = rulesEnumKey;
            this.latestRedisKey = latestRedisKey;
            this.activeRedisKey = activeRedisKey;
            this.notificationChannel = notificationChannel;
            this.realtimeChannel = realtimeChannel;
        }

        @Override
        public void open(Configuration parameters) {
            jedis = new Jedis(redisHost, redisPort);
            if (redisPassword != null && !redisPassword.isBlank()) {
                jedis.auth(redisPassword);
            }
            objectMapper = new ObjectMapper().findAndRegisterModules();

            System.out.println("[FLINK] Alarm processor started");
            System.out.println("[FLINK] Rules numeric key: " + rulesNumericKey);
            System.out.println("[FLINK] Rules enum key: " + rulesEnumKey);
            System.out.println("[FLINK] Latest state Redis key: " + latestRedisKey);
            System.out.println("[FLINK] Active alarm Redis key: " + activeRedisKey);
        }

        @Override
        public String map(String message) throws Exception {
            // 1. Parse Kafka message
            PointData point;
            try {
                point = objectMapper.readValue(message, PointData.class);
            } catch (Exception e) {
                return "[SKIP] JSON parse failed";
            }

            String path = point.getPath();
            if (path == null) return "[SKIP] path is null";

            String type = point.getType();
            String pointName = extractPointName(path);
            String ts = formatTimestamp(point.getTimestamp());

            // 2. Filter string type - no alarm rules
            if ("string".equalsIgnoreCase(type)) {
                // Still write latest state for TDengine sync
                PointAlarmState normalState = buildState(point, null, null, null, null);
                writeLatestState(normalState);
                return String.format("\033[90m[%s] [%8s] %-40s | value=%s | string type, skipped\033[0m",
                        ts, "STRING", point.getName(), point.getValue());
            }

            // 3. Construct alarm_name (TODO: currently always uses _00, optimize later for multi-rule selection)
            String alarmName = pointName + "_00";

            // 4. Look up rule from Redis based on type
            EvalResult triggered = null;
            String ruleType = type != null ? type.toLowerCase() : "";

            switch (ruleType) {
                case "boolean", "enum" -> triggered = evaluateEnum(alarmName, point.getValue());
                case "numeric" -> triggered = evaluateNumeric(alarmName, point.getValue());
                default -> {
                    // Unknown type or null type
                    PointAlarmState normalState = buildState(point, null, null, null, null);
                    writeLatestState(normalState);
                    return String.format("[%s] [%8s] %-40s | value=%s | unknown type: %s",
                            ts, "UNKNOWN", point.getName(), point.getValue(), type);
                }
            }

            if (triggered == null) {
                // No rule found for this point
                PointAlarmState normalState = buildState(point, null, null, null, null);
                writeLatestState(normalState);
                return String.format("[%s] [%8s] %-40s | value=%s | no rule found, point=%s, type=%s",
                        ts, "NORULE", point.getName(), point.getValue(), pointName, type);
            }

            boolean isAlarm = triggered.isAlarm();
            String level = triggered.level;
            String severity = triggered.severity;
            String alarmMessage = triggered.message;
            String hysteresis = triggered.hysteresis;

            // 5. Build current state and resolve stage / duration against active history
            PointAlarmState current = buildState(point, level, severity, alarmMessage, hysteresis);
            PointAlarmState history = readActive(path);
            long intervalSeconds = computeIntervalSeconds(history, current);

            // 6. Always write the real-time value to alarm:latest
            writeLatestState(current);

            // 7. Resolve alarm lifecycle and update alarm:active accordingly
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

            // 8. Console output
            if (isAlarm) {
                String color = switch (severity) {
                    case "Critical" -> "\033[91m";
                    case "Major"    -> "\033[93m";
                    case "Minor"    -> "\033[33m";
                    case "Warning"  -> "\033[94m";
                    default         -> "";
                };
                return String.format("%s[%s] [%8s] [%-8s] %-40s | value=%s | level=%s | %s\033[0m",
                        color, ts, severity, current.getStage(), point.getName(),
                        point.getValue(), level, alarmMessage);
            } else {
                return String.format("\033[92m[%s] [%8s] %-40s | value=%s | normal\033[0m",
                        ts, "NORMAL", point.getName(), point.getValue());
            }
        }

        @Override
        public void close() {
            if (jedis != null) jedis.close();
        }

        // ── Rule evaluation (type-specific, reads from Redis) ──────────────────

        /** Result of evaluating a rule against a point value. */
        record EvalResult(boolean isAlarm, String level, String severity,
                          String message, String hysteresis) {}

        private EvalResult evaluateNumeric(String alarmName, String pointValue) {
            String json = jedis.hget(rulesNumericKey, alarmName);
            if (json == null || json.isBlank()) return null;

            try {
                AlarmRuleNumericEntity rule = objectMapper.readValue(json, AlarmRuleNumericEntity.class);
                Double numVal = null;
                try {
                    numVal = Double.parseDouble(pointValue != null ? pointValue.trim() : "");
                } catch (Exception ignored) {}
                if (numVal == null) {
                    return new EvalResult(false, null, null, null, null);
                }

                // Priority order: High-High, High, Low-Low, Low
                // High-High
                if (rule.getHighHighValue() != null && !rule.getHighHighValue().isBlank()) {
                    try {
                        if (numVal >= Double.parseDouble(rule.getHighHighValue())) {
                            return new EvalResult(true, "High-High", rule.getHighHighSeverity(),
                                    rule.getHighHighMessage(), rule.getHighHighHysteresis());
                        }
                    } catch (NumberFormatException ignored) {}
                }
                // High
                if (rule.getHighValue() != null && !rule.getHighValue().isBlank()) {
                    try {
                        if (numVal >= Double.parseDouble(rule.getHighValue())) {
                            return new EvalResult(true, "High", rule.getHighSeverity(),
                                    rule.getHighMessage(), rule.getHighHysteresis());
                        }
                    } catch (NumberFormatException ignored) {}
                }
                // Low-Low
                if (rule.getLowLowValue() != null && !rule.getLowLowValue().isBlank()) {
                    try {
                        if (numVal <= Double.parseDouble(rule.getLowLowValue())) {
                            return new EvalResult(true, "Low-Low", rule.getLowLowSeverity(),
                                    rule.getLowLowMessage(), rule.getLowLowHysteresis());
                        }
                    } catch (NumberFormatException ignored) {}
                }
                // Low
                if (rule.getLowValue() != null && !rule.getLowValue().isBlank()) {
                    try {
                        if (numVal <= Double.parseDouble(rule.getLowValue())) {
                            return new EvalResult(true, "Low", rule.getLowSeverity(),
                                    rule.getLowMessage(), rule.getLowHysteresis());
                        }
                    } catch (NumberFormatException ignored) {}
                }

                return new EvalResult(false, null, null, null, null);
            } catch (Exception e) {
                System.err.println("[FLINK] Failed to deserialize numeric rule: " + alarmName + " - " + e.getMessage());
                return null;
            }
        }

        private EvalResult evaluateEnum(String alarmName, String pointValue) {
            String json = jedis.hget(rulesEnumKey, alarmName);
            if (json == null || json.isBlank()) return null;

            try {
                List<AlarmRuleEnumEntity> rules = objectMapper.readValue(json,
                        new TypeReference<List<AlarmRuleEnumEntity>>() {});
                String normalizedValue = pointValue != null ? pointValue.trim() : null;

                for (AlarmRuleEnumEntity rule : rules) {
                    if (rule.getEnumValue() != null && normalizedValue != null
                            && normalizedValue.equalsIgnoreCase(rule.getEnumValue())) {
                        // Match found - alarm only if severity is non-empty
                        if (rule.getSeverity() != null && !rule.getSeverity().isBlank()) {
                            return new EvalResult(true, "Enum", rule.getSeverity(),
                                    rule.getMessage(), null);
                        } else {
                            // Empty severity -> normal value, not an alarm
                            return new EvalResult(false, null, null, null, null);
                        }
                    }
                }
                // No matching enum value
                return new EvalResult(false, null, null, null, null);
            } catch (Exception e) {
                System.err.println("[FLINK] Failed to deserialize enum rules: " + alarmName + " - " + e.getMessage());
                return null;
            }
        }

        // ── State construction ──────────────────────────────────────────────────

        private PointAlarmState buildState(PointData point, String level, String severity,
                                           String message, String hysteresis) {
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

            if (level != null) {
                state.setLevel(level);
                state.setSeverity(severity);
                state.setMessage(message);
                state.setHysteresis(hysteresis);
            }

            state.setSource(extractSource(point.getPath()));
            state.setLocation(extractLocation(point.getPath()));
            state.setDuration("0");
            state.setStage("");
            return state;
        }

        private String extractPointName(String path) {
            if (path == null || path.isEmpty()) return "";
            String normalized = path.startsWith("/") ? path.substring(1) : path;
            String[] parts = normalized.split("/");
            return parts.length > 0 ? parts[parts.length - 1] : "";
        }

        private String extractLocation(String path) {
            if (path == null || path.isEmpty()) return "";
            String normalized = path.startsWith("/") ? path.substring(1) : path;
            String[] parts = normalized.split("/");
            return parts.length >= 3 ? parts[2] : "";
        }

        private String extractSource(String path) {
            if (path == null || path.isEmpty()) return "";
            String normalized = path.startsWith("/") ? path.substring(1) : path;
            String[] parts = normalized.split("/");
            if (parts.length >= 2) {
                return parts[parts.length - 2] + "/" + parts[parts.length - 1];
            }
            return parts.length == 1 ? parts[0] : "";
        }

        // ── Redis writers ────────────────────────────────────────────────────────

        private void writeLatestState(PointAlarmState state) {
            try {
                String json = objectMapper.writeValueAsString(state);
                jedis.hset(latestRedisKey, state.getPath(), json);
            } catch (Exception e) {
                System.err.println("[FLINK] Failed to write latest state: " + state.getPath() + " - " + e.getMessage());
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

        private PointAlarmState readActive(String path) {
            try {
                String json = jedis.hget(activeRedisKey, path);
                if (json == null || json.isBlank()) return null;
                return objectMapper.readValue(json, PointAlarmState.class);
            } catch (Exception e) {
                System.err.println("[FLINK] Failed to read active alarm: " + path + " - " + e.getMessage());
                return null;
            }
        }

        private void writeActiveState(PointAlarmState state) {
            try {
                String json = objectMapper.writeValueAsString(state);
                jedis.hset(activeRedisKey, state.getPath(), json);
            } catch (Exception e) {
                System.err.println("[FLINK] Failed to write active alarm: " + state.getPath() + " - " + e.getMessage());
            }
        }

        private void removeActive(String path) {
            try {
                jedis.hdel(activeRedisKey, path);
            } catch (Exception e) {
                System.err.println("[FLINK] Failed to remove active alarm: " + path + " - " + e.getMessage());
            }
        }

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
            } catch (NumberFormatException ignored) {}
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

        private String formatTimestamp(Long epochMs) {
            if (epochMs == null) return LocalDateTime.now().format(TS_FORMAT);
            return LocalDateTime.ofInstant(Instant.ofEpochMilli(epochMs), ZoneId.systemDefault())
                    .format(TS_FORMAT);
        }
    }
}
