package com.n4monitor.dto;

import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;

@Data
public class UserDto {
    private Long userId;
    private String username;
    private String realName;
    private String email;
    private String phone;
    private Integer status;
    private String avatar;
    private LocalDateTime lastLoginTime;
    private LocalDateTime createTime;
    private List<GroupDto> groups;
}
