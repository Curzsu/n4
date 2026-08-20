package com.n4monitor.alarm.dto;

import lombok.Data;

@Data
public class AlarmRecordDto {
    private String time;        // "2024-05-30 10:21:13"
    private String value;       // point value as text
    private String level;       // alarm threshold level
    private String severity;    // "Critical" | "Major" | "Minor" | "Warning"
    private String source;      // "CHWS" | "UPS-A" | ...
    private String message;     // "CHU-A3 High Pressure"
    private String location;    // "Building A / L2"
    private String status;      // "Unack" | "Ack"
    private String ackBy;       // "admin" | "-"
    private String duration;    // alarm duration in seconds
    private String path;        // full point path, used by frontend to extract location
    private String stage;       // alarm phase: start / continue / end (empty for non-alarm)
    private String stageMessage;  // alarm state transition: Start / Continue / {old} to {new} / End / Ack
    private String alarmStartTime;  // when the alarm first started (formatted), from TDengine alarm_start_time
}
