package com.n4monitor.entity;

import lombok.Data;

/**
 * Entity for the point_deployment table.
 * Currently used only for the alarm-rule mapping query (path -> alarm_code),
 * so only the minimal required fields are mapped.
 */
@Data
public class PointDeploymentEntity {

    /** Point deployment ID */
    private Long pointDeployId;

    /** Topic path, identical to the Kafka message path; used as the Redis mapping field */
    private String n4Address;

    /** Alarm policy (references alarm_name in the alarm table). Empty means the point has no alarm rule */
    private String alarmCode;

    /** Point data type: numeric|enum|boolean|string. Every point must have one. */
    private String type;
}
