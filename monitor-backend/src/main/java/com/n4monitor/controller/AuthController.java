package com.n4monitor.controller;

import com.n4monitor.common.ApiResponse;
import com.n4monitor.dto.ChangePasswordRequest;
import com.n4monitor.dto.CurrentUserDto;
import com.n4monitor.dto.LoginRequest;
import com.n4monitor.dto.LoginResponse;
import com.n4monitor.service.AuthService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/auth")
public class AuthController {
    private final AuthService authService;

    public AuthController(AuthService authService) {
        this.authService = authService;
    }

    @PostMapping("/login")
    public ApiResponse<LoginResponse> login(@RequestBody LoginRequest request) {
        return ApiResponse.success(authService.login(request));
    }

    @PostMapping("/logout")
    public ApiResponse<Void> logout() {
        return ApiResponse.success(null);
    }

    @GetMapping("/current-user")
    public ApiResponse<CurrentUserDto> currentUser() {
        return ApiResponse.success(authService.currentUser());
    }

    @PutMapping("/password")
    public ApiResponse<Void> changePassword(@RequestBody ChangePasswordRequest request) {
        authService.changePassword(request);
        return ApiResponse.success(null);
    }
}
