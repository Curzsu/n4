package com.n4monitor.alarm.service;

import com.n4monitor.entity.AlarmRuleBooleanEntity;
import com.n4monitor.entity.AlarmRuleEnumEntity;
import com.n4monitor.entity.AlarmRuleNumericEntity;
import com.n4monitor.mapper.AlarmRuleBooleanMapper;
import com.n4monitor.mapper.AlarmRuleEnumMapper;
import com.n4monitor.mapper.AlarmRuleNumericMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * CRUD service for alarm rules stored in MySQL, split across three type-specific tables:
 * alarm_rule_boolean, alarm_rule_numeric, alarm_rule_enum.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AlarmRuleCrudService {

    private final AlarmRuleBooleanMapper booleanMapper;
    private final AlarmRuleNumericMapper numericMapper;
    private final AlarmRuleEnumMapper enumMapper;
    private final AlarmRuleSyncService alarmRuleSyncService;

    private static final Pattern SEQ_PATTERN = Pattern.compile("_(\\d+)$");

    // ── Boolean ──────────────────────────────────────────────────────────────

    public List<AlarmRuleBooleanEntity> getAllBooleanRules() {
        return booleanMapper.findAll();
    }

    public AlarmRuleBooleanEntity createBooleanRule(AlarmRuleBooleanEntity entity) {
        entity.setAlarmName(generateAlarmName(entity.getPointName()));
        entity.setPointType("BOOL");
        booleanMapper.insert(entity);
        log.info("Created boolean alarm rule: id={}, alarmName={}", entity.getId(), entity.getAlarmName());
        alarmRuleSyncService.syncBooleanRulesToRedis();
        return entity;
    }

    public AlarmRuleBooleanEntity updateBooleanRule(AlarmRuleBooleanEntity entity) {
        booleanMapper.update(entity);
        log.info("Updated boolean alarm rule: id={}", entity.getId());
        alarmRuleSyncService.syncBooleanRulesToRedis();
        return entity;
    }

    public void deleteBooleanRule(Long id) {
        booleanMapper.deleteById(id);
        log.info("Deleted boolean alarm rule: id={}", id);
        alarmRuleSyncService.syncBooleanRulesToRedis();
    }

    // ── Numeric ──────────────────────────────────────────────────────────────

    public List<AlarmRuleNumericEntity> getAllNumericRules() {
        return numericMapper.findAll();
    }

    public AlarmRuleNumericEntity createNumericRule(AlarmRuleNumericEntity entity) {
        entity.setAlarmName(generateAlarmName(entity.getPointName()));
        entity.setPointType("NUMERIC");
        numericMapper.insert(entity);
        log.info("Created numeric alarm rule: id={}, alarmName={}", entity.getId(), entity.getAlarmName());
        alarmRuleSyncService.syncNumericRulesToRedis();
        return entity;
    }

    public AlarmRuleNumericEntity updateNumericRule(AlarmRuleNumericEntity entity) {
        numericMapper.update(entity);
        log.info("Updated numeric alarm rule: id={}", entity.getId());
        alarmRuleSyncService.syncNumericRulesToRedis();
        return entity;
    }

    public void deleteNumericRule(Long id) {
        numericMapper.deleteById(id);
        log.info("Deleted numeric alarm rule: id={}", id);
        alarmRuleSyncService.syncNumericRulesToRedis();
    }

    // ── Enum ─────────────────────────────────────────────────────────────────

    public List<AlarmRuleEnumEntity> getAllEnumRules() {
        return enumMapper.findAll();
    }

    /** Create multiple enum value rows for one point (all share the same alarm_name). */
    public List<AlarmRuleEnumEntity> createEnumRules(List<AlarmRuleEnumEntity> entities) {
        if (entities == null || entities.isEmpty()) {
            throw new IllegalArgumentException("At least one enum value is required");
        }
        checkDuplicateEnumValues(entities);
        String pointName = entities.get(0).getPointName();
        String alarmName = generateAlarmName(pointName);
        for (AlarmRuleEnumEntity e : entities) {
            e.setAlarmName(alarmName);
            e.setPointType("ENUM");
            enumMapper.insert(e);
        }
        log.info("Created enum alarm rules: alarmName={}, count={}", alarmName, entities.size());
        alarmRuleSyncService.syncEnumRulesToRedis();
        return entities;
    }

    /** Update enum rules for a point: delete all existing rows by alarm_name, then re-insert. */
    public List<AlarmRuleEnumEntity> updateEnumRules(String alarmName, List<AlarmRuleEnumEntity> entities) {
        if (entities == null || entities.isEmpty()) {
            throw new IllegalArgumentException("At least one enum value is required");
        }
        checkDuplicateEnumValues(entities);
        enumMapper.deleteByAlarmName(alarmName);
        for (AlarmRuleEnumEntity e : entities) {
            e.setAlarmName(alarmName);
            e.setPointType("ENUM");
            enumMapper.insert(e);
        }
        log.info("Updated enum alarm rules: alarmName={}, count={}", alarmName, entities.size());
        alarmRuleSyncService.syncEnumRulesToRedis();
        return entities;
    }

    public void deleteEnumRulesByAlarmName(String alarmName) {
        enumMapper.deleteByAlarmName(alarmName);
        log.info("Deleted enum alarm rules: alarmName={}", alarmName);
        alarmRuleSyncService.syncEnumRulesToRedis();
    }

    // ── Shared ───────────────────────────────────────────────────────────────

    /**
     * Generate the next alarm_name for the given point name.
     * Searches across ALL three tables to find used sequence numbers
     * (including logically deleted rows) and returns the lowest unused one.
     */
    private String generateAlarmName(String pointName) {
        if (pointName == null || pointName.isBlank()) {
            throw new IllegalArgumentException("pointName is required when creating a rule");
        }

        Set<Integer> usedSeqs = new HashSet<>();
        collectUsedSeqs(booleanMapper.findAlarmNamesByPointNamePrefix(pointName), usedSeqs);
        collectUsedSeqs(numericMapper.findAlarmNamesByPointNamePrefix(pointName), usedSeqs);
        collectUsedSeqs(enumMapper.findAlarmNamesByPointNamePrefix(pointName), usedSeqs);

        int nextSeq = 0;
        while (usedSeqs.contains(nextSeq)) {
            nextSeq++;
        }
        return String.format("%s_%02d", pointName, nextSeq);
    }

    private void collectUsedSeqs(List<String> names, Set<Integer> usedSeqs) {
        if (names == null) return;
        for (String name : names) {
            Matcher m = SEQ_PATTERN.matcher(name);
            if (m.find()) {
                try {
                    usedSeqs.add(Integer.parseInt(m.group(1)));
                } catch (NumberFormatException ignored) {
                }
            }
        }
    }

    /**
     * Check for duplicate enum_value within the same batch.
     * Throws before any insert so partial data is never written.
     */
    private void checkDuplicateEnumValues(List<AlarmRuleEnumEntity> entities) {
        Set<Integer> seen = new HashSet<>();
        for (AlarmRuleEnumEntity e : entities) {
            if (e.getEnumValue() == null) {
                throw new IllegalArgumentException("Enum value cannot be null");
            }
            if (!seen.add(e.getEnumValue())) {
                throw new IllegalArgumentException(
                    "Duplicate enum value: " + e.getEnumValue() + ". Each value must be unique.");
            }
        }
    }
}
