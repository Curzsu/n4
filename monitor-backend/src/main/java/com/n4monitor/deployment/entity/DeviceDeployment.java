package com.n4monitor.deployment.entity;

import lombok.Data;

@Data
public class DeviceDeployment {
    private Long deviceDeployId;
    private Long deviceTmplId;
    private Long navNodeId;
    private String facePlateId;
    private String alarmCode;
    private String systemName;
    private String deviceDeployName;
    private String deviceAlias;
    private String n4Address;
    private String ipAddress;
    private String macAddress;
    private Integer status;
    private String installDate;
    private String warrantyExpire;
    private String location;
    private String department;
    private String responsiblePerson;
    private String contactPhone;
    private String extraAttrs;
    private Boolean isDeleted;
    private java.time.LocalDateTime createTime;
    private java.time.LocalDateTime updateTime;
    /** Associated template device type (non-table field, obtained from JOIN device_template) */
    private String deviceType;
    /** Associated template manufacturer (non-table field) */
    private String manufacturer;
    /** Associated template model (non-table field) */
    private String model;
    /** Whether it is a mock placeholder device (non-table field, device_deploy_name starts with MOCK_) */
    private Boolean isMock;
}
