package com.n4monitor.dto;

import lombok.Data;

@Data
public class SystemStatusDto {
    private String systemName;  // "Electrical", "Cooling", "UPS", etc.
    private String status;      // "Normal", "Warning", "Critical"
    private int deviceCount;    // count of devices in this category
    private String icon;        // icon name for frontend
}
