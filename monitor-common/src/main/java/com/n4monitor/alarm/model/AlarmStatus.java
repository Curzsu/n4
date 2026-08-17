package com.n4monitor.alarm.model;

/**
 * Acknowledgement status values stored in PointAlarmState.status.
 */
public final class AlarmStatus {

    public static final String ACK = "Ack";
    public static final String UNACK = "Unack";
    public static final String CLEARED = "Cleared";

    private AlarmStatus() {
    }
}
