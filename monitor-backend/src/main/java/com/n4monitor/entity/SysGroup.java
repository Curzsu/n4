package com.n4monitor.entity;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class SysGroup {
    private Long groupId;
    private String groupName;
    private String groupCode;
    private String description;
    private Integer status;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
    private Boolean isDeleted;
}
