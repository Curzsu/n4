package com.n4monitor.entity;

import lombok.Data;

@Data
public class SysDataScope {
    private Long scopeId;
    private Long groupId;
    private String scopeType;
    private String scopeValue;
}
