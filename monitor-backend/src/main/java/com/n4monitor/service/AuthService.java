package com.n4monitor.service;

import com.n4monitor.dto.ChangePasswordRequest;
import com.n4monitor.dto.CurrentUserDto;
import com.n4monitor.dto.LoginRequest;
import com.n4monitor.dto.LoginResponse;
import com.n4monitor.exception.BusinessException;
import com.n4monitor.mapper.SysGroupMapper;
import com.n4monitor.mapper.SysUserMapper;
import com.n4monitor.security.JwtTokenProvider;
import com.n4monitor.security.LoginUser;
import com.n4monitor.security.SecurityUtils;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;

@Service
public class AuthService {
    private final AuthenticationManager authenticationManager;
    private final JwtTokenProvider tokenProvider;
    private final SysUserMapper userMapper;
    private final SysGroupMapper groupMapper;
    private final SysPermissionService permissionService;
    private final SysUserService userService;

    public AuthService(AuthenticationManager authenticationManager, JwtTokenProvider tokenProvider, SysUserMapper userMapper, SysGroupMapper groupMapper, SysPermissionService permissionService, SysUserService userService) {
        this.authenticationManager = authenticationManager;
        this.tokenProvider = tokenProvider;
        this.userMapper = userMapper;
        this.groupMapper = groupMapper;
        this.permissionService = permissionService;
        this.userService = userService;
    }

    public LoginResponse login(LoginRequest request) {
        Authentication authentication = authenticationManager.authenticate(
                new UsernamePasswordAuthenticationToken(request.getUsername(), request.getPassword()));
        SecurityContextHolder.getContext().setAuthentication(authentication);
        LoginUser loginUser = (LoginUser) authentication.getPrincipal();
        userMapper.updateLastLoginTime(loginUser.getUserId());
        return new LoginResponse(tokenProvider.generateToken(loginUser), currentUser(loginUser));
    }

    public CurrentUserDto currentUser() {
        LoginUser loginUser = SecurityUtils.getCurrentLoginUser();
        if (loginUser == null) {
            throw new BusinessException(401, "Not logged in");
        }
        return currentUser(loginUser);
    }

    public void changePassword(ChangePasswordRequest request) {
        Long userId = SecurityUtils.getCurrentUserId();
        if (userId == null) {
            throw new BusinessException(401, "Not logged in");
        }
        userService.updatePassword(userId, request);
    }

    private CurrentUserDto currentUser(LoginUser loginUser) {
        CurrentUserDto dto = new CurrentUserDto();
        dto.setUserId(loginUser.getUserId());
        dto.setUsername(loginUser.getUser().getUsername());
        dto.setRealName(loginUser.getUser().getRealName());
        dto.setAvatar(loginUser.getUser().getAvatar());
        dto.setGroups(groupMapper.findByUserId(loginUser.getUserId()));
        dto.setPermissions(permissionService.codesByUserId(loginUser.getUserId()));
        dto.setMenus(permissionService.menuTreeByUserId(loginUser.getUserId()));
        return dto;
    }
}
