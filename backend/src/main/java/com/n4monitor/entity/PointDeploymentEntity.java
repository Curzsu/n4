package com.n4monitor.entity;

import lombok.Data;

/**
 * 部署点位表 point_deployment 实体。
 * 当前仅用于报警规则映射查询（path -> alarm_code），故只映射所需最小字段。
 */
@Data
public class PointDeploymentEntity {

    /** 点位部署ID */
    private Long pointDeployId;

    /** topic路径，与 Kafka 消息的 path 一致，作为 Redis 映射 field */
    private String n4Address;

    /** 报警策略(关联 alarm 表的 alarm_name 字段)。为空表示该点位无报警规则 */
    private String alarmCode;
}
