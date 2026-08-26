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

    /**
     * Alarm state transition label, describing how this record relates to the
     * alarm lifecycle. Values (English only):
     * <ul>
     *   <li>{@code Start} - fresh alarm begins</li>
     *   <li>{@code Continue} - same level keeps alarming</li>
     *   <li>{@code {oldLevel} to {newLevel}} - level escalation (e.g. {@code High to High-High})</li>
     *   <li>{@code End} - alarm recovered</li>
     *   <li>{@code Ack} - user acknowledged the alarm</li>
     * </ul>
     * Empty for normal (non-alarm) points.
     */
    private String stageMessage;

    /**
     * Epoch-millis timestamp when the current alarm first started.
     * <p>
     * Persisted in alarm:active so that continue/end events can keep using the
     * alarm's start time (historical timestamp) for the active table and the
     * notification channel, while latest/realtime still use the live data timestamp.
     * Null for non-alarm (normal) states.
     */
    private Long alarmStartTime;
}
