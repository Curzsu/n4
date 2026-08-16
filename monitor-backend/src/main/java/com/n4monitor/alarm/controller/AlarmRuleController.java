package com.n4monitor.alarm.controller;

import com.n4monitor.alarm.service.AlarmRuleCrudService;
import com.n4monitor.alarm.service.AlarmRuleSyncService;
import com.n4monitor.common.ApiResponse;
import com.n4monitor.entity.AlarmRuleEnumEntity;
import com.n4monitor.entity.AlarmRuleNumericEntity;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * Alarm rule management API.
 * CRUD endpoints are split by point type: numeric, enum.
 * (Boolean type has been merged into Enum as string-based matching.)
 * List endpoints read from Redis first (cache-first), falling back to MySQL.
 */
@RestController
@RequestMapping("/api/alarm-rules")
@RequiredArgsConstructor
public class AlarmRuleController {

    private final AlarmRuleCrudService alarmRuleCrudService;
    private final AlarmRuleSyncService alarmRuleSyncService;

    // ── Reload (full MySQL -> Redis sync) ────────────────────────────────────

    @PostMapping("/reload")
    public ApiResponse<String> reloadRules() {
        alarmRuleSyncService.syncAllRulesToRedis();
        alarmRuleSyncService.syncPointRuleMapToRedis();
        return ApiResponse.success("Alarm rules and point-rule mappings reloaded");
    }

    // ── Numeric CRUD ─────────────────────────────────────────────────────────

    @GetMapping("/numeric")
    public ApiResponse<List<AlarmRuleNumericEntity>> listNumericRules() {
        return ApiResponse.success(alarmRuleSyncService.getNumericRulesWithCache());
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
        return ApiResponse.success(alarmRuleSyncService.getEnumRulesWithCache());
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
