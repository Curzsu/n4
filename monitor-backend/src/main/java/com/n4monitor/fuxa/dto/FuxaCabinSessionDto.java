package com.n4monitor.fuxa.dto;

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
    private int viewWidth;
    private int viewHeight;
    private boolean enabled;
    private boolean sessionActive;
    private int activeSessionCount;
}
