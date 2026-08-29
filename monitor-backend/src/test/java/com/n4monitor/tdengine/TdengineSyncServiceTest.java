package com.n4monitor.tdengine;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tests for the URL helper that strips the default-database path segment so
 * the schema bootstrap can connect before the database exists.
 */
class TdengineSyncServiceTest {

    @Test
    void stripsDatabasePathSegment() {
        assertThat(TdengineSyncService.bootstrapUrl("jdbc:TAOS-WS://host:6041/n4"))
                .isEqualTo("jdbc:TAOS-WS://host:6041");
        assertThat(TdengineSyncService.bootstrapUrl("jdbc:TAOS-RS://host:6041/n4"))
                .isEqualTo("jdbc:TAOS-RS://host:6041");
    }

    @Test
    void keepsQueryParameters() {
        assertThat(TdengineSyncService.bootstrapUrl("jdbc:TAOS-WS://host:6041/n4?httpKeepAlive=true"))
                .isEqualTo("jdbc:TAOS-WS://host:6041?httpKeepAlive=true");
    }

    @Test
    void urlsWithoutDatabasePathAreUnchanged() {
        assertThat(TdengineSyncService.bootstrapUrl("jdbc:TAOS-WS://host:6041"))
                .isEqualTo("jdbc:TAOS-WS://host:6041");
        assertThat(TdengineSyncService.bootstrapUrl("jdbc:TAOS-WS://host:6041/"))
                .isEqualTo("jdbc:TAOS-WS://host:6041/");
    }

    @Test
    void nonJdbcUrlsAreUnchanged() {
        assertThat(TdengineSyncService.bootstrapUrl("not-a-jdbc-url")).isEqualTo("not-a-jdbc-url");
    }

    @Test
    void serviceIsConstructibleForBootstrapUse() {
        // Guards the constructor shape used by tests and Spring: config + mapper only.
        TdengineSyncService service = new TdengineSyncService(new TdengineConfig(), new ObjectMapper());
        assertThat(service).isNotNull();
    }
}
