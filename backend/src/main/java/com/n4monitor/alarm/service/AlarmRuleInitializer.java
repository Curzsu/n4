package com.n4monitor.alarm.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

/**
 * 应用启动时自动加载报警规则到 Redis
 * <p>
 * 加载流程:
 * 1. 先从 Excel (alarm_rules_config.xlsx) 读取用户已配置的规则
 * 2. 对 Excel 中未配置的点位, 从日志文件自动生成模拟规则
 * 3. 合并后写入 Redis
 * <p>
 * 如果 Redis 中已有规则数据, 则跳过。
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
                log.info("Redis 中已有报警规则, 跳过自动加载");
                return;
            }

            log.info("Redis 中尚无报警规则, 开始加载 (Excel + 日志)...");
            alarmRuleLoader.loadRules(excelFilePath, logFilePath);

        } catch (Exception e) {
            log.warn("报警规则自动加载失败 (可能是 Redis 未就绪): {}", e.getMessage());
            log.warn("可通过 POST /api/alarm-rules/reload 手动触发加载");
        }
    }
}
