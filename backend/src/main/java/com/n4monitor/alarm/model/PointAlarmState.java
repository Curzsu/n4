package com.n4monitor.alarm.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 点位最新报警状态 (存入 Redis: alarm:latest)
 * <p>
 * 包含 Kafka 传入的原始点位数据 + 报警评估结果 + 附加字段
 */
@Data
@EqualsAndHashCode(callSuper = true)
@JsonIgnoreProperties(ignoreUnknown = true)
public class PointAlarmState extends PointData {

    /** 阈值分级: Normal / High / High-High / Low / Low-Low / Offline */
    private String level;

    /** 报警等级: Critical / Major / Minor / Warning / Normal */
    private String severity;

    /** 报警信息 */
    private String message;

    /** 确认人 (默认 "-") */
    private String ackBy;

    /** 报警状态: Unack / Ack (默认 "Unack") */
    private String status;

    /** 源 Source (暂为空) */
    private String source;

    /** 位置 Location (暂为空) */
    private String location;
}
