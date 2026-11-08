package com.n4monitor.tdengine;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

/**
 * TDengine database configuration.
 * <p>
 * All values are read from {@code application.yaml} under the {@code tdengine} prefix.
 * Defaults to REST mode ({@code jdbc:TAOS-RS}, port 6041) - measured faster than
 * WebSocket for this project's batched-statement workload (the WS fetch protocol
 * adds a round trip per query, and plain text SQL gains nothing from WS binary
 * binding). WebSocket mode ({@code jdbc:TAOS-WS}) stays available by changing
 * the URL only.
 */
@Data
@Configuration
@ConfigurationProperties(prefix = "tdengine")
public class TdengineConfig {

    /** Disable history reads and persistence in metadata-only experiments. */
    private boolean enabled = true;

    /** JDBC connection URL (REST mode, port 6041) */
    private String url;

    /** Database username */
    private String username;

    /** Database password */
    private String password;

    /** Database name */
    private String database;

    /** Supertable name for real-time alarm records */
    private String realtimeSupertable;

    /** Supertable name for full-detail point event persistence (every processed frame) */
    private String eventSupertable;

    /**
     * Recent-write window (days) applied to history queries so they prune by
     * the indexed primary timestamp instead of scanning the full KEEP range.
     * 0 disables the window filter.
     */
    private int queryWindowDays = 7;
}
