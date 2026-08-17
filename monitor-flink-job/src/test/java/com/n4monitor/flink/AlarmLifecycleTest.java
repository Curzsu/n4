package com.n4monitor.flink;

import com.n4monitor.entity.AlarmRuleNumericEntity;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Characterization tests for the alarm lifecycle state machine (start /
 * continue / escalate / recover, ack semantics) and the numeric deadband
 * (hysteresis) gate on recovery. Time base: T0 + offsets in milliseconds.
 */
class AlarmLifecycleTest {

    private static final String PATH = "/sites/A1/fcu1/temp1";
    private static final long T0 = 1_700_000_000_000L;

    private final AlarmHarness h = new AlarmHarness();

    private void seed(AlarmRuleNumericEntity rule) throws Exception {
        h.seedMeta(PATH, "numeric", "RULE1");
        h.seedNumericRule("RULE1", rule);
    }

    @Test
    void freshAlarmStartsWithoutDelayWritesStartEverywhere() throws Exception {
        seed(h.numericRule(null, 0));
        h.process(h.newFunction(), h.point(PATH, "85", T0));

        var active = h.activeOf(PATH);
        assertThat(active.getStage()).isEqualTo("start");
        assertThat(active.getStageMessage()).isEqualTo("Start");
        assertThat(active.getStatus()).isEqualTo("Unack");
        assertThat(active.getAckBy()).isEqualTo("-");
        assertThat(active.getDuration()).isEqualTo("0");
        assertThat(active.getAlarmStartTime()).isEqualTo(T0);
        // Active record's serialized timestamp is forced to the alarm start time.
        assertThat(active.getTimestamp()).isEqualTo(T0);

        var notify = h.publishedOn(AlarmHarness.NOTIFY_CHANNEL);
        assertThat(notify).hasSize(1);
        assertThat(notify.get(0).path("stage").asText()).isEqualTo("start");
        assertThat(notify.get(0).path("timestamp").asLong()).isEqualTo(T0);

        var realtime = h.publishedOn(AlarmHarness.REALTIME_CHANNEL);
        assertThat(realtime).hasSize(1);
        assertThat(realtime.get(0).path("timestamp").asLong()).isEqualTo(T0);

        var latest = h.latestOf(PATH);
        assertThat(latest.getStage()).isEqualTo("start");
        assertThat(latest.getTimestamp()).isEqualTo(T0);
    }

    @Test
    void sameLevelContinuesSilentlyAndPreservesAckAndStart() throws Exception {
        seed(h.numericRule(null, 0));
        h.seedActive(h.activeState(PATH, "High", "Major", "start", "Ack", T0));
        h.process(h.newFunction(), h.point(PATH, "85", T0 + 7_000));

        var active = h.activeOf(PATH);
        assertThat(active.getStage()).isEqualTo("continue");
        assertThat(active.getStageMessage()).isEqualTo("Continue");
        assertThat(active.getDuration()).isEqualTo("7");
        assertThat(active.getStatus()).isEqualTo("Ack");
        assertThat(active.getAckBy()).isEqualTo("op");
        assertThat(active.getAlarmStartTime()).isEqualTo(T0);
        // Continue publishes nothing - no notify, no realtime record.
        assertThat(h.publishedOn(AlarmHarness.NOTIFY_CHANNEL)).isEmpty();
        assertThat(h.publishedOn(AlarmHarness.REALTIME_CHANNEL)).isEmpty();
    }

