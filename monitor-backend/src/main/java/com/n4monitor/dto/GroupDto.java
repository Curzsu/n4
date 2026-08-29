package com.n4monitor.dto;

import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;

@Data
public class GroupDto {
    private Long groupId;
    private String groupName;
    private String groupCode;
    private String description;
    private Integer status;
    private Integer userCount;
    private List<Long> permissionIds;
    private LocalDateTime createTime;
}
