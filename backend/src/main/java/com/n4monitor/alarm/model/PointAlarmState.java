package com.n4monitor.alarm.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * Latest alarm state of a point (stored in Redis: alarm:latest).
 * <p>
 * Contains original point data from Kafka + alarm evaluation result + additional fields.
 */
@Data
@EqualsAndHashCode(callSuper = true)
@JsonIgnoreProperties(ignoreUnknown = true)
public class PointAlarmState extends PointData {

    /** Threshold level: Normal / High / High-High / Low / Low-Low / Offline */
    private String level;

    /** Alarm severity: Critical / Major / Minor / Warning / Normal */
    private String severity;

    /** Alarm message */
    private String message;

    /** Acknowledged by (default "-") */
    private String ackBy;

    /** Alarm status: Unack / Ack (default "Unack") */
    private String status;

    /** Source (currently empty) */
    private String source;

    /** Location (currently empty) */
    private String location;
}
