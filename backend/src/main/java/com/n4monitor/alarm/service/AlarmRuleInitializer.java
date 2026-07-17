package com.n4monitor.alarm.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

/**
 * Automatically syncs alarm rules from MySQL to Redis on application startup.
 * Runs before the Flink job (Order 20) so rules are available when Flink opens.
 */
@Slf4j
@Component
@RequiredArgsConstructor
@Order(0)
public class AlarmRuleInitializer implements ApplicationRunner {

    private final AlarmRuleSyncService alarmRuleSyncService;

    @Override
    public void run(ApplicationArguments args) {
        try {
            log.info("Syncing alarm rules from MySQL to Redis...");
            alarmRuleSyncService.syncAllRulesToRedis();
        } catch (Exception e) {
            log.warn("Auto sync of alarm rules failed (MySQL/Redis may not be ready): {}", e.getMessage());
        }
    }
}
