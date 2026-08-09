package com.n4monitor.entity;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * Boolean 类型点位报警规则实体，对应 alarm_rule_boolean 表。
 */
@Data
public class AlarmRuleBooleanEntity {

    private Long id;

    /** 规则名称：点位名_序号(从00开始) */
    private String alarmName;

    /** 点位名称（仅用于新增时由前端传入，后端据此生成 alarm_name，不映射到数据库列） */
    private String pointName;

    /** 点位类型：BOOL */
    private String pointType;

    private String booleanValue;
    private String booleanSeverity;
    private String booleanMessage;

    private LocalDateTime createTime;
    private LocalDateTime updateTime;
    private Integer isDeleted;
}
