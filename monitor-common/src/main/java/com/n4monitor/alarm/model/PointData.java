package com.n4monitor.alarm.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.Data;

import java.io.Serializable;

/**
 * Point data from Kafka message (value JSON deserialization).
 * <p>
 * The upstream message carries five fields:
 * {clientId, mqttPath, path, timestamp, value}. The point {@code type}
 * (numeric|enum|boolean|string) is no longer carried by the message;
 * it is resolved at runtime by the Flink job from the point_deployment
 * table via the Redis {@code alarm:point:rule} metadata map.
 * <p>
 * {@code mqttPath} is the point identifier used throughout alarm processing
 * (keyBy partitioning, Redis keys, TDengine tags). It matches the
 * {@code n4_address} column in the point_deployment table. The legacy
 * {@code path} field (slot path) is retained for reference but is not used
 * in processing logic.
 */
@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class PointData implements Serializable {

    /** Source client identifier from the upstream message */
    private String clientId;

    /** MQTT topic path, the unique key of a point. Used for keyBy partitioning,
     *  Redis alarm:active / alarm:latest keys, and rule lookups. Matches the
     *  n4_address column in point_deployment. */
    private String mqttPath;

    /** Legacy slot path from the upstream message (e.g. slot:/Drivers/...).
     *  Retained for reference; not used in alarm processing. */
    private String path;

    /** Point value (string, evaluated per its resolved type) */
    private String value;

    /** Resolved point type: numeric|enum|boolean|string (backfilled from Redis, not from the message) */
    private String type;

    /** Epoch-millis data timestamp from the upstream message */
    private Long timestamp;
}
