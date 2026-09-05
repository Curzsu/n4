package com.n4monitor.controller;

import com.n4monitor.annotation.BusinessType;
import com.n4monitor.annotation.OperationLog;
import com.n4monitor.common.ApiResponse;
import com.n4monitor.entity.SysDataScope;
import com.n4monitor.service.SysDataScopeService;
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
@RequestMapping("/api/data-scopes")
public class SysDataScopeController {
    private final SysDataScopeService dataScopeService;

    public SysDataScopeController(SysDataScopeService dataScopeService) {
        this.dataScopeService = dataScopeService;
    }

    @GetMapping("/groups/{groupId}")
    @PreAuthorize("hasAuthority('sys:group:list')")
    public ApiResponse<List<SysDataScope>> listByGroup(@PathVariable Long groupId) {
        return ApiResponse.success(dataScopeService.listByGroupId(groupId));
    }

    @PostMapping
    @PreAuthorize("hasAuthority('sys:group:edit')")
    @OperationLog(module = "Data Scope Management", operation = "Add Data Scope", businessType = BusinessType.CREATE)
    public ApiResponse<Void> create(@RequestBody SysDataScope scope) {
        dataScopeService.create(scope);
        return ApiResponse.success(null);
    }

    @PutMapping("/{scopeId}")
    @PreAuthorize("hasAuthority('sys:group:edit')")
    @OperationLog(module = "Data Scope Management", operation = "Edit Data Scope", businessType = BusinessType.UPDATE)
    public ApiResponse<Void> update(@PathVariable Long scopeId, @RequestBody SysDataScope scope) {
        dataScopeService.update(scopeId, scope);
        return ApiResponse.success(null);
    }

    @DeleteMapping("/{scopeId}")
    @PreAuthorize("hasAuthority('sys:group:edit')")
    @OperationLog(module = "Data Scope Management", operation = "Delete Data Scope", businessType = BusinessType.DELETE)
    public ApiResponse<Void> delete(@PathVariable Long scopeId) {
        dataScopeService.delete(scopeId);
        return ApiResponse.success(null);
    }
}
