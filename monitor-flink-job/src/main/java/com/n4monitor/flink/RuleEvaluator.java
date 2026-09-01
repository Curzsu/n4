package com.n4monitor.flink;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.n4monitor.alarm.model.PointAlarmState;
import com.n4monitor.entity.AlarmRuleEnumEntity;
import com.n4monitor.entity.AlarmRuleNumericEntity;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import redis.clients.jedis.Jedis;

import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * Rule evaluation for one point value against the Redis-hosted rule set
 * (numeric thresholds or enum value matching). Extracted verbatim from
 * AlarmProcessFunction; evaluation order and result semantics are unchanged.
 */
final class RuleEvaluator {

    private static final Logger log = LoggerFactory.getLogger(RuleEvaluator.class);

    private final ObjectMapper objectMapper;
    private final String rulesNumericKey;
    private final String rulesEnumKey;

    /**
     * Rule definitions change only when edited in the management UI; they are
     * cached per subtask with a TTL, so an edit takes effect within the TTL
     * without a restart. Missing rules are cached too (negative caching):
     * probing an unknown alarm code costs one Redis hit per TTL window.
     * The TTL is configurable (alarm.flink.rule-cache-ttl-seconds); this
     * constant is only the fallback when no explicit value is supplied.
     */
    static final long DEFAULT_RULE_CACHE_TTL_SECONDS = 60;

    private final TtlCache<AlarmRuleNumericEntity> numericRuleCache;
    private final TtlCache<List<AlarmRuleEnumEntity>> enumRuleCache;

    RuleEvaluator(ObjectMapper objectMapper, String rulesNumericKey, String rulesEnumKey) {
        this(objectMapper, rulesNumericKey, rulesEnumKey, DEFAULT_RULE_CACHE_TTL_SECONDS);
    }

    RuleEvaluator(ObjectMapper objectMapper, String rulesNumericKey, String rulesEnumKey,
                  long ruleCacheTtlSeconds) {
        this.objectMapper = objectMapper;
        this.rulesNumericKey = rulesNumericKey;
        this.rulesEnumKey = rulesEnumKey;
        this.numericRuleCache = new TtlCache<>(ruleCacheTtlSeconds, TimeUnit.SECONDS);
        this.enumRuleCache = new TtlCache<>(ruleCacheTtlSeconds, TimeUnit.SECONDS);
    }

    /**
     * Load one numeric rule through the TTL cache. Returns null when absent
     * in Redis or unparseable; the null is cached so repeated probes of an
     * unknown alarm code do not hit Redis again within the TTL window.
     */
    private AlarmRuleNumericEntity getNumericRule(Jedis jedis, String alarmName) {
        return numericRuleCache.get(alarmName, key -> {
            String json = jedis.hget(rulesNumericKey, key);
            if (json == null || json.isBlank()) return null;
            try {
                return objectMapper.readValue(json, AlarmRuleNumericEntity.class);
            } catch (Exception e) {
                log.warn("Failed to deserialize numeric rule: {} - {}", key, e.getMessage());
                return null;
            }
        });
    }

    /**
     * Load the enum rule list through the TTL cache. Returns null when absent
     * in Redis or unparseable (cached, same as numeric rules).
     */
    private List<AlarmRuleEnumEntity> getEnumRules(Jedis jedis, String alarmName) {
        return enumRuleCache.get(alarmName, key -> {
            String json = jedis.hget(rulesEnumKey, key);
            if (json == null || json.isBlank()) return null;
            try {
                return objectMapper.readValue(json, new TypeReference<List<AlarmRuleEnumEntity>>() {});
            } catch (Exception e) {
                log.warn("Failed to deserialize enum rules: {} - {}", key, e.getMessage());
                return null;
            }
        });
    }

