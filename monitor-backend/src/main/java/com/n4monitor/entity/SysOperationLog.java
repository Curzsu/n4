package com.n4monitor.entity;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class SysOperationLog {
    private Long logId;
    private Long operatorId;
    private String operatorName;
    private String module;
    private String operation;
    private Integer businessType;
    private String method;
    private String requestMethod;
    private String requestUri;
    private String requestParams;
    private Integer status;
    private String errorMsg;
    private Long costTime;
    private String operatorIp;
    private LocalDateTime createTime;
}
