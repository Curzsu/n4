package com.n4monitor.dto;

import lombok.Data;

@Data
public class FuxaDiscoveryRequest {
    private Long deviceId;
    private String deviceCode;
    private String mqttDeviceId;
    private String mqttTopicPrefix;
    private String modelCode = "SPM32";
}
