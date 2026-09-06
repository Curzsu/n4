package com.n4monitor.flink;

import com.fasterxml.jackson.databind.JsonNode;
import com.n4monitor.entity.AlarmRuleEnumEntity;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Every processed point frame (alarm or not) must be published to the
 * full-detail persistence channel, so the backend can write each frame to
 * TDengine {@code alarm_event}. The one exception is a null path, which is
 * rejected before any processing happens.
 */
class PersistenceChannelTest {

    private static final String PATH = "/sites/A1/fcu1/temp1";
    private static final long TS = 1_700_000_012_345L;

    private final AlarmHarness h = new AlarmHarness();

    @Test
    void normalNumericFrameIsPersisted() throws Exception {
        h.seedMeta(PATH, "numeric", "RULE1");
        h.seedNumericRule("RULE1", h.numericRule("1", 0));
        h.process(h.newFunction(), h.point(PATH, "50", TS));
        assertThat(h.consoleOutput()).contains("normal");
        assertThat(h.publishedOn(AlarmHarness.PERSISTENCE_STREAM)).hasSize(1);
    }

    @Test
    void alarmFrameIsPersisted() throws Exception {
        h.seedMeta(PATH, "numeric", "RULE1");
        h.seedNumericRule("RULE1", h.numericRule("1", 0));
        h.process(h.newFunction(), h.point(PATH, "95", TS));
        assertThat(h.consoleOutput()).contains("level=High");
        assertThat(h.publishedOn(AlarmHarness.PERSISTENCE_STREAM)).hasSize(1);
    }

    @Test
    void stringFrameIsPersisted() throws Exception {
        h.seedMeta(PATH, "string", "RULE1");
        h.process(h.newFunction(), h.point(PATH, "hello", TS));
        assertThat(h.consoleOutput()).contains("string type, skipped");
        assertThat(h.publishedOn(AlarmHarness.PERSISTENCE_STREAM)).hasSize(1);
    }

    @Test
    void noRuleFrameIsPersisted() throws Exception {
        h.seedMeta(PATH, "numeric", "");
        h.process(h.newFunction(), h.point(PATH, "95", TS));
        assertThat(h.consoleOutput()).contains("no alarm_code");
        assertThat(h.publishedOn(AlarmHarness.PERSISTENCE_STREAM)).hasSize(1);
    }

    @Test
    void unknownEnumFrameIsPersisted() throws Exception {
        AlarmRuleEnumEntity rule = new AlarmRuleEnumEntity();
        rule.setAlarmName("RULE1");
        rule.setEnumValue("0");
        rule.setSeverity("Major");
        rule.setMessage("Fault");
        h.seedMeta(PATH, "enum", "RULE1");
        h.seedEnumRules("RULE1", java.util.List.of(rule));
        h.process(h.newFunction(), h.point(PATH, "false", TS));
        assertThat(h.consoleOutput()).contains("unknown enum value, skipped");
        assertThat(h.publishedOn(AlarmHarness.PERSISTENCE_STREAM)).hasSize(1);
    }

    @Test
    void nullPathIsNotPersisted() throws Exception {
        h.process(h.newFunction(), h.point(null, "42", TS));
        assertThat(h.consoleOutput()).contains("[SKIP] path is null");
        assertThat(h.publishedOn(AlarmHarness.PERSISTENCE_STREAM)).isEmpty();
    }

    @Test
    void persistedFrameCarriesItsOwnTimestamp() throws Exception {
        h.seedMeta(PATH, "numeric", "RULE1");
        h.seedNumericRule("RULE1", h.numericRule("1", 0));
        h.process(h.newFunction(), h.point(PATH, "50", TS));
        JsonNode frame = h.publishedOn(AlarmHarness.PERSISTENCE_STREAM).get(0);
        assertThat(frame.get("timestamp").asLong()).isEqualTo(TS);
        assertThat(frame.get("mqttPath").asText()).isEqualTo(PATH);
        assertThat(frame.get("value").asText()).isEqualTo("50");
    }

    /**
     * Frames inside the delay-confirmation window (fresh entry, same-level
     * refresh, level-change reset) must keep the matched rule's timeDelay
     * and deadband, while still showing no phantom alarm level.
     */
    @Test
    void pendingFramesKeepRuleMetadata() throws Exception {
        h.seedMeta(PATH, "numeric", "RULE1");
        h.seedNumericRule("RULE1", h.numericRule("2", 30));
        AlarmProcessFunction fn = h.newFunction();
        // 1st frame (High) enters the pending window; 2nd frame refreshes the
        // same level; 3rd frame (High-High) resets the delay at the new level.
        h.process(fn, h.point(PATH, "85", TS));
        h.process(fn, h.point(PATH, "85", TS + 1000));
        h.process(fn, h.point(PATH, "95", TS + 2000));
        assertThat(h.consoleOutput()).contains("pending");
        List<JsonNode> frames = h.publishedOn(AlarmHarness.PERSISTENCE_STREAM);
        assertThat(frames).hasSize(3);
        for (JsonNode frame : frames) {
            assertThat(frame.get("timeDelay").asInt()).isEqualTo(30);
            assertThat(frame.get("deadband").asText()).isEqualTo("2");
            assertThat(frame.hasNonNull("level")).isFalse();
        }
    }
}
