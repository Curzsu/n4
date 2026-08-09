package com.n4monitor.dto;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class FuxaCabinSessionDto {
    private String cabinName;
    private String sessionId;
    private String fuxaDeviceId;
    private String fuxaDeviceName;
    private String viewName;
    private String iframeUrl;
    private boolean enabled;
    private boolean sessionActive;
    private int activeSessionCount;
}
