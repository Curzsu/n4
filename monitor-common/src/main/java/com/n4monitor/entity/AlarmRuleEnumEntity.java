package com.n4monitor.entity;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * Alarm rule entity for enum points, mapped to the alarm_rule_enum table.
 * One row per enum value; rows of the same point share one alarm_name.
 */
@Data
public class AlarmRuleEnumEntity {

    private Long id;

    /** Rule name: pointName_XX, shared by all enum values of the same point */
    private String alarmName;

    /** Point name (passed in by the frontend on create only, not mapped to a table column) */
    private String pointName;

    /** Point type: ENUM */
    private String pointType;

    /** Alarm trigger delay in seconds, shared by all enum rows of the same point */
    private Integer timeDelay;

    /** Enum value (string, e.g. "0"/"1"/"true"/"false"/"Open"/"Closed") */
    private String enumValue;

    /** Meaning of the enum value (e.g. shutdown, running, fault) */
    private String enumMeaning;

    /** Alarm severity: Critical/Major/Minor/Warning/Normal (Normal does not trigger an alarm) */
    private String severity;

    /** Alarm prompt message */
    private String message;

    private LocalDateTime createTime;
    private LocalDateTime updateTime;
    private Integer isDeleted;
}
