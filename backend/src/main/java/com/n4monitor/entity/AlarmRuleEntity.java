package com.n4monitor.entity;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * Point alarm rule entity, corresponding to the MySQL alarm_rule table.
 * Each row stores an alarm rule for a point (wide table, with fields of each alarm level listed side by side).
 */
@Data
public class AlarmRuleEntity {

    private Long id;

    /** Rule Name: Point Name_Sequence Number (starting from 00) */
    private String alarmName;

    /**
     * Point Name (only used for new entries, passed from the frontend, backend generates alarm_name based on this).
     * This field is not mapped to a database column, MyBatis will not populate this field during queries.
     */
    private String pointName;

    /** Point Type: BOOL / NUMERIC */
    private String pointType;

    // ── Boolean Rule ──
    private String booleanValue;
    private String booleanSeverity;
    private String booleanMessage;

    // ── High Rule ──
    private String highValue;
    private String highSeverity;
    private String highMessage;
    private String highHysteresis;

    // ── High-High Rule ──
    private String highHighValue;
    private String highHighSeverity;
    private String highHighMessage;
    private String highHighHysteresis;

    // ── Low Rule ──
    private String lowValue;
    private String lowSeverity;
    private String lowMessage;
    private String lowHysteresis;

    // ── Low-Low Rule ──
    private String lowLowValue;
    private String lowLowSeverity;
    private String lowLowMessage;
    private String lowLowHysteresis;

    private LocalDateTime createTime;
    private LocalDateTime updateTime;
    private Integer isDeleted;
}
