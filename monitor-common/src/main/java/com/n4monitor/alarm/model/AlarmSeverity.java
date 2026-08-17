package com.n4monitor.alarm.model;

import java.util.List;

/**
 * Severity vocabulary used across the alarm pipeline (rules, Redis records,
 * charts, frontend). Wire values are plain strings - severity travels as a
 * string field inside PointAlarmState JSON - so these constants single-source
 * the spelling instead of typing the field.
 */
public final class AlarmSeverity {

    public static final String CRITICAL = "Critical";
    public static final String MAJOR = "Major";
    public static final String MINOR = "Minor";
    public static final String WARNING = "Warning";
    /** Non-alarm bucket: aggregation totals count severity-less records as Normal. */
    public static final String NORMAL = "Normal";

    /** Display/evaluation order used by charts and evaluation priority lists. */
    public static final List<String> ORDERED = List.of(CRITICAL, MAJOR, MINOR, WARNING);

    private AlarmSeverity() {
    }
}
