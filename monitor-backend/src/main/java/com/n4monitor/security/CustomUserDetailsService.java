package com.n4monitor.security;

import com.n4monitor.entity.SysUser;
import com.n4monitor.mapper.SysPermissionMapper;
import com.n4monitor.mapper.SysUserMapper;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class CustomUserDetailsService implements UserDetailsService {
    private final SysUserMapper userMapper;
    private final SysPermissionMapper permissionMapper;

    public CustomUserDetailsService(SysUserMapper userMapper, SysPermissionMapper permissionMapper) {
        this.userMapper = userMapper;
        this.permissionMapper = permissionMapper;
    }

    @Override
    public UserDetails loadUserByUsername(String username) throws UsernameNotFoundException {
        SysUser user = userMapper.findByUsername(username);
        if (user == null) {
            throw new UsernameNotFoundException("User not found: " + username);
        }
        List<String> permissions = permissionMapper.findCodesByUserId(user.getUserId());
        return new LoginUser(user, permissions);
    }
}
