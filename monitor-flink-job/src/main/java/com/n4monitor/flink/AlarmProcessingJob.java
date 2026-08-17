package com.n4monitor.flink;

import com.n4monitor.alarm.model.PointData;
import org.apache.flink.api.common.eventtime.WatermarkStrategy;
import org.apache.flink.api.common.typeinfo.TypeInformation;
import org.apache.flink.util.ParameterTool;
import org.apache.flink.connector.kafka.source.KafkaSource;
import org.apache.flink.connector.kafka.source.enumerator.initializer.OffsetsInitializer;
import org.apache.flink.api.common.serialization.SimpleStringSchema;
import org.apache.flink.streaming.api.datastream.DataStream;
import org.apache.flink.streaming.api.datastream.KeyedStream;
import org.apache.flink.streaming.api.environment.StreamExecutionEnvironment;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Duration;

/**
 * Flink alarm processing job.
 * <p>
 * Consumes device point data from Kafka, reads alarm rules from Redis (three
 * type-specific hashes keyed by alarm_name), evaluates each message in real-time,
 * and writes latest state to Redis.
 * <p>
 * Rule lookup: extracts the point path from the Kafka message, looks up the
 * point's alarm_code from the Redis hash alarm:point:rule (field = path), and
 * uses that alarm_code as the alarm_name to query the rule hashes
 * (alarm:rules:numeric / alarm:rules:enum). A point with an empty alarm_code
 * has no rule and is skipped (no evaluation). String-type messages are also skipped.
 * <p>
 * Two entry points:
 * <ul>
 *   <li>{@link #main(String[])} - standalone mode, submit the shaded
 *       {@code *-standalone.jar} to a Flink cluster via {@code flink run};
 *       all settings come from command-line arguments.</li>
 *   <li>{@link #run(String, String, String, String, int, String, String, String,
 *       String, String, String, String, String, int)} - embedded mode, called by
 *       the Spring Boot backend ({@code FlinkJobRunner}) with values injected
 *       from {@code application.yaml}.</li>
 * </ul>
 */
public class AlarmProcessingJob {

    private static final Logger log = LoggerFactory.getLogger(AlarmProcessingJob.class);

    // Dev defaults (also the fallback when a standalone submission omits an argument).
    private static final String DEFAULT_KAFKA_BROKER = "localhost:9092";
    private static final String DEFAULT_KAFKA_TOPIC = "obix-mqtt-points";
    private static final String DEFAULT_GROUP_ID = "n4-flink-alarm-processor";
    private static final String DEFAULT_REDIS_HOST = "localhost";
    private static final int DEFAULT_REDIS_PORT = 6379;

    private static final String DEFAULT_RULES_NUMERIC_KEY = "alarm:rules:numeric";
    private static final String DEFAULT_RULES_ENUM_KEY = "alarm:rules:enum";
    private static final String DEFAULT_LATEST_KEY = "alarm:latest";
    private static final String DEFAULT_ACTIVE_KEY = "alarm:active";
    private static final String DEFAULT_NOTIFICATIONS_CHANNEL = "alarm:notifications";
    private static final String DEFAULT_REALTIME_CHANNEL = "alarm:realtime";
    private static final String DEFAULT_POINT_RULE_MAP_KEY = "alarm:point:rule";
    private static final int DEFAULT_PARALLELISM = 1;

