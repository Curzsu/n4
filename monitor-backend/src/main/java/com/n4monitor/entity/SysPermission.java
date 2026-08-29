package com.n4monitor.entity;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class SysPermission {
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
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
    private Boolean isDeleted;
}