    @Test
    void levelEscalationClosesOldPhaseAndStartsNewOne() throws Exception {
        seed(h.numericRule(null, 0));
        h.seedActive(h.activeState(PATH, "High", "Major", "start", "Unack", T0));
        h.process(h.newFunction(), h.point(PATH, "95", T0 + 3_000));

        // Realtime gets the End record first, then the new Start.
        var realtime = h.publishedOn(AlarmHarness.REALTIME_CHANNEL);
        assertThat(realtime).hasSize(2);
        assertThat(realtime.get(0).path("stage").asText()).isEqualTo("end");
        assertThat(realtime.get(0).path("stageMessage").asText()).isEqualTo("End - High to High-High");
        assertThat(realtime.get(0).path("level").asText()).isEqualTo("High");
        assertThat(realtime.get(0).path("duration").asText()).isEqualTo("3");
        assertThat(realtime.get(0).path("timestamp").asLong()).isEqualTo(T0 + 3_000);
        assertThat(realtime.get(1).path("stage").asText()).isEqualTo("start");
        assertThat(realtime.get(1).path("stageMessage").asText()).isEqualTo("Start - High to High-High");
        assertThat(realtime.get(1).path("level").asText()).isEqualTo("High-High");

        // One notification: the new phase's start.
        var notify = h.publishedOn(AlarmHarness.NOTIFY_CHANNEL);
        assertThat(notify).hasSize(1);
        assertThat(notify.get(0).path("level").asText()).isEqualTo("High-High");
        assertThat(notify.get(0).path("timestamp").asLong()).isEqualTo(T0 + 3_000);

        var active = h.activeOf(PATH);
        assertThat(active.getLevel()).isEqualTo("High-High");
        assertThat(active.getStage()).isEqualTo("start");
        assertThat(active.getDuration()).isEqualTo("0");
        assertThat(active.getStatus()).isEqualTo("Unack");
        assertThat(active.getAlarmStartTime()).isEqualTo(T0 + 3_000);
    }

    @Test
    void recoveryWhileUnackedFreezesRecordInActive() throws Exception {
        seed(h.numericRule(null, 0));
        h.seedActive(h.activeState(PATH, "High", "Major", "start", "Unack", T0));
        h.process(h.newFunction(), h.point(PATH, "50", T0 + 9_000));

        var active = h.activeOf(PATH);
        assertThat(active).isNotNull();
        assertThat(active.getStage()).isEqualTo("end");
        assertThat(active.getStageMessage()).isEqualTo("End");
        assertThat(active.getDuration()).isEqualTo("9");
        // The frozen record keeps the historical start time.
        assertThat(active.getAlarmStartTime()).isEqualTo(T0);

        var notify = h.publishedOn(AlarmHarness.NOTIFY_CHANNEL);
        assertThat(notify).hasSize(1);
        assertThat(notify.get(0).path("stage").asText()).isEqualTo("end");
        assertThat(notify.get(0).path("timestamp").asLong()).isEqualTo(T0);

        var realtime = h.publishedOn(AlarmHarness.REALTIME_CHANNEL);
        assertThat(realtime).hasSize(1);
        assertThat(realtime.get(0).path("stage").asText()).isEqualTo("end");
        assertThat(realtime.get(0).path("timestamp").asLong()).isEqualTo(T0 + 9_000);

        // Latest carries the recovered (normal) reading.
        assertThat(h.latestOf(PATH).getLevel()).isNull();
    }

    @Test
    void recoveryWhileAckedRemovesActiveAndBroadcastsEnd() throws Exception {
        seed(h.numericRule(null, 0));
        h.seedActive(h.activeState(PATH, "High", "Major", "start", "Ack", T0));
        h.process(h.newFunction(), h.point(PATH, "50", T0 + 9_000));

        assertThat(h.activeOf(PATH)).isNull();
        var notify = h.publishedOn(AlarmHarness.NOTIFY_CHANNEL);
        assertThat(notify).hasSize(1);
        assertThat(notify.get(0).path("stage").asText()).isEqualTo("end");
        var realtime = h.publishedOn(AlarmHarness.REALTIME_CHANNEL);
        assertThat(realtime).hasSize(1);
        assertThat(realtime.get(0).path("stage").asText()).isEqualTo("end");
    }

    @Test
    void reAlarmAfterRecoveryStartsANewCycle() throws Exception {
        seed(h.numericRule(null, 0));
        h.seedActive(h.activeState(PATH, "High", "Major", "end", "Unack", T0));
        h.process(h.newFunction(), h.point(PATH, "85", T0 + 5_000));

        var active = h.activeOf(PATH);
        assertThat(active.getStage()).isEqualTo("start");
        assertThat(active.getDuration()).isEqualTo("0");
        assertThat(active.getAlarmStartTime()).isEqualTo(T0 + 5_000);
    }