    /**
     * Result of evaluating a rule against a point value.
     * <p>{@code skipped} distinguishes an unknown enum value (no rule
     * matches) from a real normal value (a rule matched but its severity
     * is empty). A skipped result must not touch alarm:active, otherwise
     * it would wrongly recover an ongoing alarm.
     */
    record EvalResult(boolean isAlarm, boolean skipped, String level, String severity,
                      String message, Integer timeDelay, String deadband) {}

    EvalResult evaluateNumeric(Jedis jedis, String alarmName, String pointValue) {
        AlarmRuleNumericEntity rule = getNumericRule(jedis, alarmName);
        if (rule == null) return null;

        try {
            Double numVal = null;
            try {
                numVal = Double.parseDouble(pointValue != null ? pointValue.trim() : "");
            } catch (Exception ignored) {}
            if (numVal == null) {
                return new EvalResult(false, false, null, null, null, null, null);
            }

            Integer timeDelay = rule.getTimeDelay();
            String deadband = rule.getDeadband();

            // Priority order: High-High, High, Low-Low, Low
            Double highHigh = parseThreshold(rule.getHighHighValue(), alarmName, "High-High");
            if (highHigh != null && numVal >= highHigh) {
                return alarm("High-High", rule.getHighHighSeverity(), rule.getHighHighMessage(), timeDelay, deadband);
            }
            Double high = parseThreshold(rule.getHighValue(), alarmName, "High");
            if (high != null && numVal >= high) {
                return alarm("High", rule.getHighSeverity(), rule.getHighMessage(), timeDelay, deadband);
            }
            Double lowLow = parseThreshold(rule.getLowLowValue(), alarmName, "Low-Low");
            if (lowLow != null && numVal <= lowLow) {
                return alarm("Low-Low", rule.getLowLowSeverity(), rule.getLowLowMessage(), timeDelay, deadband);
            }
            Double low = parseThreshold(rule.getLowValue(), alarmName, "Low");
            if (low != null && numVal <= low) {
                return alarm("Low", rule.getLowSeverity(), rule.getLowMessage(), timeDelay, deadband);
            }

            // Value is normal (no threshold tripped). Still carry timeDelay and
            // deadband so the hysteresis gate in processMessage can read them
            // from the current state - without deadband here, recovery would
            // always be instantaneous regardless of the configured deadband.
            return new EvalResult(false, false, null, null, null, timeDelay, deadband);
        } catch (Exception e) {
            log.warn("Failed to evaluate numeric rule: {} - {}", alarmName, e.getMessage());
            return null;
        }
    }

    /**
     * Parse one threshold string. Returns null when the level is not configured
     * (null/blank) or when the configured value is malformed; malformed values
     * are logged so a mistyped rule no longer fails silently.
     */
    private Double parseThreshold(String thresholdStr, String alarmName, String level) {
        if (thresholdStr == null || thresholdStr.isBlank()) return null;
        try {
            return Double.parseDouble(thresholdStr.trim());
        } catch (NumberFormatException e) {
            log.warn("Malformed {} threshold '{}' for rule {}, skipping this level check",
                    level, thresholdStr, alarmName);
            return null;
        }
    }

    private EvalResult alarm(String level, String severity, String message,
                             Integer timeDelay, String deadband) {
        return new EvalResult(true, false, level, severity, message, timeDelay, deadband);
    }

    /** Resolve the raw threshold value string for a given alarm level. */
    private String getThresholdForLevel(AlarmRuleNumericEntity rule, String level) {
        if (level == null) return null;
        return switch (level) {
            case "High-High" -> rule.getHighHighValue();
            case "High"      -> rule.getHighValue();
            case "Low-Low"   -> rule.getLowLowValue();
            case "Low"       -> rule.getLowValue();
            default          -> null;
        };
    }

