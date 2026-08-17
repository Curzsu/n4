package com.n4monitor.flink;

import com.n4monitor.alarm.model.PointAlarmState;

import java.io.Serializable;

/**
 * In-flight (unconfirmed) alarm held in keyed state while a delay timer
 * runs. Carries the would-be alarm state so {@code AlarmProcessFunction#onTimer}
 * can fire it without the original PointData, plus timing metadata.
 */
public class PendingAlarm implements Serializable {
    /** Would-be alarm state captured at first threshold crossing; value/timestamp
     *  refreshed on each same-level tick during the delay window. Level/severity/
     *  message are kept so onTimer knows which alarm to raise, but they are NOT
     *  written to alarm:latest while pending (a separate normal-state write is
     *  used for latest during the delay window). */
    public PointAlarmState state;
    /** Event-time timestamp of the first threshold crossing (the data tick's
     *  own timestamp). The confirmation timer target is derived from this
     *  (firstEventTime + timeDelay), not from processing time, so the delay
     *  is measured in the data's own time domain as required. */
    public long firstEventTime;
    /** Target processing time of the confirmation timer (used to identify
     *  the timer when cancelling / firing). Computed as firstEventTime +
     *  timeDelay, floored to current processing time so a delayed message
     *  whose delay window already passed fires promptly rather than in the
     *  past. */
    public long timerTarget;

    public PendingAlarm() {}

    public PendingAlarm(PointAlarmState state, long firstEventTime, long timerTarget) {
        this.state = state;
        this.firstEventTime = firstEventTime;
        this.timerTarget = timerTarget;
    }
}
