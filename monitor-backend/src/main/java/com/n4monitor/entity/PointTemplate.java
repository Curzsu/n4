package com.n4monitor.entity;

import lombok.Data;

@Data
public class PointTemplate {
    private Long pointTmplId;
    private Long deviceTmplId;
    private String alarmCode;
    private String pointName;
    private String pointType;
    private String dataType;
    private String unit;
    private Integer displayPrecision;
    private Boolean isDeleted;
    private java.time.LocalDateTime createTime;
    private java.time.LocalDateTime updateTime;
    /** 所属模板是否为真实数据（非表字段） */
    private Boolean isReal;
}
