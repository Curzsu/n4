package com.n4monitor.fuxa.entity;

import lombok.Data;

@Data
public class FuxaDeviceBinding {
    private Long deviceId;
    private String deviceCode;
    private String mqttDeviceId;
    private String modelCode;
    private String fuxaDeviceId;
    private String fuxaDeviceName;
    private String viewName;
    private String provisionStatus;
    private String lastError;
}
