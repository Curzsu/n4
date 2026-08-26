package com.n4monitor.flink;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Characterization tests for the delay-confirmation (pending) state machine:
 * entering the delay window, same-level refresh, level-change reset,
 * cancellation on normal value, and the onTimer confirmation itself.
 */
class PendingAlarmDelayTest {

    private static final String PATH = "/sites/A1/fcu1/temp1";
    /** Aligned with AlarmHarness.FakeTimerService's default processing time. */
    private static final long NOW = 1_700_000_000_000L;
    private static final long E1 = NOW + 1_000;
    private static final long DELAY_MS = 10_000;

    private final AlarmHarness h = new AlarmHarness();

    private AlarmProcessFunction seededFunction(Integer delaySeconds) throws Exception {
        h.seedMeta(PATH, "numeric", "RULE1");
        h.seedNumericRule("RULE1", h.numericRule(null, delaySeconds));
        return h.newFunction();
    }

    @Test
    void firstAlarmTickWithDelayEntersPendingInsteadOfFiring() throws Exception {
        AlarmProcessFunction fn = seededFunction(10);
        h.process(fn, h.point(PATH, "85", E1));

        long target = E1 + DELAY_MS;
        assertThat(h.timers.registered).containsExactly(target);
        // Nothing in alarm:active and nothing published while pending.
        assertThat(h.activeOf(PATH)).isNull();
        assertThat(h.publishedOn(AlarmHarness.NOTIFY_CHANNEL)).isEmpty();
        assertThat(h.publishedOn(AlarmHarness.REALTIME_CHANNEL)).isEmpty();
        // Pending state captured the would-be alarm.
        assertThat(h.pendingState.value()).isNotNull();
        assertThat(h.pendingState.value().state.getLevel()).isEqualTo("High");
        // Latest shows a NORMAL (level-less) reading during the delay window.
        assertThat(h.latestOf(PATH).getLevel()).isNull();
        assertThat(h.latestOf(PATH).getValue()).isEqualTo("85");
        assertThat(h.consoleOutput()).contains("pending High");
    }

    @Test
    void lateEventTimeIsFlooredToCurrentProcessingTime() throws Exception {
        AlarmProcessFunction fn = seededFunction(10);
        // Event timestamp is 60s in the past: target would be in the past,
        // so the timer is floored to "now".
        h.process(fn, h.point(PATH, "85", NOW - 60_000));
        assertThat(h.timers.registered).containsExactly(NOW);
    }

    @Test
    void sameLevelTickRefreshesPendingWithoutReRegistering() throws Exception {
        AlarmProcessFunction fn = seededFunction(10);
        h.process(fn, h.point(PATH, "85", E1));
        h.process(fn, h.point(PATH, "86", E1 + 2_000));

        assertThat(h.timers.registered).containsExactly(E1 + DELAY_MS);
        assertThat(h.timers.deleted).isEmpty();
        assertThat(h.pendingState.value().state.getValue()).isEqualTo("86");
        assertThat(h.pendingState.value().state.getTimestamp()).isEqualTo(E1 + 2_000);
        assertThat(h.activeOf(PATH)).isNull();
        assertThat(h.latestOf(PATH).getLevel()).isNull();
        assertThat(h.consoleOutput()).contains("pending High, 2s elapsed");
    }

    @Test
    void differentLevelResetsTheDelayWindow() throws Exception {
        AlarmProcessFunction fn = seededFunction(10);
        h.process(fn, h.point(PATH, "85", E1));
        long secondTick = E1 + 3_000;
        h.process(fn, h.point(PATH, "95", secondTick));

        assertThat(h.timers.deleted).containsExactly(E1 + DELAY_MS);
        assertThat(h.timers.registered).containsExactly(E1 + DELAY_MS, secondTick + DELAY_MS);
        assertThat(h.pendingState.value().state.getLevel()).isEqualTo("High-High");
        assertThat(h.activeOf(PATH)).isNull();
        assertThat(h.consoleOutput()).contains("reset from High");
    }

