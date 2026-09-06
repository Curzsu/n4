package com.n4monitor.template.entity;

import lombok.Data;
import com.fasterxml.jackson.annotation.JsonProperty;

@Data
public class PointTemplate {
    private Long pointTmplId;
    private Long deviceTmplId;
    private String alarmCode;
    private String pointName;
    private String type;
    private String unit;
    private Integer displayPrecision;
    private Boolean isDeleted;
    private java.time.LocalDateTime createTime;
    private java.time.LocalDateTime updateTime;
    /** 所属模板是否为真实数据（非表字段） */
    private Boolean isReal;

    /** Legacy request fields kept out of the canonical point_template contract. */
    @JsonProperty(access = JsonProperty.Access.WRITE_ONLY)
    private String pointType;
    @JsonProperty(access = JsonProperty.Access.WRITE_ONLY)
    private String dataType;
}
