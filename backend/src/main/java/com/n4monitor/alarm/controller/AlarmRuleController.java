package com.n4monitor.alarm.controller;

import com.n4monitor.alarm.service.AlarmRuleLoader;
import com.n4monitor.alarm.service.AlarmRuleService;
import com.n4monitor.dto.ApiResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/**
 * Alarm rule management API.
 */
@RestController
@RequestMapping("/api/alarm-rules")
@RequiredArgsConstructor
public class AlarmRuleController {

    private final AlarmRuleService alarmRuleService;
    private final AlarmRuleLoader alarmRuleLoader;

    @Value("${alarm.rule.excel-file:../材料/alarm_rules_config.xlsx}")
    private String excelFilePath;

    @Value("${alarm.rule.log-file:../材料/points-2.log}")
    private String logFilePath;

    /**
     * Manually trigger reload of rules (Excel + Log) into Redis.
     */
    @PostMapping("/reload")
    public ApiResponse<String> reloadRules() {
        alarmRuleLoader.loadRules(excelFilePath, logFilePath);
        return ApiResponse.success("Alarm rules reloaded to Redis (Excel + Log)");
    }

    /**
     * Query current rule count.
     */
    @GetMapping("/count")
    public ApiResponse<Map<String, Object>> ruleCount() {
        var rules = alarmRuleService.loadAllRules();
        return ApiResponse.success(Map.of(
                "totalPoints", rules.size(),
                "loaded", !rules.isEmpty()
        ));
    }
}
