package com.n4monitor.device.entity;

import lombok.Data;

@Data
public class MonitorPoint {
    private Long pointId;
    private String registerId;
    private String pointName;
    private String pointCode;
    private Integer pointType;     // 1=DI, 2=DO, 3=AI, 4=AO
    private Integer dataType;      // 1=int, 2=float
    private String unit;
    private Integer displayPrecision;
    private Long deviceId;
    private String pointValue;
    private Integer pointStatus;   // 1=normal, 0=alarm
}
