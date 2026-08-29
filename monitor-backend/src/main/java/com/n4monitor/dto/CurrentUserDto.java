package com.n4monitor.dto;

import lombok.Data;

import java.util.List;

@Data
public class CurrentUserDto {
    private Long userId;
    private String username;
    private String realName;
    private String avatar;
    private List<GroupDto> groups;
    private List<String> permissions;
    private List<PermissionTreeDto> menus;
}
