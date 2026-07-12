package com.n4monitor.controller;

import com.n4monitor.dto.*;
import com.n4monitor.service.AlarmService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/alarms")
public class AlarmController {

    private final AlarmService alarmService;

    public AlarmController(AlarmService alarmService) {
        this.alarmService = alarmService;
    }

    @GetMapping("/active")
    public ApiResponse<List<AlarmRecordDto>> getActiveAlarms(
            @RequestParam(value = "tab", defaultValue = "active") String tab) {
        return ApiResponse.success(alarmService.getActiveAlarms(tab));
    }

    @GetMapping("/summary")
    public ApiResponse<AlarmSummaryDto> getAlarmSummary() {
        return ApiResponse.success(alarmService.getAlarmSummary());
    }

    @GetMapping("/by-severity")
    public ApiResponse<AlarmSeverityChartDto> getAlarmBySeverity() {
        return ApiResponse.success(alarmService.getAlarmBySeverity());
    }

    @GetMapping("/trend")
    public ApiResponse<AlarmTrendChartDto> getAlarmTrend() {
        return ApiResponse.success(alarmService.getAlarmTrend());
    }

    @GetMapping("/by-hour")
    public ApiResponse<AlarmHourChartDto> getAlarmByHour() {
        return ApiResponse.success(alarmService.getAlarmByHour());
    }

    @GetMapping("/by-system")
    public ApiResponse<AlarmSystemChartDto> getAlarmBySystem() {
        return ApiResponse.success(alarmService.getAlarmBySystem());
    }

    @GetMapping("/top-sources")
    public ApiResponse<AlarmSourceChartDto> getAlarmTopSources() {
        return ApiResponse.success(alarmService.getAlarmTopSources());
    }

    @PostMapping("/ack")
    public ApiResponse<Integer> ackAlarms(@RequestBody AlarmAckRequestDto request) {
        int updated = alarmService.acknowledgeAlarms(request.getPaths(), request.getAckBy());
        return ApiResponse.success(updated);
    }
}
