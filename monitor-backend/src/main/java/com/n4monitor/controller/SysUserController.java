package com.n4monitor.controller;

import com.n4monitor.annotation.BusinessType;
import com.n4monitor.annotation.OperationLog;
import com.n4monitor.common.ApiResponse;
import com.n4monitor.dto.ChangePasswordRequest;
import com.n4monitor.dto.GroupDto;
import com.n4monitor.dto.IdsRequest;
import com.n4monitor.dto.PageResult;
import com.n4monitor.dto.StatusRequest;
import com.n4monitor.dto.UserDto;
import com.n4monitor.dto.UserFormDto;
import com.n4monitor.service.SysUserService;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/users")
public class SysUserController {
    private final SysUserService userService;

    public SysUserController(SysUserService userService) {
        this.userService = userService;
    }

    @GetMapping
    @PreAuthorize("hasAuthority('sys:user:list')")
    public ApiResponse<PageResult<UserDto>> page(@RequestParam(required = false) String username,
                                                 @RequestParam(required = false) String realName,
                                                 @RequestParam(required = false) Integer status,
                                                 @RequestParam(defaultValue = "1") int page,
                                                 @RequestParam(defaultValue = "10") int pageSize) {
        return ApiResponse.success(userService.page(username, realName, status, page, pageSize));
    }

    @PostMapping
    @PreAuthorize("hasAuthority('sys:user:add')")
    @OperationLog(module = "User Management", operation = "Add User", businessType = BusinessType.CREATE)
    public ApiResponse<Void> create(@RequestBody UserFormDto form) {
        userService.create(form);
        return ApiResponse.success(null);
    }

    @PutMapping("/{userId}")
    @PreAuthorize("hasAuthority('sys:user:edit')")
    @OperationLog(module = "User Management", operation = "Edit User", businessType = BusinessType.UPDATE)
    public ApiResponse<Void> update(@PathVariable Long userId, @RequestBody UserFormDto form) {
        userService.update(userId, form);
        return ApiResponse.success(null);
    }

    @DeleteMapping("/{userId}")
    @PreAuthorize("hasAuthority('sys:user:delete')")
    @OperationLog(module = "User Management", operation = "Delete User", businessType = BusinessType.DELETE)
    public ApiResponse<Void> delete(@PathVariable Long userId) {
        userService.delete(userId);
        return ApiResponse.success(null);
    }

    @PutMapping("/{userId}/status")
    @PreAuthorize("hasAuthority('sys:user:edit')")
    @OperationLog(module = "User Management", operation = "Update User Status", businessType = BusinessType.UPDATE)
    public ApiResponse<Void> updateStatus(@PathVariable Long userId, @RequestBody StatusRequest request) {
        userService.updateStatus(userId, request.getStatus());
        return ApiResponse.success(null);
    }

    @PutMapping("/{userId}/password")
    @PreAuthorize("hasAuthority('sys:user:edit')")
    @OperationLog(module = "User Management", operation = "Reset User Password", businessType = BusinessType.UPDATE)
    public ApiResponse<Void> updatePassword(@PathVariable Long userId, @RequestBody ChangePasswordRequest request) {
        userService.updatePassword(userId, request);
        return ApiResponse.success(null);
    }

    @GetMapping("/{userId}/groups")
    @PreAuthorize("hasAuthority('sys:user:list')")
    public ApiResponse<List<GroupDto>> groups(@PathVariable Long userId) {
        return ApiResponse.success(userService.groups(userId));
    }

    @PutMapping("/{userId}/groups")
    @PreAuthorize("hasAuthority('sys:user:edit')")
    @OperationLog(module = "User Management", operation = "Assign User Groups", businessType = BusinessType.GRANT)
    public ApiResponse<Void> assignGroups(@PathVariable Long userId, @RequestBody IdsRequest request) {
        userService.assignGroups(userId, request.getIds());
        return ApiResponse.success(null);
    }
}
