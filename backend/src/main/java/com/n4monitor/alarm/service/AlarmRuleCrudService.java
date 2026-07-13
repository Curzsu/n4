package com.n4monitor.alarm.service;

import com.n4monitor.entity.AlarmRuleEntity;
import com.n4monitor.mapper.AlarmRuleMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * CRUD service for alarm rules stored in MySQL (alarm_rule table).
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AlarmRuleCrudService {

    private final AlarmRuleMapper alarmRuleMapper;

    /** Matches alarm_name suffix "_NN" to extract the sequence number. */
    private static final Pattern SEQ_PATTERN = Pattern.compile("_(\\d+)$");

    public List<AlarmRuleEntity> getAllRules() {
        return alarmRuleMapper.findAll();
    }

    public AlarmRuleEntity getRuleById(Long id) {
        return alarmRuleMapper.findById(id);
    }

    public AlarmRuleEntity createRule(AlarmRuleEntity entity) {
        // Generate alarm_name from pointName: {pointName}_{NN}.
        // NN starts from 00, using the lowest unused number among non-deleted rules.
        String pointName = entity.getPointName();
        if (pointName == null || pointName.isBlank()) {
            throw new IllegalArgumentException("pointName is required when creating a rule");
        }
        entity.setAlarmName(generateAlarmName(pointName));
        alarmRuleMapper.insert(entity);
        log.info("Created alarm rule: id={}, alarmName={}", entity.getId(), entity.getAlarmName());
        return entity;
    }

    public AlarmRuleEntity updateRule(AlarmRuleEntity entity) {
        alarmRuleMapper.update(entity);
        log.info("Updated alarm rule: id={}, alarmName={}", entity.getId(), entity.getAlarmName());
        return entity;
    }

    public void deleteRule(Long id) {
        alarmRuleMapper.deleteById(id);
        log.info("Deleted alarm rule: id={}", id);
    }

    /**
     * Generate the next alarm_name for the given point name.
     * Finds the lowest unused sequence number (starting from 0) among ALL rules
     * with the same point name prefix — including logically deleted ones, because
     * the unique index uk_alarm_name covers deleted rows too and a deleted rule's
     * alarm_name still occupies that index slot.
     */
    private String generateAlarmName(String pointName) {
        List<String> existing = alarmRuleMapper.findAlarmNamesByPointNamePrefix(pointName);

        java.util.Set<Integer> usedSeqs = new java.util.HashSet<>();
        for (String name : existing) {
            Matcher m = SEQ_PATTERN.matcher(name);
            if (m.find()) {
                try {
                    usedSeqs.add(Integer.parseInt(m.group(1)));
                } catch (NumberFormatException ignored) {
                }
            }
        }

        int nextSeq = 0;
        while (usedSeqs.contains(nextSeq)) {
            nextSeq++;
        }
        return String.format("%s_%02d", pointName, nextSeq);
    }
}
