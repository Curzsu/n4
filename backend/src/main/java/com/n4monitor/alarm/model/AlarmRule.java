package com.n4monitor.alarm.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 单条报警阈值规则
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AlarmRule {

    /** 阈值分级: Normal, High, High-High, Low, Low-Low, Offline */
    private String level;

    /** 阈值数值 (Normal为null, Offline为"Null") */
    private String value;

    /** 报警等级: Critical, Major, Minor, Warning */
    private String severity;

    /** 报警提示内容 */
    private String message;
}
