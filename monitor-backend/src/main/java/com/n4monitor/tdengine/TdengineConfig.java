package com.n4monitor.tdengine;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

/**
 * TDengine database configuration.
 * <p>
 * All values are read from {@code application.yaml} under the {@code tdengine} prefix.
 * Uses REST mode (jdbc:TAOS-RS) to avoid native library dependency.
 */
@Data
@Configuration
@ConfigurationProperties(prefix = "tdengine")
public class TdengineConfig {

    /** JDBC connection URL (REST mode, port 6041) */
    private String url;

    /** Database username */
    private String username;

    /** Database password */
    private String password;

    /** Database name */
    private String database;

    /** Supertable name for periodic latest-state snapshots */
    private String supertable;

    /** Supertable name for real-time alarm records */
    private String realtimeSupertable;

    /** Fixed rate interval (ms) for periodic sync from Redis to TDengine */
    private long syncFixedRateMs;

    /** Initial delay (ms) before the first periodic sync */
    private long syncInitialDelayMs;
}