    @Test
    void normalValueDuringDelayCancelsPending() throws Exception {
        AlarmProcessFunction fn = seededFunction(10);
        h.process(fn, h.point(PATH, "85", E1));
        h.process(fn, h.point(PATH, "50", E1 + 4_000));

        assertThat(h.timers.deleted).containsExactly(E1 + DELAY_MS);
        assertThat(h.pendingState.value()).isNull();
        assertThat(h.activeOf(PATH)).isNull();
        assertThat(h.publishedOn(AlarmHarness.NOTIFY_CHANNEL)).isEmpty();
        var latest = h.latestOf(PATH);
        assertThat(latest.getValue()).isEqualTo("50");
        assertThat(latest.getLevel()).isNull();
        assertThat(h.consoleOutput()).contains("cancelled");
    }

    @Test
    void onTimerConfirmsAlarmWithConfirmationInstantAsStart() throws Exception {
        AlarmProcessFunction fn = seededFunction(10);
        h.process(fn, h.point(PATH, "85", E1));
        long target = E1 + DELAY_MS;

        h.sink.clear();
        fn.onTimer(target, h.mockTimerContext(), h.collector());

        var active = h.activeOf(PATH);
        assertThat(active).isNotNull();
        assertThat(active.getStage()).isEqualTo("start");
        assertThat(active.getStatus()).isEqualTo("Unack");
        assertThat(active.getDuration()).isEqualTo("0");
        // alarmStartTime is the confirmation instant (timer target), not E1.
        assertThat(active.getAlarmStartTime()).isEqualTo(target);
        // All writes share the confirmation timestamp.
        assertThat(active.getTimestamp()).isEqualTo(target);
        assertThat(h.latestOf(PATH).getTimestamp()).isEqualTo(target);
        assertThat(h.latestOf(PATH).getLevel()).isEqualTo("High");

        var notify = h.publishedOn(AlarmHarness.NOTIFY_CHANNEL);
        assertThat(notify).hasSize(1);
        assertThat(notify.get(0).path("stage").asText()).isEqualTo("start");
        assertThat(notify.get(0).path("timestamp").asLong()).isEqualTo(target);
        var realtime = h.publishedOn(AlarmHarness.REALTIME_CHANNEL);
        assertThat(realtime).hasSize(1);
        assertThat(realtime.get(0).path("timestamp").asLong()).isEqualTo(target);
        // The pending-entry frame plus this confirmed start frame both reach
        // the full-detail persistence channel (alarm_event).
        var persistence = h.publishedOn(AlarmHarness.PERSISTENCE_CHANNEL);
        assertThat(persistence).hasSize(2);
        assertThat(persistence.get(1).path("timestamp").asLong()).isEqualTo(target);
        assertThat(persistence.get(1).path("level").asText()).isEqualTo("High");
        assertThat(persistence.get(1).path("stage").asText()).isEqualTo("start");

        assertThat(h.pendingState.value()).isNull();
        assertThat(h.consoleOutput()).contains("delay confirmed, ALARM");
    }

    @Test
    void staleTimerWithMismatchedTargetIsIgnored() throws Exception {
        AlarmProcessFunction fn = seededFunction(10);
        h.process(fn, h.point(PATH, "85", E1));
        long target = E1 + DELAY_MS;

        h.sink.clear();
        fn.onTimer(target + 55_555, h.mockTimerContext(), h.collector());
        assertThat(h.activeOf(PATH)).isNull();
        assertThat(h.sink).isEmpty();
        // Pending is kept: the real timer may still fire.
        assertThat(h.pendingState.value()).isNotNull();
    }

    @Test
    void timerWithNoPendingStateIsANoOp() throws Exception {
        AlarmProcessFunction fn = seededFunction(10);
        fn.onTimer(NOW + 999, h.mockTimerContext(), h.collector());
        assertThat(h.activeOf(PATH)).isNull();
        assertThat(h.sink).isEmpty();
    }

    @Test
    void zeroDelayFiresImmediatelyWithoutPending() throws Exception {
        AlarmProcessFunction fn = seededFunction(0);
        h.process(fn, h.point(PATH, "85", E1));
        assertThat(h.timers.registered).isEmpty();
        assertThat(h.pendingState.value()).isNull();
        assertThat(h.activeOf(PATH).getStage()).isEqualTo("start");
    }
}
