package com.n4monitor.dto;

import lombok.Data;

@Data
public class PermissionFormDto {
    private String permName;
    private String permCode;
    private Integer permType;
    private Long parentId;
    private String path;
    private String component;
    private String icon;
    private Integer sortOrder;
    private Integer status;
}
