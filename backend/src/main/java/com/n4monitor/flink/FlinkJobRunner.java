package com.n4monitor.flink;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

/**
 * Automatically start Flink alarm processing job on Spring Boot startup.
 * <p>
 * Runs Flink job in a background daemon thread, starts and stops with Spring Boot.
 * Can be disabled via alarm.flink.enabled=false.
 */
@Slf4j
@Component
@Order(20)  // Start after rule loading (Order 0)
@RequiredArgsConstructor
public class FlinkJobRunner implements CommandLineRunner {

    @Value("${spring.kafka.bootstrap-servers}")
    private String kafkaBroker;

    @Value("${alarm.flink.topic}")
    private String kafkaTopic;

    @Value("${alarm.flink.group-id}")
    private String groupId;

    @Value("${spring.data.redis.host}")
    private String redisHost;

    @Value("${spring.data.redis.port:6379}")
    private int redisPort;

    @Value("${spring.data.redis.password:}")
    private String redisPassword;

    @Value("${alarm.flink.enabled}")
    private boolean enabled;

    @Value("${alarm.redis.rules-numeric-key}")
    private String rulesNumericKey;

    @Value("${alarm.redis.rules-enum-key}")
    private String rulesEnumKey;

    @Value("${alarm.redis.latest-key}")
    private String latestRedisKey;

    @Value("${alarm.redis.active-key}")
    private String activeRedisKey;

    @Value("${alarm.redis.notification-channel}")
    private String notificationChannel;

    @Value("${alarm.redis.realtime-channel}")
    private String realtimeChannel;

    @Value("${alarm.redis.point-rule-map-key:alarm:point:rule}")
    private String pointRuleMapKey;

    @Override
    public void run(String... args) {
        if (!enabled) {
            log.info("Flink alarm processing job is disabled (alarm.flink.enabled=false)");
            return;
        }

        Thread flinkThread = new Thread(() -> {
            try {
                log.info("Starting Flink alarm processing job...");
                log.info("  Kafka: {} topic={}", kafkaBroker, kafkaTopic);
                log.info("  Redis: {}:{}", redisHost, redisPort);
                log.info("  Rules numeric key: {}", rulesNumericKey);
                log.info("  Rules enum key: {}", rulesEnumKey);
                log.info("  Latest key: {}", latestRedisKey);
                log.info("  Active key: {}", activeRedisKey);
                log.info("  Notification channel: {}", notificationChannel);
                log.info("  Realtime channel: {}", realtimeChannel);
                log.info("  Point rule map key: {}", pointRuleMapKey);

                AlarmProcessingJob.run(kafkaBroker, kafkaTopic, groupId,
                        redisHost, redisPort, redisPassword,
                        rulesNumericKey, rulesEnumKey,
                        latestRedisKey, activeRedisKey,
                        notificationChannel, realtimeChannel,
                        pointRuleMapKey);
            } catch (InterruptedException e) {
                log.info("Flink job thread interrupted, stopping...");
                Thread.currentThread().interrupt();
            } catch (Exception e) {
                log.error("Flink job exited abnormally: {}", e.getMessage(), e);
            }
        }, "flink-alarm-thread");

        flinkThread.setDaemon(true);
        flinkThread.start();

        log.info("Flink alarm processing job has started in background thread");
    }
}
