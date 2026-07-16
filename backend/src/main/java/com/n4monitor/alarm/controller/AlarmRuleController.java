package com.n4monitor.alarm.controller;

import com.n4monitor.alarm.service.AlarmRuleCrudService;
import com.n4monitor.alarm.service.AlarmRuleLoader;
import com.n4monitor.alarm.service.AlarmRuleService;
import com.n4monitor.dto.ApiResponse;
import com.n4monitor.entity.AlarmRuleBooleanEntity;
import com.n4monitor.entity.AlarmRuleEnumEntity;
import com.n4monitor.entity.AlarmRuleNumericEntity;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * Alarm rule management API.
 * CRUD endpoints are split by point type: boolean, numeric, enum.
 */
@RestController
@RequestMapping("/api/alarm-rules")
@RequiredArgsConstructor
public class AlarmRuleController {

    private final AlarmRuleService alarmRuleService;
    private final AlarmRuleLoader alarmRuleLoader;
    private final AlarmRuleCrudService alarmRuleCrudService;

    @Value("${alarm.rule.excel-file}")
    private String excelFilePath;

    @Value("${alarm.rule.log-file}")
    private String logFilePath;

    // ── Reload + Count (Redis, from Excel) ───────────────────────────────────

    @PostMapping("/reload")
    public ApiResponse<String> reloadRules() {
        alarmRuleLoader.loadRules(excelFilePath, logFilePath);
        return ApiResponse.success("Alarm rules reloaded to Redis (Excel + Log)");
    }

    @GetMapping("/count")
    public ApiResponse<Map<String, Object>> ruleCount() {
        var rules = alarmRuleService.loadAllRules();
        return ApiResponse.success(Map.of(
                "totalPoints", rules.size(),
                "loaded", !rules.isEmpty()
        ));
    }

    // ── Boolean CRUD ─────────────────────────────────────────────────────────

    @GetMapping("/boolean")
    public ApiResponse<List<AlarmRuleBooleanEntity>> listBooleanRules() {
        return ApiResponse.success(alarmRuleCrudService.getAllBooleanRules());
    }

    @PostMapping("/boolean")
    public ApiResponse<AlarmRuleBooleanEntity> createBooleanRule(@RequestBody AlarmRuleBooleanEntity entity) {
        return ApiResponse.success(alarmRuleCrudService.createBooleanRule(entity));
    }

    @PutMapping("/boolean/{id}")
    public ApiResponse<AlarmRuleBooleanEntity> updateBooleanRule(@PathVariable Long id,
                                                                  @RequestBody AlarmRuleBooleanEntity entity) {
        entity.setId(id);
        return ApiResponse.success(alarmRuleCrudService.updateBooleanRule(entity));
    }

    @DeleteMapping("/boolean/{id}")
    public ApiResponse<Void> deleteBooleanRule(@PathVariable Long id) {
        alarmRuleCrudService.deleteBooleanRule(id);
        return ApiResponse.success(null);
    }

    // ── Numeric CRUD ─────────────────────────────────────────────────────────

    @GetMapping("/numeric")
    public ApiResponse<List<AlarmRuleNumericEntity>> listNumericRules() {
        return ApiResponse.success(alarmRuleCrudService.getAllNumericRules());
    }

    @PostMapping("/numeric")
    public ApiResponse<AlarmRuleNumericEntity> createNumericRule(@RequestBody AlarmRuleNumericEntity entity) {
        return ApiResponse.success(alarmRuleCrudService.createNumericRule(entity));
    }

    @PutMapping("/numeric/{id}")
    public ApiResponse<AlarmRuleNumericEntity> updateNumericRule(@PathVariable Long id,
                                                                  @RequestBody AlarmRuleNumericEntity entity) {
        entity.setId(id);
        return ApiResponse.success(alarmRuleCrudService.updateNumericRule(entity));
    }

    @DeleteMapping("/numeric/{id}")
    public ApiResponse<Void> deleteNumericRule(@PathVariable Long id) {
        alarmRuleCrudService.deleteNumericRule(id);
        return ApiResponse.success(null);
    }

    // ── Enum CRUD ────────────────────────────────────────────────────────────

    @GetMapping("/enum")
    public ApiResponse<List<AlarmRuleEnumEntity>> listEnumRules() {
        return ApiResponse.success(alarmRuleCrudService.getAllEnumRules());
    }

    @PostMapping("/enum")
    public ApiResponse<List<AlarmRuleEnumEntity>> createEnumRules(@RequestBody List<AlarmRuleEnumEntity> entities) {
        return ApiResponse.success(alarmRuleCrudService.createEnumRules(entities));
    }

    @PutMapping("/enum/{alarmName}")
    public ApiResponse<List<AlarmRuleEnumEntity>> updateEnumRules(@PathVariable String alarmName,
                                                                   @RequestBody List<AlarmRuleEnumEntity> entities) {
        return ApiResponse.success(alarmRuleCrudService.updateEnumRules(alarmName, entities));
    }

    @DeleteMapping("/enum/{alarmName}")
    public ApiResponse<Void> deleteEnumRules(@PathVariable String alarmName) {
        alarmRuleCrudService.deleteEnumRulesByAlarmName(alarmName);
        return ApiResponse.success(null);
    }
}
