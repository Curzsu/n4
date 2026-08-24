package com.n4monitor.controller;

import com.n4monitor.annotation.BusinessType;
import com.n4monitor.annotation.OperationLog;
import com.n4monitor.common.ApiResponse;
import com.n4monitor.dto.PermissionFormDto;
import com.n4monitor.dto.PermissionTreeDto;
import com.n4monitor.service.SysPermissionService;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/permissions")
public class SysPermissionController {
    private final SysPermissionService permissionService;

    public SysPermissionController(SysPermissionService permissionService) {
        this.permissionService = permissionService;
    }

    @GetMapping("/tree")
    @PreAuthorize("hasAuthority('sys:permission:list') or hasAuthority('sys:group:list')")
    public ApiResponse<List<PermissionTreeDto>> tree() {
        return ApiResponse.success(permissionService.tree());
    }

    @PostMapping
    @PreAuthorize("hasAuthority('sys:permission:add')")
    @OperationLog(module = "Permission Management", operation = "Add Permission", businessType = BusinessType.CREATE)
    public ApiResponse<Void> create(@RequestBody PermissionFormDto form) {
        permissionService.create(form);
        return ApiResponse.success(null);
    }

    @PutMapping("/{permId}")
    @PreAuthorize("hasAuthority('sys:permission:edit')")
    @OperationLog(module = "Permission Management", operation = "Edit Permission", businessType = BusinessType.UPDATE)
    public ApiResponse<Void> update(@PathVariable Long permId, @RequestBody PermissionFormDto form) {
        permissionService.update(permId, form);
        return ApiResponse.success(null);
    }

    @DeleteMapping("/{permId}")
    @PreAuthorize("hasAuthority('sys:permission:delete')")
    @OperationLog(module = "Permission Management", operation = "Delete Permission", businessType = BusinessType.DELETE)
    public ApiResponse<Void> delete(@PathVariable Long permId) {
        permissionService.delete(permId);
        return ApiResponse.success(null);
    }
}
