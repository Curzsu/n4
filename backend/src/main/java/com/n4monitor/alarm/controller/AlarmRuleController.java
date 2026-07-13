package com.n4monitor.alarm.controller;

import com.n4monitor.alarm.service.AlarmRuleCrudService;
import com.n4monitor.alarm.service.AlarmRuleLoader;
import com.n4monitor.alarm.service.AlarmRuleService;
import com.n4monitor.dto.ApiResponse;
import com.n4monitor.entity.AlarmRuleEntity;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.*;

import java.util.List;
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
    private final AlarmRuleCrudService alarmRuleCrudService;

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

    // ── CRUD endpoints (MySQL alarm_rule table) ─────────────────────────────

    @GetMapping
    public ApiResponse<List<AlarmRuleEntity>> listRules() {
        return ApiResponse.success(alarmRuleCrudService.getAllRules());
    }

    @GetMapping("/{id}")
    public ApiResponse<AlarmRuleEntity> getRule(@PathVariable Long id) {
        return ApiResponse.success(alarmRuleCrudService.getRuleById(id));
    }

    @PostMapping
    public ApiResponse<AlarmRuleEntity> createRule(@RequestBody AlarmRuleEntity entity) {
        return ApiResponse.success(alarmRuleCrudService.createRule(entity));
    }

    @PutMapping("/{id}")
    public ApiResponse<AlarmRuleEntity> updateRule(@PathVariable Long id,
                                                    @RequestBody AlarmRuleEntity entity) {
        entity.setId(id);
        return ApiResponse.success(alarmRuleCrudService.updateRule(entity));
    }

    @DeleteMapping("/{id}")
    public ApiResponse<Void> deleteRule(@PathVariable Long id) {
        alarmRuleCrudService.deleteRule(id);
        return ApiResponse.success(null);
    }
}
