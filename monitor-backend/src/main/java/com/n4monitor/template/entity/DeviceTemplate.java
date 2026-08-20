package com.n4monitor.template.entity;

import lombok.Data;

@Data
public class DeviceTemplate {
    private Long deviceTmplId;
    private String facePlateId;
    private String alarmCode;
    private String deviceTmplName;
    private String n4Name;
    private String deviceType;
    private String model;
    private String manufacturer;
    private String purpose;
    private String functionalArea;
    private String manuDate;
    private Boolean isDeleted;
    private java.time.LocalDateTime createTime;
    private java.time.LocalDateTime updateTime;
    /** Number of points under this template (aggregated field) */
    private Integer pointCount;
    /** Whether it is real template data */
    private Boolean isReal;
}
