package com.n4monitor.dto;

import lombok.Data;

import java.util.List;

@Data
public class GroupFormDto {
    private String groupName;
    private String groupCode;
    private String description;
    private Integer status;
    private List<Long> permissionIds;
}
