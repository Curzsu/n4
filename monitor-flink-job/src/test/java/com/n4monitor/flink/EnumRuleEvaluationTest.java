package com.n4monitor.flink;

import com.n4monitor.entity.AlarmRuleEnumEntity;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Characterization tests for enum/boolean rule evaluation: value matching,
 * explicit normal values, unknown values (must NOT touch alarm:active), and
 * the level namespacing that keeps different enum values distinguishable.
 */
class EnumRuleEvaluationTest {

    private static final String PATH = "/sites/A1/fcu1/pump1";
    private static final long TS = 1_700_000_012_345L;

    private final AlarmHarness h = new AlarmHarness();

    private AlarmRuleEnumEntity enumRule(String value, String severity, String message, Integer delay) {
        AlarmRuleEnumEntity rule = new AlarmRuleEnumEntity();
        rule.setAlarmName("RULE1");
        rule.setEnumValue(value);
        rule.setSeverity(severity);
        rule.setMessage(message);
        rule.setTimeDelay(delay);
        return rule;
    }

    private void seedRules(AlarmRuleEnumEntity... rules) throws Exception {
        h.seedMeta(PATH, "enum", "RULE1");
        h.seedEnumRules("RULE1", List.of(rules));
    }

    @Test
    void matchingValueWithSeverityAlarmsWithEnumPrefixedLevel() throws Exception {
        seedRules(enumRule("0", "Major", "Fault", 0), enumRule("1", "", "Running", 0));
        h.process(h.newFunction(), h.point(PATH, "0", TS));
        var active = h.activeOf(PATH);
        assertThat(active.getLevel()).isEqualTo("Enum:0");
        assertThat(active.getSeverity()).isEqualTo("Major");
        assertThat(active.getMessage()).isEqualTo("Fault");
        assertThat(active.getStage()).isEqualTo("start");
    }

    @Test
    void matchingValueWithBlankSeverityIsExplicitNormal() throws Exception {
        seedRules(enumRule("1", "", "Running", 0));
        h.process(h.newFunction(), h.point(PATH, "1", TS));
        assertThat(h.consoleOutput()).contains("normal");
        assertThat(h.activeOf(PATH)).isNull();
    }

    @Test
    void unknownValueIsSkippedAndLeavesActiveUntouched() throws Exception {
        seedRules(enumRule("0", "Major", "Fault", 0));
        // Seed an ongoing alarm: an unknown value must NOT recover it.
        h.seedActive(h.activeState(PATH, "Enum:0", "Major", "start", "Unack", TS));
        h.process(h.newFunction(), h.point(PATH, "9", TS + 1000));
        assertThat(h.consoleOutput()).contains("unknown enum value, skipped");
        var active = h.activeOf(PATH);
        assertThat(active).isNotNull();
        assertThat(active.getStage()).isEqualTo("start");
        // Latest is still refreshed with the live reading (level-less).
        var latest = h.latestOf(PATH);
        assertThat(latest.getValue()).isEqualTo("9");
        assertThat(latest.getLevel()).isNull();
    }

    @Test
    void enumMatchIsCaseInsensitive() throws Exception {
        seedRules(enumRule("RUN", "Minor", "Running", 0));
        h.process(h.newFunction(), h.point(PATH, "run", TS));
        assertThat(h.activeOf(PATH).getLevel()).isEqualTo("Enum:RUN");
    }

    @Test
    void booleanTypeRoutesToEnumEvaluation() throws Exception {
        h.seedMeta(PATH, "boolean", "RULE1");
        h.seedEnumRules("RULE1", List.of(enumRule("true", "Critical", "Trip", 0)));
        h.process(h.newFunction(), h.point(PATH, "true", TS));
        assertThat(h.activeOf(PATH).getLevel()).isEqualTo("Enum:true");
        assertThat(h.activeOf(PATH).getSeverity()).isEqualTo("Critical");
    }

    @Test
    void missingEnumRulesInRedisReportsNoRule() throws Exception {
        h.seedMeta(PATH, "enum", "RULE_MISSING");
        h.process(h.newFunction(), h.point(PATH, "0", TS));
        assertThat(h.consoleOutput()).contains("no rule found");
    }
}
