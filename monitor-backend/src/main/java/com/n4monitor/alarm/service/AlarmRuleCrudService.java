package com.n4monitor.alarm.service;

import com.n4monitor.entity.AlarmRuleEnumEntity;
import com.n4monitor.entity.AlarmRuleNumericEntity;
import com.n4monitor.alarm.mapper.AlarmRuleEnumMapper;
import com.n4monitor.alarm.mapper.AlarmRuleNumericMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * CRUD service for alarm rules stored in MySQL, across two type-specific tables:
 * alarm_rule_numeric, alarm_rule_enum.
 * (Boolean type has been merged into Enum as string-based matching.)
 * <p>
 * All write methods are transactional: the delete-then-reinsert pattern used
 * for enum updates used to lose the previous rows when a later insert failed.
 * The Redis sync runs inside the same transaction, so a Redis failure rolls
 * the MySQL change back instead of leaving the rule cache stale.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AlarmRuleCrudService {

    private final AlarmRuleNumericMapper numericMapper;
    private final AlarmRuleEnumMapper enumMapper;
    private final AlarmRuleSyncService alarmRuleSyncService;

    private static final Pattern SEQ_PATTERN = Pattern.compile("_(\\d+)$");

    /**
     * Concurrent creations for the same point can generate the same
     * alarm_name (query-then-insert race). The unique key rejects the loser;
     * retry with a freshly generated name instead of surfacing a 500.
     */
    private static final int NAME_COLLISION_RETRIES = 3;

    // ── Numeric ──────────────────────────────────────────────────────────────

    public List<AlarmRuleNumericEntity> getAllNumericRules() {
        return numericMapper.findAll();
    }

    @Transactional
    public AlarmRuleNumericEntity createNumericRule(AlarmRuleNumericEntity entity) {
        entity.setPointType("numeric");
        insertNumericWithUniqueName(entity);
        log.info("Created numeric alarm rule: id={}, alarmName={}", entity.getId(), entity.getAlarmName());
        alarmRuleSyncService.syncNumericRulesToRedis();
        return entity;
    }

    @Transactional
    public AlarmRuleNumericEntity updateNumericRule(AlarmRuleNumericEntity entity) {
        numericMapper.update(entity);
        log.info("Updated numeric alarm rule: id={}", entity.getId());
        alarmRuleSyncService.syncNumericRulesToRedis();
        return entity;
    }

    @Transactional
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
    @Transactional
    public List<AlarmRuleEnumEntity> createEnumRules(List<AlarmRuleEnumEntity> entities) {
        if (entities == null || entities.isEmpty()) {
            throw new IllegalArgumentException("At least one enum value is required");
        }
        checkDuplicateEnumValues(entities);
        insertEnumsWithUniqueName(entities);
        log.info("Created enum alarm rules: alarmName={}, count={}",
                entities.get(0).getAlarmName(), entities.size());
        alarmRuleSyncService.syncEnumRulesToRedis();
        return entities;
    }

    /** Update enum rules for a point: delete all existing rows by alarm_name, then re-insert. */
    @Transactional
    public List<AlarmRuleEnumEntity> updateEnumRules(String alarmName, List<AlarmRuleEnumEntity> entities) {
        if (entities == null || entities.isEmpty()) {
            throw new IllegalArgumentException("At least one enum value is required");
        }
        checkDuplicateEnumValues(entities);
        enumMapper.deleteByAlarmName(alarmName);
        // time_delay is per-point; take from the first row and propagate to all rows
        Integer timeDelay = entities.get(0).getTimeDelay();
        for (AlarmRuleEnumEntity e : entities) {
            e.setAlarmName(alarmName);
            e.setPointType("enum");
            e.setTimeDelay(timeDelay);
            enumMapper.insert(e);
        }
        log.info("Updated enum alarm rules: alarmName={}, count={}", alarmName, entities.size());
        alarmRuleSyncService.syncEnumRulesToRedis();
        return entities;
    }

    @Transactional
    public void deleteEnumRulesByAlarmName(String alarmName) {
        enumMapper.deleteByAlarmName(alarmName);
        log.info("Deleted enum alarm rules: alarmName={}", alarmName);
        alarmRuleSyncService.syncEnumRulesToRedis();
    }

    // ── Shared ───────────────────────────────────────────────────────────────

    /** Insert a numeric rule, regenerating the sequence name on unique-key races. */
    private void insertNumericWithUniqueName(AlarmRuleNumericEntity entity) {
        for (int attempt = 1; ; attempt++) {
            entity.setAlarmName(generateAlarmName(entity.getPointName()));
            try {
                numericMapper.insert(entity);
                return;
            } catch (DuplicateKeyException e) {
                if (attempt >= NAME_COLLISION_RETRIES) {
                    throw new IllegalArgumentException(
                            "Failed to generate a unique alarm name for point " + entity.getPointName()
                                    + " after " + attempt + " attempts, please retry");
                }
                log.warn("alarm_name collision on attempt {} for point {}, regenerating",
                        attempt, entity.getPointName());
            }
        }
    }

    /** Insert enum rows, regenerating the shared alarm name on unique-key races. */
    private void insertEnumsWithUniqueName(List<AlarmRuleEnumEntity> entities) {
        String pointName = entities.get(0).getPointName();
        // time_delay is per-point; take from the first row and propagate to all rows
        Integer timeDelay = entities.get(0).getTimeDelay();
        for (int attempt = 1; ; attempt++) {
            String alarmName = generateAlarmName(pointName);
            try {
                for (AlarmRuleEnumEntity e : entities) {
                    e.setAlarmName(alarmName);
                    e.setPointType("enum");
                    e.setTimeDelay(timeDelay);
                    enumMapper.insert(e);
                }
                return;
            } catch (DuplicateKeyException e) {
                if (attempt >= NAME_COLLISION_RETRIES) {
                    throw new IllegalArgumentException(
                            "Failed to generate a unique alarm name for point " + pointName
                                    + " after " + attempt + " attempts, please retry");
                }
                log.warn("alarm_name collision on attempt {} for point {}, regenerating", attempt, pointName);
            }
        }
    }

    /**
     * Generate the next alarm_name for the given point name.
     * Searches across both tables to find used sequence numbers
     * (including logically deleted rows) and returns the lowest unused one.
     */
    private String generateAlarmName(String pointName) {
        if (pointName == null || pointName.isBlank()) {
            throw new IllegalArgumentException("pointName is required when creating a rule");
        }

        Set<Integer> usedSeqs = new HashSet<>();
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
        Set<String> seen = new HashSet<>();
        for (AlarmRuleEnumEntity e : entities) {
            if (e.getEnumValue() == null || e.getEnumValue().isBlank()) {
                throw new IllegalArgumentException("Enum value cannot be null or empty");
            }
            if (!seen.add(e.getEnumValue())) {
                throw new IllegalArgumentException(
                    "Duplicate enum value: " + e.getEnumValue() + ". Each value must be unique.");
            }
        }
    }
}
