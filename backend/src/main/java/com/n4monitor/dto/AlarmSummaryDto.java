package com.n4monitor.dto;

import lombok.Data;

@Data
public class AlarmSummaryDto {
    private int critical;   // 3
    private int major;      // 14
    private int minor;      // 27
    private int warning;    // 44
}
