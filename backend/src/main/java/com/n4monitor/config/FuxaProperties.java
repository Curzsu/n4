package com.n4monitor.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Data
@Configuration
@ConfigurationProperties(prefix = "fuxa")
public class FuxaProperties {
    private String baseUrl = "http://127.0.0.1:1881";
    private boolean enabled = true;
    private String templateDeviceId;
    private String templateTopicDeviceId;
    private String templateViewId;
    private String templateViewName;
    private String adapterId;
    private String adapterName;
}
