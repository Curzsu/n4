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

    /** Threshold level: Normal, High, High-High, Low, Low-Low, Offline */
    private String level;

    /** Threshold value (null for Normal, "Null" for Offline) */
    private String value;

    /** Alarm severity: Critical, Major, Minor, Warning */
    private String severity;

    /** Alarm prompt message */
    private String message;
}
