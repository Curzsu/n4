package com.n4monitor.entity;

import lombok.Data;

@Data
public class PointDeployment {
    private Long pointDeployId;
    private Long pointTmplId;
    private Long deviceDeployId;
    private String alarmCode;
    private String pointName;
    private String n4Address;
    private String pointCode;
    private String register;
    private String pointType;
    private String dataType;
    private String unit;
    private Integer displayPrecision;
    private Boolean isDeleted;
    private java.time.LocalDateTime createTime;
    private java.time.LocalDateTime updateTime;
    /** 是否真实数据点位（非表字段，由所属模板名称判断） */
    private Boolean isReal;
}
