package com.n4monitor.dto;

import lombok.Data;

@Data
public class LoginRequest {
    private String username;
    private String password;
}