    /**
     * Deadband (hysteresis) gate for alarm recovery.
     * <p>
     * When the value no longer trips any raw threshold (the caller has
     * already determined isAlarm == false) but an alarm is still active,
     * the deadband keeps the alarm alive until the value moves past the
     * recovery boundary:
     * <ul>
     *   <li>High side (High-High / High): alarm persists while
     *       {@code value >= threshold - deadband}; recovers only when
     *       {@code value < threshold - deadband}.</li>
     *   <li>Low side (Low-Low / Low): alarm persists while
     *       {@code value <= threshold + deadband}; recovers only when
     *       {@code value > threshold + deadband}.</li>
     * </ul>
     * If the deadband is absent, zero, or unparseable, no hysteresis is
     * applied and the alarm recovers at the raw threshold, preserving
     * backward compatibility.
     *
     * @param jedis    Redis connection (rule lookups go through the TTL cache)
     * @param alarmName the rule's alarm code (hash field key)
     * @param history  the currently active alarm record (carries the level)
     * @param current  the latest point state (carries the value + deadband)
     * @return {@code true} if the value is still within the deadband band
     *         and the alarm should be kept alive; {@code false} to proceed
     *         with normal recovery
     */
    boolean isInDeadband(Jedis jedis, String alarmName,
                         PointAlarmState history, PointAlarmState current) {
        String deadbandStr = current.getDeadband();
        if (isBlank(deadbandStr)) return false;
        double deadband;
        try {
            deadband = Double.parseDouble(deadbandStr.trim());
        } catch (NumberFormatException e) {
            return false;
        }
        if (deadband <= 0) return false;

        // The rule comes from the TTL cache; this also removes the redundant
        // re-read of the same hash entry that the per-frame evaluation
        // already fetched.
        AlarmRuleNumericEntity rule = getNumericRule(jedis, alarmName);
        if (rule == null) return false;

        String level = history.getLevel();
        String thresholdStr = getThresholdForLevel(rule, level);
        if (thresholdStr == null || thresholdStr.isBlank()) return false;

        double threshold;
        try {
            threshold = Double.parseDouble(thresholdStr.trim());
        } catch (NumberFormatException e) {
            return false;
        }

        double value;
        try {
            value = Double.parseDouble(current.getValue().trim());
        } catch (Exception e) {
            return false; // cannot evaluate, let normal recovery proceed
        }

        // High side: alarm persists while value >= threshold - deadband.
        if ("High-High".equals(level) || "High".equals(level)) {
            return value >= threshold - deadband;
        }
        // Low side: alarm persists while value <= threshold + deadband.
        if ("Low-Low".equals(level) || "Low".equals(level)) {
            return value <= threshold + deadband;
        }
        return false;
    }

    EvalResult evaluateEnum(Jedis jedis, String alarmName, String pointValue) {
        List<AlarmRuleEnumEntity> rules = getEnumRules(jedis, alarmName);
        if (rules == null) return null;

        String normalizedValue = pointValue != null ? pointValue.trim() : null;

        for (AlarmRuleEnumEntity rule : rules) {
            if (rule.getEnumValue() != null && normalizedValue != null
                    && normalizedValue.equalsIgnoreCase(rule.getEnumValue())) {
                // Matched the configured enum value
                if (rule.getSeverity() != null && !rule.getSeverity().isBlank()) {
                    // level carries the enum value so the lifecycle state
                    // machine can distinguish alarms on different enum
                    // values (e.g. "Enum:0" vs "Enum:2"); otherwise every
                    // enum alarm shares the same level and severity changes
                    // are wrongly treated as "continue".
                    String level = "Enum:" + rule.getEnumValue();
                    return new EvalResult(true, false, level, rule.getSeverity(),
                            rule.getMessage(), rule.getTimeDelay(), null);
                } else {
                    // Matched but severity empty -> explicitly normal value
                    return new EvalResult(false, false, null, null, null,
                            rule.getTimeDelay(), null);
                }
            }
        }
        // No matching enum value: skip evaluation, do NOT treat as normal.
        // Writing a normal state here would wrongly recover an active alarm,
        // so only the latest real-time value should be refreshed by the caller.
        return new EvalResult(false, true, null, null, null, null, null);
    }

    private static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }
}
