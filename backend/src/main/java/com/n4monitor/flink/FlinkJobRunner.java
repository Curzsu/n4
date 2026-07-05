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

    @Value("${alarm.flink.topic:obix-mqtt-points}")
    private String kafkaTopic;

    @Value("${alarm.flink.group-id:n4-flink-alarm-processor}")
    private String groupId;

    @Value("${spring.data.redis.host}")
    private String redisHost;

    @Value("${spring.data.redis.port:6379}")
    private int redisPort;

    @Value("${alarm.flink.enabled:true}")
    private boolean enabled;

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

                AlarmProcessingJob.run(kafkaBroker, kafkaTopic, groupId,
                        redisHost, redisPort);
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
