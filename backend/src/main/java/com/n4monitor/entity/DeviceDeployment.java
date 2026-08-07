package com.n4monitor.entity;

import lombok.Data;

@Data
public class DeviceDeployment {
    private Long deviceDeployId;
    private Long deviceTmplId;
    private Long navNodeId;
    private String facePlateId;
    private String alarmCode;
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
    /** 所属模板类型（非表字段，由 JOIN device_template 得出） */
    private String deviceType;
    /** 所属模板厂商（非表字段） */
    private String manufacturer;
    /** 所属模板型号（非表字段） */
    private String model;
    /** 是否 Mock 占位设备（非表字段，device_deploy_name 以 MOCK_ 开头） */
    private Boolean isMock;
}
