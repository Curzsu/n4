package com.n4monitor.alarm.model;

/**
 * Alarm lifecycle stage values stored in PointAlarmState.stage and used by
 * the Flink state machine, the acknowledgement flow and the frontend.
 * Note: stageMessage is a free-text display label and is NOT part of this
 * closed vocabulary.
 */
public final class AlarmStage {

    public static final String START = "start";
    public static final String CONTINUE = "continue";
    /** Terminal stage: the alarm recovered (record is frozen or removed). */
    public static final String END = "end";

    private AlarmStage() {
    }
}
