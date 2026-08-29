package com.n4monitor.dto;

import lombok.Data;

import java.util.ArrayList;
import java.util.List;

@Data
public class PermissionTreeDto {
    private Long permId;
    private String permName;
    private String permCode;
    private Integer permType;
    private Long parentId;
    private String path;
    private String component;
    private String icon;
    private Integer sortOrder;
    private Integer status;
    private List<PermissionTreeDto> children = new ArrayList<>();
}
