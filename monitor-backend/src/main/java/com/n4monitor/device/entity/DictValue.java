package com.n4monitor.device.entity;

import lombok.Data;

@Data
public class DictValue {
    private Long dictId;
    private String typeName;
    private String dictKey;
    private String dictValue;
    private Integer sortValue;
    private Integer isActive;
}
