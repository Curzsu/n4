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

    // ── High-High ──
    private String highHighValue;
    private String highHighSeverity;
    private String highHighMessage;
    private String highHighHysteresis;

    // ── High ──
    private String highValue;
    private String highSeverity;
    private String highMessage;
    private String highHysteresis;

    // ── Low ──
    private String lowValue;
    private String lowSeverity;
    private String lowMessage;
    private String lowHysteresis;

    // ── Low-Low ──
    private String lowLowValue;
    private String lowLowSeverity;
    private String lowLowMessage;
    private String lowLowHysteresis;

    private LocalDateTime createTime;
    private LocalDateTime updateTime;
    private Integer isDeleted;
}
