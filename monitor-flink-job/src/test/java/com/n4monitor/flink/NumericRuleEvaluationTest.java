package com.n4monitor.flink;

import com.n4monitor.entity.AlarmRuleNumericEntity;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Characterization tests for numeric rule evaluation: threshold regions,
 * priority order, boundary equality, malformed thresholds (currently skipped
 * silently) and the normal path (which must still carry timeDelay/deadband).
 */
class NumericRuleEvaluationTest {

    private static final String PATH = "/sites/A1/fcu1/temp1";
    private static final long TS = 1_700_000_012_345L;

    private final AlarmHarness h = new AlarmHarness();

    private void seed(AlarmRuleCustomizer customizer) throws Exception {
        h.seedMeta(PATH, "numeric", "RULE1");
        AlarmRuleNumericEntity rule = h.numericRule("5", 0);
        if (customizer != null) customizer.apply(rule);
        h.seedNumericRule("RULE1", rule);
    }

    interface AlarmRuleCustomizer {
        void apply(AlarmRuleNumericEntity rule);
    }

    @Test
    void valueAboveHighHighTripsHighestLevel() throws Exception {
        seed(null);
        h.process(h.newFunction(), h.point(PATH, "95", TS));
        assertThat(h.consoleOutput()).contains("level=High-High").contains("Critical").contains("value=95");
        var active = h.activeOf(PATH);
        assertThat(active.getLevel()).isEqualTo("High-High");
        assertThat(active.getSeverity()).isEqualTo("Critical");
        assertThat(active.getMessage()).isEqualTo("HH exceeded");
    }

    @Test
    void valueBetweenHighAndHighHighTripsHigh() throws Exception {
        seed(null);
        h.process(h.newFunction(), h.point(PATH, "85", TS));
        assertThat(h.activeOf(PATH).getLevel()).isEqualTo("High");
    }

    @Test
    void valueBelowLowLowTripsLowLowBeforeLow() throws Exception {
        seed(null);
        h.process(h.newFunction(), h.point(PATH, "5", TS));
        // 5 is below both Low(20) and Low-Low(10); Low-Low has priority.
        assertThat(h.activeOf(PATH).getLevel()).isEqualTo("Low-Low");
    }

    @Test
    void valueBetweenLowAndLowLowTripsLow() throws Exception {
        seed(null);
        h.process(h.newFunction(), h.point(PATH, "15", TS));
        assertThat(h.activeOf(PATH).getLevel()).isEqualTo("Low");
    }

    @Test
    void boundaryEqualityTriggersAlarm() throws Exception {
        seed(null);
        h.process(h.newFunction(), h.point(PATH, "90", TS));
        assertThat(h.activeOf(PATH).getLevel()).isEqualTo("High-High");

        AlarmHarness h2 = new AlarmHarness();
        h2.seedMeta(PATH, "numeric", "RULE1");
        h2.seedNumericRule("RULE1", h2.numericRule("5", 0));
        h2.process(h2.newFunction(), h2.point(PATH, "20", TS));
        assertThat(h2.activeOf(PATH).getLevel()).isEqualTo("Low");
    }

    @Test
    void normalValueWritesNoActiveButCarriesRuleMetadataToLatest() throws Exception {
        seed(null);
        h.process(h.newFunction(), h.point(PATH, "50", TS));
        assertThat(h.consoleOutput()).contains("normal");
        assertThat(h.activeOf(PATH)).isNull();
        var latest = h.latestOf(PATH);
        assertThat(latest.getLevel()).isNull();
        // Even a normal result must carry deadband/timeDelay for the hysteresis gate.
        assertThat(latest.getDeadband()).isEqualTo("5");
        assertThat(latest.getTimeDelay()).isEqualTo(0);
    }

    @Test
    void nonNumericValueIsTreatedAsNormal() throws Exception {
        seed(null);
        h.process(h.newFunction(), h.point(PATH, "N/A", TS));
        assertThat(h.consoleOutput()).contains("normal");
        assertThat(h.activeOf(PATH)).isNull();
    }

    @Test
    void malformedHighHighThresholdIsSkippedSilentlyAndNextLevelWins() throws Exception {
        seed(rule -> rule.setHighHighValue("abc"));
        h.process(h.newFunction(), h.point(PATH, "95", TS));
        // The unparseable High-High threshold no longer fails silently (a warn
        // is logged since the refactor) but evaluation still falls through to
        // the High level, exactly as before.
        assertThat(h.activeOf(PATH).getLevel()).isEqualTo("High");
    }

    @Test
    void malformedThresholdAsOnlyRuleNeverAlarms() throws Exception {
        seed(rule -> {
            rule.setHighHighValue("abc");
            rule.setHighValue(null);
            rule.setLowValue(null);
            rule.setLowLowValue(null);
        });
        h.process(h.newFunction(), h.point(PATH, "95", TS));
        assertThat(h.consoleOutput()).contains("normal");
        assertThat(h.activeOf(PATH)).isNull();
    }

    @Test
    void missingRuleInRedisReportsNoRule() throws Exception {
        h.seedMeta(PATH, "numeric", "RULE_MISSING");
        h.process(h.newFunction(), h.point(PATH, "95", TS));
        assertThat(h.consoleOutput()).contains("no rule found");
        assertThat(h.activeOf(PATH)).isNull();
        // Latest is still refreshed so the live display stays current.
        assertThat(h.latestOf(PATH)).isNotNull();
    }

    @Test
    void activeRecordCarriesParsedPathFields() throws Exception {
        seed(null);
        h.process(h.newFunction(), h.point(PATH, "85", TS));
        var active = h.activeOf(PATH);
        assertThat(active.getLocation()).isEqualTo("sites");
        assertThat(active.getSource()).isEqualTo("fcu1/temp1");
        assertThat(h.consoleOutput()).contains("temp1");
    }
}