    @Test
    void normalAfterRecoveredEndIsIdempotent() throws Exception {
        seed(h.numericRule(null, 0));
        h.seedActive(h.activeState(PATH, "High", "Major", "end", "Unack", T0));
        h.process(h.newFunction(), h.point(PATH, "50", T0 + 5_000));

        // Frozen record stays untouched, no publishes.
        assertThat(h.activeOf(PATH).getStage()).isEqualTo("end");
        assertThat(h.publishedOn(AlarmHarness.NOTIFY_CHANNEL)).isEmpty();
        assertThat(h.publishedOn(AlarmHarness.REALTIME_CHANNEL)).isEmpty();
    }

    // ── Deadband (hysteresis) gate ───────────────────────────────────────────

    @Test
    void highAlarmPersistsInsideDeadbandBand() throws Exception {
        // High=80, deadband=5: recovery boundary is 75.
        seed(h.numericRule("5", 0));
        h.seedActive(h.activeState(PATH, "High", "Major", "start", "Unack", T0));
        h.process(h.newFunction(), h.point(PATH, "77", T0 + 1_000));

        var active = h.activeOf(PATH);
        assertThat(active.getStage()).isEqualTo("continue");
        assertThat(active.getLevel()).isEqualTo("High");
        assertThat(active.getSeverity()).isEqualTo("Major");
        assertThat(active.getMessage()).isEqualTo("msg-High");
        assertThat(h.consoleOutput()).contains("level=High");
    }

    @Test
    void highAlarmRecoversOnlyPastDeadbandBoundary() throws Exception {
        seed(h.numericRule("5", 0));
        h.seedActive(h.activeState(PATH, "High", "Major", "start", "Unack", T0));
        h.process(h.newFunction(), h.point(PATH, "74", T0 + 1_000));

        assertThat(h.activeOf(PATH).getStage()).isEqualTo("end");
    }

    @Test
    void lowAlarmPersistsInsideDeadbandBand() throws Exception {
        // Low=20, deadband=5: recovery boundary is 25.
        seed(h.numericRule("5", 0));
        h.seedActive(h.activeState(PATH, "Low", "Minor", "start", "Unack", T0));
        h.process(h.newFunction(), h.point(PATH, "23", T0 + 1_000));
        assertThat(h.activeOf(PATH).getStage()).isEqualTo("continue");

        AlarmHarness h2 = new AlarmHarness();
        h2.seedMeta(PATH, "numeric", "RULE1");
        h2.seedNumericRule("RULE1", h2.numericRule("5", 0));
        h2.seedActive(h2.activeState(PATH, "Low", "Minor", "start", "Unack", T0));
        h2.process(h2.newFunction(), h2.point(PATH, "26", T0 + 1_000));
        assertThat(h2.activeOf(PATH).getStage()).isEqualTo("end");
    }

    @Test
    void zeroOrMalformedDeadbandRecoversAtRawThreshold() throws Exception {
        seed(h.numericRule("0", 0));
        h.seedActive(h.activeState(PATH, "High", "Major", "start", "Unack", T0));
        h.process(h.newFunction(), h.point(PATH, "79", T0 + 1_000));
        assertThat(h.activeOf(PATH).getStage()).isEqualTo("end");

        AlarmHarness h2 = new AlarmHarness();
        h2.seedMeta(PATH, "numeric", "RULE1");
        h2.seedNumericRule("RULE1", h2.numericRule("abc", 0));
        h2.seedActive(h2.activeState(PATH, "High", "Major", "start", "Unack", T0));
        h2.process(h2.newFunction(), h2.point(PATH, "79", T0 + 1_000));
        assertThat(h2.activeOf(PATH).getStage()).isEqualTo("end");
    }
}
