package com.n4monitor.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

/**
 * TDengine database configuration.
 * Uses REST mode (jdbc:TAOS-RS) to avoid native library dependency.
 */
@Data
@Configuration
@ConfigurationProperties(prefix = "tdengine")
public class TdengineConfig {

    /** JDBC connection URL (REST mode, default port 6041) */
    private String url = "jdbc:TAOS-RS://47.121.30.136:6041/n4";

    /** Database username */
    private String username = "root";

    /** Database password */
    private String password = "taosdata";

    /** Database name */
    private String database = "n4";

    /** Supertable name */
    private String supertable = "alarm";
}
