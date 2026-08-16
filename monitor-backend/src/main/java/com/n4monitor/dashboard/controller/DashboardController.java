package com.n4monitor.dashboard.controller;

import com.n4monitor.alarm.dto.AlarmItemDto;
import com.n4monitor.alarm.dto.AlarmSummaryDto;
import com.n4monitor.common.ApiResponse;
import com.n4monitor.dashboard.dto.*;
import com.n4monitor.dashboard.service.DashboardService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/dashboard")
public class DashboardController {

    private final DashboardService dashboardService;

    public DashboardController(DashboardService dashboardService) {
        this.dashboardService = dashboardService;
    }

    @GetMapping("/kpi")
    public ApiResponse<KpiMetricsDto> getKpi() {
        return ApiResponse.success(dashboardService.getKpiMetrics());
    }

    @GetMapping("/alarm-summary")
    public ApiResponse<AlarmSummaryDto> getAlarmSummary() {
        return ApiResponse.success(dashboardService.getAlarmSummary());
    }

    @GetMapping("/alarm-top5")
    public ApiResponse<List<AlarmItemDto>> getAlarmTop5() {
        return ApiResponse.success(dashboardService.getAlarmTop5());
    }

    @GetMapping("/system-status")
    public ApiResponse<List<SystemStatusDto>> getSystemStatus() {
        return ApiResponse.success(dashboardService.getSystemStatus());
    }

    @GetMapping("/power-trend")
    public ApiResponse<TrendDataDto> getPowerTrend() {
        return ApiResponse.success(dashboardService.getPowerTrend());
    }

    @GetMapping("/energy-trend")
    public ApiResponse<TrendDataDto> getEnergyTrend() {
        return ApiResponse.success(dashboardService.getEnergyTrend());
    }

    @GetMapping("/pue-trend")
    public ApiResponse<TrendDataDto> getPueTrend() {
        return ApiResponse.success(dashboardService.getPueTrend());
    }

    @GetMapping("/temperature-trend")
    public ApiResponse<TrendDataDto> getTemperatureTrend() {
        return ApiResponse.success(dashboardService.getTemperatureTrend());
    }
}
