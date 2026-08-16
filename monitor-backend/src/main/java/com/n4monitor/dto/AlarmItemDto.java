package com.n4monitor.dto;

import lombok.Data;

@Data
public class AlarmItemDto {
    private String alarmTime;   // e.g. "10:21"
    private String alarmDesc;   // e.g. "CHU-A3 High Pressure"
    private String alarmLevel;  // "Critical", "Major", "Minor", "Warning"
    private String sourceDevice;
}
