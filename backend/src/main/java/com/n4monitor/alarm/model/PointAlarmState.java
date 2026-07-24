package com.n4monitor.alarm.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.io.Serializable;

/**
 * Latest alarm state of a point (stored in Redis: alarm:latest).
 * <p>
 * Contains original point data from Kafka + alarm evaluation result + additional fields.
 */
@Data
@EqualsAndHashCode(callSuper = true)
@JsonIgnoreProperties(ignoreUnknown = true)
public class PointAlarmState extends PointData implements Serializable {

    /** Threshold level: Boolean / High / High-High / Low / Low-Low */
    private String level;

    /** Alarm severity: Critical / Major / Minor / Warning */
    private String severity;

    /** Alarm message */
    private String message;

    /** Alarm trigger delay in seconds (per-point, from alarm rule) */
    private Integer timeDelay;

    /** Deadband value (per-point, from numeric alarm rule) */
    private String deadband;

    /** Acknowledged by (default "-") */
    private String ackBy;

    /** Alarm status: Unack / Ack (default "Unack") */
    private String status;

    /** Source derived from the last two segments of path */
    private String source;

    /** Location derived from the third segment of path */
    private String location;

    /** Alarm duration in seconds (default "0") */
    private String duration;

    /** Alarm phase: start / continue / end (empty for non-alarm) */
    private String stage;
}
