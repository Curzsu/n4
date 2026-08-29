package com.n4monitor.dto;

import lombok.Data;

import java.util.List;

@Data
public class UserFormDto {
    private String username;
    private String password;
    private String realName;
    private String email;
    private String phone;
    private Integer status;
    private List<Long> groupIds;
}
