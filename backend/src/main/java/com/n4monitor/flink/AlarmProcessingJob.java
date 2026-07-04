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
 * Flink 报警处理作业
 * <p>
 * 从 Kafka (obix-mqtt-points) 消费设备点位数据，
 * 从 Redis 加载报警规则，实时评估每条消息，
 * 输出到控制台并同时将最新状态写入 Redis (alarm:latest)。
 * <p>
 * 可通过 main 方法独立运行，也可由 FlinkJobRunner 随 Spring Boot 自动启动。
 */
public class AlarmProcessingJob {

    /** Redis 中报警规则的 key */
    private static final String REDIS_RULES_KEY = "alarm:rules";

    /** Redis 中最新点位状态的 key */
    private static final String REDIS_LATEST_KEY = "alarm:latest";

    /**
     * 独立运行入口 (IDE 或命令行)
     */
    public static void main(String[] args) throws Exception {
        run("47.121.30.136:9092", "obix-mqtt-points",
            "n4-flink-alarm-processor", "localhost", 6379);
    }

    /**
     * 启动 Flink 作业 (阻塞方法, 应在独立线程中调用)
     *
     * @param kafkaBroker  Kafka 地址
     * @param kafkaTopic   Kafka topic
     * @param groupId      消费者组 ID
     * @param redisHost    Redis 地址
     * @param redisPort    Redis 端口
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
                .setStartingOffsets(OffsetsInitializer.earliest())
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
     * Flink ProcessFunction: 从 Redis 加载规则, 评估每条 Kafka 消息,
     * 输出到控制台并将最新状态写入 Redis
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

            // 从 Redis 加载报警规则
            Map<String, String> raw = jedis.hgetAll(REDIS_RULES_KEY);
            for (Map.Entry<String, String> entry : raw.entrySet()) {
                try {
                    List<AlarmRule> rules = objectMapper.readValue(
                            entry.getValue(),
                            objectMapper.getTypeFactory().constructCollectionType(List.class, AlarmRule.class)
                    );
                    rulesCache.put(entry.getKey(), rules);
                } catch (Exception e) {
                    System.err.println("[FLINK] 反序列化规则失败: " + entry.getKey());
                }
            }

            System.out.println("[FLINK] 报警处理器已启动, 加载了 " + rulesCache.size() + " 个点位规则");
            System.out.println("[FLINK] 最新状态将写入 Redis key: " + REDIS_LATEST_KEY);
        }

        @Override
        public String map(String message) throws Exception {
            if (rulesCache == null || rulesCache.isEmpty()) return "[SKIP] 规则未加载";

            // 1. 解析 Kafka 消息
            PointData point;
            try {
                point = objectMapper.readValue(message, PointData.class);
            } catch (Exception e) {
                return "[SKIP] JSON 解析失败";
            }

            String path = point.getPath();
            if (path == null) return "[SKIP] path is null";

            // 2. 查找规则并评估
            List<AlarmRule> pointRules = rulesCache.get(path);
            if (pointRules == null || pointRules.isEmpty()) return "[SKIP] 未找到规则: " + path;

            AlarmRule triggered = evaluate(point.getValue(), pointRules);
            String ts = formatTimestamp(point.getTimestamp());

            // 3. 构建最新状态并写入 Redis
            PointAlarmState state = new PointAlarmState();
            // 复制 PointData 的所有字段
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
            // 报警评估结果
            if (triggered != null) {
                state.setLevel(triggered.getLevel());
                state.setSeverity(triggered.getSeverity());
                state.setMessage(triggered.getMessage());
            } else {
                state.setLevel("Normal");
                state.setSeverity("Normal");
                state.setMessage("within normal range");
            }
            // 附加字段
            state.setAckBy("-");
            state.setStatus("Unack");
            state.setSource("");
            state.setLocation("");

            // 写入 Redis hash: alarm:latest, field=path, value=JSON
            try {
                String json = objectMapper.writeValueAsString(state);
                jedis.hset(REDIS_LATEST_KEY, path, json);
            } catch (Exception e) {
                System.err.println("[FLINK] 写入 Redis 最新状态失败: " + path + " - " + e.getMessage());
            }

            // 4. 控制台输出
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

        // ── 阈值评估 ────────────────────────────────────────────────────

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
