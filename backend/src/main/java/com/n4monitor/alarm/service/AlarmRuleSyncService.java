package com.n4monitor.alarm.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.n4monitor.entity.AlarmRuleEnumEntity;
import com.n4monitor.entity.AlarmRuleNumericEntity;
import com.n4monitor.mapper.AlarmRuleEnumMapper;
import com.n4monitor.mapper.AlarmRuleNumericMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.stream.Collectors;

/**
 * Syncs alarm rules from MySQL to Redis (two type-specific hashes) and provides
 * cache-first read-through queries for the frontend.
 *
 * Redis structure:
 *   alarm:rules:numeric -> hash, field=alarm_name, value=NumericEntity JSON
 *   alarm:rules:enum    -> hash, field=alarm_name, value=List<EnumEntity> JSON
 * (Boolean type has been merged into Enum as string-based matching.)
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AlarmRuleSyncService {

    @Value("${alarm.redis.rules-numeric-key}")
    private String rulesNumericKey;

    @Value("${alarm.redis.rules-enum-key}")
    private String rulesEnumKey;

    private final StringRedisTemplate redisTemplate;
    private final AlarmRuleNumericMapper numericMapper;
    private final AlarmRuleEnumMapper enumMapper;
    private final ObjectMapper objectMapper;

    // ═══════════════════════════════════════════════════════════════════════
    //  Full sync (startup + reload)
    // ═══════════════════════════════════════════════════════════════════════

    public void syncAllRulesToRedis() {
        syncNumericRulesToRedis();
        syncEnumRulesToRedis();
        log.info("All alarm rules synced to Redis");
    }

    // ═══════════════════════════════════════════════════════════════════════
    //  Per-type sync (called after CRUD operations)
    // ═══════════════════════════════════════════════════════════════════════

    public void syncNumericRulesToRedis() {
        List<AlarmRuleNumericEntity> rules = numericMapper.findAll();
        Map<String, String> redisData = new HashMap<>();
        for (AlarmRuleNumericEntity e : rules) {
            try {
                redisData.put(e.getAlarmName(), objectMapper.writeValueAsString(e));
            } catch (Exception ex) {
                log.error("Failed to serialize numeric rule: {}", e.getAlarmName(), ex);
            }
        }
        redisTemplate.delete(rulesNumericKey);
        if (!redisData.isEmpty()) {
            redisTemplate.opsForHash().putAll(rulesNumericKey, redisData);
        }
        log.info("Synced {} numeric rules to Redis key '{}'", redisData.size(), rulesNumericKey);
    }

    public void syncEnumRulesToRedis() {
        List<AlarmRuleEnumEntity> rules = enumMapper.findAll();
        // Group by alarm_name -> List<EnumEntity>
        Map<String, List<AlarmRuleEnumEntity>> grouped = rules.stream()
                .collect(Collectors.groupingBy(AlarmRuleEnumEntity::getAlarmName, LinkedHashMap::new, Collectors.toList()));

        Map<String, String> redisData = new HashMap<>();
        for (Map.Entry<String, List<AlarmRuleEnumEntity>> entry : grouped.entrySet()) {
            try {
                redisData.put(entry.getKey(), objectMapper.writeValueAsString(entry.getValue()));
            } catch (Exception ex) {
                log.error("Failed to serialize enum rules: {}", entry.getKey(), ex);
            }
        }
        redisTemplate.delete(rulesEnumKey);
        if (!redisData.isEmpty()) {
            redisTemplate.opsForHash().putAll(rulesEnumKey, redisData);
        }
        log.info("Synced {} enum rule groups to Redis key '{}'", redisData.size(), rulesEnumKey);
    }

    // ═══════════════════════════════════════════════════════════════════════
    //  Cache-first read-through (for frontend queries)
    // ═══════════════════════════════════════════════════════════════════════

    public List<AlarmRuleNumericEntity> getNumericRulesWithCache() {
        Map<Object, Object> cached = redisTemplate.opsForHash().entries(rulesNumericKey);
        if (cached != null && !cached.isEmpty()) {
            List<AlarmRuleNumericEntity> result = new ArrayList<>();
            for (Object v : cached.values()) {
                try {
                    result.add(objectMapper.readValue((String) v, AlarmRuleNumericEntity.class));
                } catch (Exception e) {
                    log.error("Failed to deserialize numeric rule from Redis", e);
                }
            }
            return result;
        }
        List<AlarmRuleNumericEntity> rules = numericMapper.findAll();
        syncNumericRulesToRedis();
        return rules;
    }

    public List<AlarmRuleEnumEntity> getEnumRulesWithCache() {
        Map<Object, Object> cached = redisTemplate.opsForHash().entries(rulesEnumKey);
        if (cached != null && !cached.isEmpty()) {
            List<AlarmRuleEnumEntity> result = new ArrayList<>();
            for (Object v : cached.values()) {
                try {
                    List<AlarmRuleEnumEntity> group = objectMapper.readValue(
                            (String) v, new TypeReference<>() {});
                    result.addAll(group);
                } catch (Exception e) {
                    log.error("Failed to deserialize enum rules from Redis", e);
                }
            }
            return result;
        }
        List<AlarmRuleEnumEntity> rules = enumMapper.findAll();
        syncEnumRulesToRedis();
        return rules;
    }
}
