package com.n4monitor.entity;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * Alarm rule entity for numeric points, mapped to the alarm_rule_numeric table.
 */
@Data
public class AlarmRuleNumericEntity {

    private Long id;

    /** Rule name: pointName_XX (sequence starting from 00) */
    private String alarmName;

    /** Point name (passed in by the frontend on create only, not mapped to a table column) */
    private String pointName;

    /** Point type: NUMERIC */
    private String pointType;

    /** Alarm trigger delay in seconds, one value per point */
    private Integer timeDelay;

    /** Deadband value (integer or float, stored as varchar), one value per point */
    private String deadband;

    // ── High-High ──
    private String highHighValue;
    private String highHighSeverity;
    private String highHighMessage;

    // ── High ──
    private String highValue;
    private String highSeverity;
    private String highMessage;

    // ── Low ──
    private String lowValue;
    private String lowSeverity;
    private String lowMessage;

    // ── Low-Low ──
    private String lowLowValue;
    private String lowLowSeverity;
    private String lowLowMessage;

    private LocalDateTime createTime;
    private LocalDateTime updateTime;
    private Integer isDeleted;
}
