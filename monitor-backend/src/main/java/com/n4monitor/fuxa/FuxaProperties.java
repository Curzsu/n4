package com.n4monitor.fuxa;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

import java.util.LinkedHashMap;
import java.util.Map;

@Data
@Configuration
@ConfigurationProperties(prefix = "fuxa")
public class FuxaProperties {
    private String baseUrl = "http://127.0.0.1:1881";
    private boolean enabled = true;
    /** Keep a client warm briefly after its last viewer leaves. */
    private long cabinIdleShutdownDelaySeconds = 60;
    /**
     * Cabin name to FUXA MQTT client/view mapping.  The Client is disabled by
     * default in FUXA and is enabled only when its cabin monitor is opened.
     */
    private Map<String, CabinClient> cabinClients = new LinkedHashMap<>();

    @Data
    public static class CabinClient {
        /** Stable FUXA device id. Preferred over deviceName when both are set. */
        private String deviceId;
        /** Human-readable FUXA MQTT client name. */
        private String deviceName;
        /** FUXA view rendered for this cabin. */
        private String viewName;
        /** Reusable cabin template selected after the runtime host maps tags. */
        private String templateViewName;
        /** Native FUXA view dimensions used for responsive iframe scaling. */
        private int viewWidth = 1780;
        private int viewHeight = 980;
    }
}
