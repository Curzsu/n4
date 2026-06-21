package com.n4monitor.entity;

import lombok.Data;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Data
public class Device {
    private Long deviceId;
    private String deviceCode;
    private String deviceName;
    private String ipAddress;
    private String macAddress;
    private Long productId;

    // System hierarchy fields
    private Long sysParentId;
    private String sysPath;
    private Integer sysLevel;
    private Integer sysSortOrder;

    // Location hierarchy fields
    private Long locParentId;
    private String locPath;
    private Integer locLevel;
    private Integer locSortOrder;

    // Device attributes
    private Integer status;        // 1=online, 2=offline, 3=maintenance, 4=fault
    private LocalDate installDate;
    private LocalDate warrantyExpire;

    // Ops info
    private String region;
    private String department;
    private String responsiblePerson;
    private String contactPhone;

    // Extension & audit
    private String extraAttrs;     // JSON stored as String
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
    private Boolean isDeleted;
}
