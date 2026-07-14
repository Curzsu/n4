package com.n4monitor.alarm.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.n4monitor.alarm.model.AlarmRule;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.util.*;

/**
 * Alarm rule Redis management service.
 * <p>
 * Responsible for reading and writing alarm rules from/to Redis,
 * used by Kafka consumers for real-time evaluation.
 * Redis key is configurable via {@code alarm.redis.rules-key}.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AlarmRuleService {

    @Value("${alarm.redis.rules-key:alarm:rules}")
    private String rulesRedisKey;

    private final StringRedisTemplate redisTemplate;
    private final ObjectMapper objectMapper = new ObjectMapper();

    /**
     * Save all rules to Redis in batch (overwrites existing data).
     */
    public void saveAllRules(Map<String, List<AlarmRule>> allRules) {
        redisTemplate.delete(rulesRedisKey);

        Map<String, String> redisData = new HashMap<>();
        for (Map.Entry<String, List<AlarmRule>> entry : allRules.entrySet()) {
            try {
                redisData.put(entry.getKey(), objectMapper.writeValueAsString(entry.getValue()));
            } catch (Exception e) {
                log.error("Failed to serialize rules, path={}", entry.getKey(), e);
            }
        }

        if (!redisData.isEmpty()) {
            redisTemplate.opsForHash().putAll(rulesRedisKey, redisData);
        }
        log.info("Stored {} point rules to Redis key '{}'", redisData.size(), rulesRedisKey);
    }

    /**
     * Load all rules from Redis into memory.
     */
    public Map<String, List<AlarmRule>> loadAllRules() {
        Map<Object, Object> raw = redisTemplate.opsForHash().entries(rulesRedisKey);
        Map<String, List<AlarmRule>> rules = new HashMap<>();

        for (Map.Entry<Object, Object> entry : raw.entrySet()) {
            try {
                String path = (String) entry.getKey();
                String json = (String) entry.getValue();
                List<AlarmRule> ruleList = objectMapper.readValue(json, new TypeReference<>() {});
                rules.put(path, ruleList);
            } catch (Exception e) {
                log.error("Failed to deserialize rules, key={}", entry.getKey(), e);
            }
        }

        log.info("Loaded {} point rules from Redis", rules.size());
        return rules;
    }

    /**
     * Get rules for a single point.
     */
    public List<AlarmRule> getRules(String path) {
        String json = (String) redisTemplate.opsForHash().get(rulesRedisKey, path);
        if (json == null) return Collections.emptyList();
        try {
            return objectMapper.readValue(json, new TypeReference<>() {});
        } catch (Exception e) {
            log.error("Failed to deserialize rules, path={}", path, e);
            return Collections.emptyList();
        }
    }

    /**
     * Check if rule data already exists in Redis.
     */
    public boolean hasRules() {
        Long size = redisTemplate.opsForHash().size(rulesRedisKey);
        return size != null && size > 0;
    }
}
