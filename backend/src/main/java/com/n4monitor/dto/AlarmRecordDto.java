package com.n4monitor.dto;

import lombok.Data;

@Data
public class AlarmRecordDto {
    private String time;        // "2024-05-30 10:21:13"
    private String severity;    // "Critical" | "Major" | "Minor" | "Warning"
    private String source;      // "CHWS" | "UPS-A" | ...
    private String message;     // "CHU-A3 High Pressure"
    private String location;    // "Building A / L2"
    private String status;      // "Unack" | "Ack"
    private String ackBy;       // "admin" | "—"
    private String path;        // full point path, used by frontend to extract location
}
