package com.n4monitor.dto;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class FuxaBindingDto {
    private Long deviceId;
    private String deviceCode;
    private String mqttDeviceId;
    private String modelCode;
    private String fuxaDeviceId;
    private String fuxaDeviceName;
    private String viewName;
    private String iframeUrl;
    private String status;
}
