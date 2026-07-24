package com.n4monitor.entity;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * Enum 类型点位报警规则实体，对应 alarm_rule_enum 表。
 * 每个枚举值一行，同一点位的多个枚举值共享同一 alarm_name。
 */
@Data
public class AlarmRuleEnumEntity {

    private Long id;

    /** 规则名称：点位名_序号(从00开始)，同一点位的多个枚举值共享 */
    private String alarmName;

    /** 点位名称（仅用于新增时由前端传入，不映射到数据库列） */
    private String pointName;

    /** 点位类型：ENUM */
    private String pointType;

    /** 报警触发延时（秒），同一点位所有枚举行共享 */
    private Integer timeDelay;

    /** 枚举值（字符串，如 "0"/"1"/"true"/"false"/"Open"/"Closed"） */
    private String enumValue;

    /** 枚举值含义（如 关机、开机、故障） */
    private String enumMeaning;

    /** 报警级别：Critical/Major/Minor/Warning/Normal（Normal 不触发报警） */
    private String severity;

    /** 报警提示信息 */
    private String message;

    private LocalDateTime createTime;
    private LocalDateTime updateTime;
    private Integer isDeleted;
}
