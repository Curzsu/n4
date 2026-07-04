package com.n4monitor.alarm.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.Data;

/**
 * Kafka 消息中的点位数据 (value JSON 反序列化)
 */
@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class PointData {

    private String name;
    private String displayName;
    private String path;
    private String value;
    private String type;
    private String unit;
    private String max;
    private String min;
    private String ip;
    private String port;
    private String other;
    private Long timestamp;
}
