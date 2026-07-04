package com.n4monitor.flink;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

/**
 * Spring Boot 启动时自动启动 Flink 报警处理作业
 * <p>
 * 在后台守护线程中运行 Flink 作业, 随 Spring Boot 一起启动和关闭。
 * 通过 alarm.flink.enabled=false 可禁用。
 */
@Slf4j
@Component
@Order(20)  // 在规则加载 (Order 0) 之后启动
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
            log.info("Flink 报警处理作业已禁用 (alarm.flink.enabled=false)");
            return;
        }

        Thread flinkThread = new Thread(() -> {
            try {
                log.info("启动 Flink 报警处理作业...");
                log.info("  Kafka: {} topic={}", kafkaBroker, kafkaTopic);
                log.info("  Redis: {}:{}", redisHost, redisPort);

                AlarmProcessingJob.run(kafkaBroker, kafkaTopic, groupId,
                        redisHost, redisPort);
            } catch (InterruptedException e) {
                log.info("Flink 作业线程被中断, 正在停止...");
                Thread.currentThread().interrupt();
            } catch (Exception e) {
                log.error("Flink 作业异常退出: {}", e.getMessage(), e);
            }
        }, "flink-alarm-thread");

        flinkThread.setDaemon(true);
        flinkThread.start();

        log.info("Flink 报警处理作业已在后台线程启动");
    }
}
