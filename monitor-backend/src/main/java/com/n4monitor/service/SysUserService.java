package com.n4monitor.service;

import com.n4monitor.dto.ChangePasswordRequest;
import com.n4monitor.dto.GroupDto;
import com.n4monitor.dto.PageResult;
import com.n4monitor.dto.UserDto;
import com.n4monitor.dto.UserFormDto;
import com.n4monitor.entity.SysUser;
import com.n4monitor.exception.BusinessException;
import com.n4monitor.mapper.SysGroupMapper;
import com.n4monitor.mapper.SysUserMapper;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class SysUserService {
    private final SysUserMapper userMapper;
    private final SysGroupMapper groupMapper;
    private final PasswordEncoder passwordEncoder;

    public SysUserService(SysUserMapper userMapper, SysGroupMapper groupMapper, PasswordEncoder passwordEncoder) {
        this.userMapper = userMapper;
        this.groupMapper = groupMapper;
        this.passwordEncoder = passwordEncoder;
    }

    public PageResult<UserDto> page(String username, String realName, Integer status, int page, int pageSize) {
        int currentPage = Math.max(page, 1);
        int currentPageSize = Math.max(pageSize, 1);
        int offset = (currentPage - 1) * currentPageSize;
        List<UserDto> users = userMapper.findPage(username, realName, status, offset, currentPageSize);
        users.forEach(user -> user.setGroups(groupMapper.findByUserId(user.getUserId())));
        long total = userMapper.countPage(username, realName, status);
        return new PageResult<>(users, total, currentPage, currentPageSize);
    }

    public void create(UserFormDto form) {
        if (userMapper.countByUsername(form.getUsername()) > 0) {
            throw new BusinessException("Username already exists");
        }
        if (form.getPassword() == null || form.getPassword().isBlank()) {
            throw new BusinessException("Password cannot be empty");
        }
        validatePasswordStrength(form.getPassword());
        SysUser user = toEntity(null, form);
        user.setPassword(passwordEncoder.encode(form.getPassword()));
        userMapper.insert(user);
        if (form.getGroupIds() != null && !form.getGroupIds().isEmpty()) {
            assignGroups(user.getUserId(), form.getGroupIds());
        }
    }

    private void validatePasswordStrength(String password) {
        if (password.length() < 6) {
            throw new BusinessException("Password must be at least 6 characters long");
        }
    }

    public void update(Long userId, UserFormDto form) {
        userMapper.update(toEntity(userId, form));
        if (form.getGroupIds() != null) {
            assignGroups(userId, form.getGroupIds());
        }
    }

    public void delete(Long userId) {
        userMapper.logicalDelete(userId);
    }

    public void updateStatus(Long userId, Integer status) {
        userMapper.updateStatus(userId, status);
    }

    public void updatePassword(Long userId, ChangePasswordRequest request) {
        SysUser user = userMapper.findById(userId);
        if (user == null) {
            throw new BusinessException("User not found");
        }
        if (request.getOldPassword() != null && !request.getOldPassword().isBlank() && !passwordEncoder.matches(request.getOldPassword(), user.getPassword())) {
            throw new BusinessException("Incorrect old password");
        }
        validatePasswordStrength(request.getNewPassword());
        userMapper.updatePassword(userId, passwordEncoder.encode(request.getNewPassword()));
    }

    public List<GroupDto> groups(Long userId) {
        return groupMapper.findByUserId(userId);
    }

    @Transactional
    public void assignGroups(Long userId, List<Long> groupIds) {
        userMapper.deleteUserGroups(userId);
        if (groupIds != null) {
            for (Long groupId : groupIds) {
                userMapper.insertUserGroup(userId, groupId);
            }
        }
    }

    private SysUser toEntity(Long userId, UserFormDto form) {
        SysUser user = new SysUser();
        user.setUserId(userId);
        user.setUsername(form.getUsername());
        user.setRealName(form.getRealName());
        user.setEmail(form.getEmail());
        user.setPhone(form.getPhone());
        user.setStatus(form.getStatus() == null ? 1 : form.getStatus());
        return user;
    }
}
