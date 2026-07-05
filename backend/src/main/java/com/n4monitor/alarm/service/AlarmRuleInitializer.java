package com.n4monitor.alarm.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

/**
 * Automatically load alarm rules into Redis on application startup.
 * <p>
 * Loading process:
 * 1. Read user-configured rules from Excel (alarm_rules_config.xlsx)
 * 2. For points not configured in Excel, auto-generate mock rules from log file
 * 3. Merge and write to Redis
 * <p>
 * Skips if rule data already exists in Redis.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class AlarmRuleInitializer implements ApplicationRunner {

    private final AlarmRuleService alarmRuleService;
    private final AlarmRuleLoader alarmRuleLoader;

    @Value("${alarm.rule.excel-file:../材料/alarm_rules_config.xlsx}")
    private String excelFilePath;

    @Value("${alarm.rule.log-file:../材料/points-2.log}")
    private String logFilePath;

    @Override
    public void run(ApplicationArguments args) {
        try {
            if (alarmRuleService.hasRules()) {
                log.info("Alarm rules already exist in Redis, skipping auto load");
                return;
            }

            log.info("No alarm rules in Redis, starting load (Excel + Log)...");
            alarmRuleLoader.loadRules(excelFilePath, logFilePath);

        } catch (Exception e) {
            log.warn("Auto load of alarm rules failed (Redis may not be ready): {}", e.getMessage());
            log.warn("You can manually trigger loading via POST /api/alarm-rules/reload");
        }
    }
}
