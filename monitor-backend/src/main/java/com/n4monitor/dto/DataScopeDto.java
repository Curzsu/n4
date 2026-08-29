package com.n4monitor.dto;

import lombok.Data;

@Data
public class DataScopeDto {
    private Long scopeId;
    private Long groupId;
    private String scopeType;
    private String scopeValue;
}
