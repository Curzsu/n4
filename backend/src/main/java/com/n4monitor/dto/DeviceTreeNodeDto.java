package com.n4monitor.dto;

import lombok.Data;
import java.util.List;

@Data
public class DeviceTreeNodeDto {
    private Long id;
    private String deviceCode;
    private String label;          // device name
    private String type;           // "site", "building", "system", "device"
    private Integer status;        // device status (1=online, etc.)
    private String path;
    private List<DeviceTreeNodeDto> children;
}
