package com.n4monitor.alarm.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Single alarm threshold rule.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AlarmRule {

    /** Threshold level: Boolean, High, High-High, Low, Low-Low */
    private String level;

    /** Threshold value */
    private String value;

    /** Alarm severity: Critical, Major, Minor, Warning */
    private String severity;

    /** Alarm prompt message */
    private String message;

    /** Alarm trigger delay in seconds (per-point) */
    private Integer timeDelay;

    /** Deadband for numeric thresholds (per-point, null for Boolean rules) */
    private String deadband;

    /** Point value type: BOOL or NUMERIC */
    private String pointType;
}
