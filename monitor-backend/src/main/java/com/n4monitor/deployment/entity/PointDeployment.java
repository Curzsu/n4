package com.n4monitor.deployment.entity;

import lombok.Data;
import com.fasterxml.jackson.annotation.JsonProperty;

@Data
public class PointDeployment {
    private Long pointDeployId;
    private Long pointTmplId;
    private Long deviceDeployId;
    private String alarmCode;
    private String pointName;
    private String n4Address;
    private String pointCode;
    private String dataType;
    private String unit;
    private Integer displayPrecision;
    private String allPath;
    private String type;
    private Double conversionScale;
    private Double conversionOffset;
    private String pollFrequency;
    private String addressFormat;
    private String modbusAddress;
    private Integer numberOfBits;
    private Boolean isDeleted;
    private java.time.LocalDateTime createTime;
    private java.time.LocalDateTime updateTime;
    /** 是否真实数据点位（非表字段，由所属模板名称判断） */
    private Boolean isReal;

    /** Legacy fields are not part of the current point_deployment contract. */
    @JsonProperty(access = JsonProperty.Access.WRITE_ONLY)
    private String register;
    @JsonProperty(access = JsonProperty.Access.WRITE_ONLY)
    private String pointType;
}
