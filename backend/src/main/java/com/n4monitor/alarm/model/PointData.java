package com.n4monitor.alarm.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.Data;

import java.io.Serializable;

/**
 * Point data from Kafka message (value JSON deserialization).
 * <p>
 * The upstream message format has been slimmed to four fields:
 * {clientId, path, timestamp, value}. The point {@code type}
 * (numeric|enum|boolean|string) is no longer carried by the message;
 * it is resolved at runtime by the Flink job from the point_deployment
 * table via the Redis {@code alarm:point:rule} metadata map.
 */
@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class PointData implements Serializable {

    /** Source client identifier from the upstream message */
    private String clientId;

    /** Topic path, the unique key of a point */
    private String path;

    /** Point value (string, evaluated per its resolved type) */
    private String value;

    /** Resolved point type: numeric|enum|boolean|string (backfilled from Redis, not from the message) */
    private String type;

    /** Epoch-millis data timestamp from the upstream message */
    private Long timestamp;
}
