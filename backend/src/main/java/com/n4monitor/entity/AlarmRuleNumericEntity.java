package com.n4monitor.entity;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * Numeric 类型点位报警规则实体，对应 alarm_rule_numeric 表。
 */
@Data
public class AlarmRuleNumericEntity {

    private Long id;

    /** 规则名称：点位名_序号(从00开始) */
    private String alarmName;

    /** 点位名称（仅用于新增时由前端传入，不映射到数据库列） */
    private String pointName;

    /** 点位类型：NUMERIC */
    private String pointType;

    /** 报警触发延时（秒），每个点位一个值 */
    private Integer timeDelay;

    /** 死区值（整数或浮点数，存为 varchar），每个点位一个值 */
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
