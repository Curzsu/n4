package com.n4monitor.entity;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class SysUser {
    private Long userId;
    private String username;
    private String password;
    private String realName;
    private String email;
    private String phone;
    private Integer status;
    private String avatar;
    private LocalDateTime lastLoginTime;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
    private Boolean isDeleted;
}
