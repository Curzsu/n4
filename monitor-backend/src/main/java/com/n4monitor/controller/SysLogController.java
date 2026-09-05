package com.n4monitor.controller;

import com.n4monitor.annotation.BusinessType;
import com.n4monitor.annotation.OperationLog;
import com.n4monitor.common.ApiResponse;
import com.n4monitor.dto.IdsRequest;
import com.n4monitor.dto.OperationLogDto;
import com.n4monitor.dto.PageResult;
import com.n4monitor.service.SysOperationLogService;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDateTime;

@RestController
@RequestMapping("/api/logs")
public class SysLogController {
    private final SysOperationLogService logService;

    public SysLogController(SysOperationLogService logService) {
        this.logService = logService;
    }

    @GetMapping
    @PreAuthorize("hasAuthority('sys:log:list')")
    public ApiResponse<PageResult<OperationLogDto>> page(@RequestParam(required = false) String operatorName,
                                                         @RequestParam(required = false) String module,
                                                         @RequestParam(required = false) String operation,
                                                         @RequestParam(required = false) Integer status,
                                                         @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime startTime,
                                                         @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime endTime,
                                                         @RequestParam(defaultValue = "1") int page,
                                                         @RequestParam(defaultValue = "10") int pageSize) {
        return ApiResponse.success(logService.page(operatorName, module, operation, status, startTime, endTime, page, pageSize));
    }

    @GetMapping("/{logId}")
    @PreAuthorize("hasAuthority('sys:log:list')")
    public ApiResponse<OperationLogDto> detail(@PathVariable Long logId) {
        return ApiResponse.success(logService.detail(logId));
    }

    @DeleteMapping
    @PreAuthorize("hasAuthority('sys:log:delete')")
    @OperationLog(module = "Log Management", operation = "Delete Logs", businessType = BusinessType.DELETE)
    public ApiResponse<Void> delete(@RequestBody IdsRequest request) {
        logService.delete(request);
        return ApiResponse.success(null);
    }

    @DeleteMapping("/clear")
    @PreAuthorize("hasAuthority('sys:log:delete')")
    @OperationLog(module = "Log Management", operation = "Clear All Logs", businessType = BusinessType.DELETE)
    public ApiResponse<Void> clear() {
        logService.clear();
        return ApiResponse.success(null);
    }
}