    /**
     * Standalone entry point for submission to a dedicated Flink cluster:
     * <pre>
     * flink run -d -c com.n4monitor.flink.AlarmProcessingJob monitor-flink-job-standalone.jar \
     *   --kafka-broker kafka:9092 --kafka-topic obix-mqtt-points \
     *   --group-id n4-flink-alarm-processor-prod \
     *   --redis-host redis --redis-port 6379 --redis-password secret \
     *   --parallelism 4
     * </pre>
     * Every key/value pair maps 1:1 onto the {@link #run} arguments; the Redis
     * key/channel names fall back to the same defaults the backend uses.
     */
    public static void main(String[] args) throws Exception {
        ParameterTool params = ParameterTool.fromArgs(args);

        String kafkaBroker = params.get("kafka-broker", DEFAULT_KAFKA_BROKER);
        String kafkaTopic = params.get("kafka-topic", DEFAULT_KAFKA_TOPIC);
        String groupId = params.get("group-id", DEFAULT_GROUP_ID);
        String redisHost = params.get("redis-host", DEFAULT_REDIS_HOST);
        int redisPort = params.getInt("redis-port", DEFAULT_REDIS_PORT);
        String redisPassword = params.get("redis-password", "");
        int parallelism = params.getInt("parallelism", DEFAULT_PARALLELISM);

        log.info("Standalone submission parameters: kafka={} topic={} groupId={} " +
                        "redis={}:{} parallelism={}",
                kafkaBroker, kafkaTopic, groupId, redisHost, redisPort, parallelism);

        run(kafkaBroker, kafkaTopic, groupId,
                redisHost, redisPort, redisPassword,
                params.get("rules-numeric-key", DEFAULT_RULES_NUMERIC_KEY),
                params.get("rules-enum-key", DEFAULT_RULES_ENUM_KEY),
                params.get("latest-key", DEFAULT_LATEST_KEY),
                params.get("active-key", DEFAULT_ACTIVE_KEY),
                params.get("notification-channel", DEFAULT_NOTIFICATIONS_CHANNEL),
                params.get("realtime-channel", DEFAULT_REALTIME_CHANNEL),
                params.get("point-rule-map-key", DEFAULT_POINT_RULE_MAP_KEY),
                parallelism);
    }

    /**
     * Start Flink job (blocking method, should be called in a separate thread).
     * <p>
     * Parallelism is driven by the {@code parallelism} argument (from config
     * {@code alarm.flink.parallelism}). The Kafka source parallelism is bounded
     * by the topic's partition count; a downstream {@code keyBy(path)} rebalances
     * by point path so that all messages for one point always land on the same
     * task, making the read-modify-write on Redis {@code alarm:active} safe
     * under parallel execution. Ensure the Kafka topic has at least as many
     * partitions as the configured parallelism, otherwise some tasks stay idle.
     */
    public static void run(String kafkaBroker, String kafkaTopic,
                           String groupId, String redisHost, int redisPort, String redisPassword,
                           String rulesNumericKey, String rulesEnumKey,
                           String latestRedisKey, String activeRedisKey,
                           String notificationChannel, String realtimeChannel,
                           String pointRuleMapKey, int parallelism) throws Exception {

        StreamExecutionEnvironment env = StreamExecutionEnvironment.getExecutionEnvironment();
        env.setParallelism(parallelism);

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

        // Stage 1: parse raw JSON into PointData. Stateless and fully parallel;
        // parsing failures are dropped here so downstream logic never sees a
        // malformed record.
        DataStream<PointData> parsedStream = kafkaStream.flatMap(
                new ParsePointFunction(), TypeInformation.of(PointData.class));

        // Stage 2: partition by point path. Same path always routes to the same
        // task instance, so the read-modify-write sequence on alarm:active (and
        // future per-key timing/deadband state) is serial per key and safe under
        // parallel execution. This is the prerequisite for both parallelism and
        // the upcoming delay-alarm / deadband features.
        KeyedStream<PointData, String> keyedStream = parsedStream.keyBy(PointData::getMqttPath);

        // Stage 3: rule lookup + alarm lifecycle evaluation. Runs at full
        // parallelism; keyBy guarantees one path -> one task. Uses a
        // KeyedProcessFunction so it can hold per-key pending-alarm state and
        // register processing-time timers for delay-based alarm confirmation.
        DataStream<String> alarmStream = keyedStream.process(
                new AlarmProcessFunction(redisHost, redisPort,
                redisPassword,
                        rulesNumericKey, rulesEnumKey,
                        latestRedisKey, activeRedisKey, notificationChannel, realtimeChannel,
                        pointRuleMapKey));

        alarmStream.print();

        env.execute("N4-Monitor Alarm Processing Job");
    }
}
