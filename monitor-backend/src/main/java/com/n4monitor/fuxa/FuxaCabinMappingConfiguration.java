package com.n4monitor.fuxa;

import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.PropertySource;

/**
 * Loads the cabin topology separately from deployment-specific settings such
 * as the FUXA endpoint and idle timeout.
 */
@Configuration
@PropertySource(value = "classpath:fuxa/cabin-clients.properties", encoding = "UTF-8")
public class FuxaCabinMappingConfiguration {
}
