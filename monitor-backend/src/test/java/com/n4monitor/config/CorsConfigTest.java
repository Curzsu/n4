package com.n4monitor.config;

import org.junit.jupiter.api.Test;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.servlet.config.annotation.CorsRegistry;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

class CorsConfigTest {
    @Test
    void allowsLocalDevelopmentFrontendWhenViteFallsBackToAnotherPort() {
        ExposedCorsRegistry registry = new ExposedCorsRegistry();

        new CorsConfig().addCorsMappings(registry);

        CorsConfiguration configuration = registry.configurations().get("/api/**");
        assertNotNull(configuration);
        assertNotNull(configuration.checkOrigin("http://localhost:5173"));
        assertNotNull(configuration.checkOrigin("http://localhost:5174"));
        assertNotNull(configuration.checkOrigin("http://127.0.0.1:5174"));
        assertNull(configuration.checkOrigin("http://example.com:5174"));
    }

    private static final class ExposedCorsRegistry extends CorsRegistry {
        private Map<String, CorsConfiguration> configurations() {
            return getCorsConfigurations();
        }
    }
}
