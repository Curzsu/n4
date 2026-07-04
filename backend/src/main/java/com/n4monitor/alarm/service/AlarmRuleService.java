package com.n4monitor.alarm.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.n4monitor.alarm.model.AlarmRule;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.util.*;

/**
 * 报警规则 Redis 管理服务
 * <p>
 * 负责从 Redis 中读写报警规则，供 Kafka 消费者实时评估使用。
 * Redis key: alarm:rules (Hash)
 *   field: 点位路径 path
 *   value: JSON 数组 [AlarmRule, ...]
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AlarmRuleService {

    private static final String REDIS_KEY = "alarm:rules";

    private final StringRedisTemplate redisTemplate;
    private final ObjectMapper objectMapper = new ObjectMapper();

    /**
     * 将所有规则批量写入 Redis（覆盖已有数据）
     */
    public void saveAllRules(Map<String, List<AlarmRule>> allRules) {
        redisTemplate.delete(REDIS_KEY);

        Map<String, String> redisData = new HashMap<>();
        for (Map.Entry<String, List<AlarmRule>> entry : allRules.entrySet()) {
            try {
                redisData.put(entry.getKey(), objectMapper.writeValueAsString(entry.getValue()));
            } catch (Exception e) {
                log.error("序列化规则失败, path={}", entry.getKey(), e);
            }
        }

        if (!redisData.isEmpty()) {
            redisTemplate.opsForHash().putAll(REDIS_KEY, redisData);
        }
        log.info("已存储 {} 个点位规则到 Redis key '{}'", redisData.size(), REDIS_KEY);
    }

    /**
     * 从 Redis 加载全部规则到内存
     */
    public Map<String, List<AlarmRule>> loadAllRules() {
        Map<Object, Object> raw = redisTemplate.opsForHash().entries(REDIS_KEY);
        Map<String, List<AlarmRule>> rules = new HashMap<>();

        for (Map.Entry<Object, Object> entry : raw.entrySet()) {
            try {
                String path = (String) entry.getKey();
                String json = (String) entry.getValue();
                List<AlarmRule> ruleList = objectMapper.readValue(json, new TypeReference<>() {});
                rules.put(path, ruleList);
            } catch (Exception e) {
                log.error("反序列化规则失败, key={}", entry.getKey(), e);
            }
        }

        log.info("从 Redis 加载了 {} 个点位规则", rules.size());
        return rules;
    }

    /**
     * 获取单个点位的规则
     */
    public List<AlarmRule> getRules(String path) {
        String json = (String) redisTemplate.opsForHash().get(REDIS_KEY, path);
        if (json == null) return Collections.emptyList();
        try {
            return objectMapper.readValue(json, new TypeReference<>() {});
        } catch (Exception e) {
            log.error("反序列化规则失败, path={}", path, e);
            return Collections.emptyList();
        }
    }

    /**
     * 检查 Redis 中是否已有规则数据
     */
    public boolean hasRules() {
        Long size = redisTemplate.opsForHash().size(REDIS_KEY);
        return size != null && size > 0;
    }
}
