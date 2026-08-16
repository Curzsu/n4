package com.n4monitor.template.entity;

import lombok.Data;

@Data
public class DeviceTemplate {
    private Long deviceTmplId;
    private String facePlateId;
    private String alarmCode;
    private String deviceTmplName;
    private String deviceType;
    private String model;
    private String manufacturer;
    private String functionalArea;
    private String manuDate;
    private Boolean isDeleted;
    private java.time.LocalDateTime createTime;
    private java.time.LocalDateTime updateTime;
    /** 该模板下的点位数量（非表字段，由查询聚合） */
    private Integer pointCount;
    /** 是否真实数据（名称匹配远程库模板则为 true，本地独有模板为 false） */
    private Boolean isReal;
}
