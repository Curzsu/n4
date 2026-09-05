package com.n4monitor.controller;

import com.n4monitor.annotation.BusinessType;
import com.n4monitor.annotation.OperationLog;
import com.n4monitor.common.ApiResponse;
import com.n4monitor.dto.GroupDto;
import com.n4monitor.dto.GroupFormDto;
import com.n4monitor.dto.IdsRequest;
import com.n4monitor.service.SysGroupService;
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
@RequestMapping("/api/groups")
public class SysGroupController {
    private final SysGroupService groupService;

    public SysGroupController(SysGroupService groupService) {
        this.groupService = groupService;
    }

    @GetMapping
    @PreAuthorize("hasAuthority('sys:group:list') or hasAuthority('sys:user:list')")
    public ApiResponse<List<GroupDto>> list(@RequestParam(required = false) Integer status) {
        return ApiResponse.success(groupService.list(status));
    }

    @PostMapping
    @PreAuthorize("hasAuthority('sys:group:add')")
    @OperationLog(module = "Group Management", operation = "Add Group", businessType = BusinessType.CREATE)
    public ApiResponse<Void> create(@RequestBody GroupFormDto form) {
        groupService.create(form);
        return ApiResponse.success(null);
    }

    @PutMapping("/{groupId}")
    @PreAuthorize("hasAuthority('sys:group:edit')")
    @OperationLog(module = "Group Management", operation = "Edit Group", businessType = BusinessType.UPDATE)
    public ApiResponse<Void> update(@PathVariable Long groupId, @RequestBody GroupFormDto form) {
        groupService.update(groupId, form);
        return ApiResponse.success(null);
    }

    @DeleteMapping("/{groupId}")
    @PreAuthorize("hasAuthority('sys:group:delete')")
    @OperationLog(module = "Group Management", operation = "Delete Group", businessType = BusinessType.DELETE)
    public ApiResponse<Void> delete(@PathVariable Long groupId) {
        groupService.delete(groupId);
        return ApiResponse.success(null);
    }

    @GetMapping("/{groupId}/permissions")
    @PreAuthorize("hasAuthority('sys:group:list')")
    public ApiResponse<List<Long>> permissionIds(@PathVariable Long groupId) {
        return ApiResponse.success(groupService.permissionIds(groupId));
    }

    @PutMapping("/{groupId}/permissions")
    @PreAuthorize("hasAuthority('sys:group:edit')")
    @OperationLog(module = "Group Management", operation = "Assign Group Permissions", businessType = BusinessType.GRANT)
    public ApiResponse<Void> assignPermissions(@PathVariable Long groupId, @RequestBody IdsRequest request) {
        groupService.assignPermissions(groupId, request.getIds());
        return ApiResponse.success(null);
    }
}
