package com.n4monitor.deployment.dto;

import lombok.Data;

@Data
public class DeviceDeployListDto {
    private Long deviceDeployId;
    private String deviceDeployName;
    private String deviceAlias;
    private Integer status;
    private String n4Address;
    private String deviceType;
    private String manufacturer;
    private String model;
    private Boolean isMock;
}
