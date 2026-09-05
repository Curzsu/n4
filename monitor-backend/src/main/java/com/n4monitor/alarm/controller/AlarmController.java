package com.n4monitor.alarm.controller;

import com.n4monitor.alarm.dto.*;
import com.n4monitor.alarm.export.EventExcelWriter;
import com.n4monitor.common.ApiResponse;
import com.n4monitor.alarm.service.AlarmService;
import com.n4monitor.alarm.service.EventExportService;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;

@RestController
@RequestMapping("/api/alarms")
public class AlarmController {

    private final AlarmService alarmService;
    private final EventExportService eventExportService;

    public AlarmController(AlarmService alarmService, EventExportService eventExportService) {
        this.alarmService = alarmService;
        this.eventExportService = eventExportService;
    }

    @GetMapping("/active")
    public ApiResponse<List<AlarmRecordDto>> getActiveAlarms(
            @RequestParam(value = "tab", defaultValue = "active") String tab) {
        return ApiResponse.success(alarmService.getActiveAlarms(tab));
    }

    @GetMapping("/cleared")
    public ApiResponse<List<AlarmRecordDto>> getClearedAlarms(
            @RequestParam(value = "topTs", required = false) Long topTs,
            @RequestParam(value = "offset", defaultValue = "0") Integer offset,
            @RequestParam(value = "limit", defaultValue = "50") Integer limit) {
        return ApiResponse.success(alarmService.getClearedAlarms(topTs, offset, limit));
    }

    @GetMapping("/events")
    public ApiResponse<List<AlarmRecordDto>> getAlarmEvents(
            @RequestParam(value = "topTs", required = false) Long topTs,
            @RequestParam(value = "offset", defaultValue = "0") Integer offset,
            @RequestParam(value = "limit", defaultValue = "50") Integer limit) {
        return ApiResponse.success(alarmService.getAlarmEvents(topTs, offset, limit));
    }

    /**
     * Export alarm_event rows to a streaming xlsx download. Scoped by a
     * mandatory record-time range plus optional severity whitelist and the
     * alarm-only switch; oversized ranges are rejected with a 400 before any
     * Excel work starts. The workbook is built into a temp file first so a
     * mid-export failure surfaces as a clean JSON error instead of a broken
     * download.
     */
    @GetMapping("/events/export")
    public void exportAlarmEvents(
            @RequestParam("start") long start,
            @RequestParam("end") long end,
            @RequestParam(value = "severity", required = false) List<String> severity,
            @RequestParam(value = "alarmOnly", defaultValue = "true") boolean alarmOnly,
            HttpServletResponse response) throws IOException {
        EventExportFilter filter = new EventExportFilter(start, end, severity, alarmOnly);
        Path tempFile = Files.createTempFile("n4-events-export-", ".xlsx");
        try {
            long rows = eventExportService.writeExport(filter, tempFile);
            String fileName = "n4_events_"
                    + LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss"))
                    + ".xlsx";
            response.setContentType(EventExcelWriter.XLSX_CONTENT_TYPE);
            response.setHeader("Content-Disposition", "attachment; filename=\"" + fileName + "\"");
            response.setHeader("X-Export-Rows", String.valueOf(rows));
            response.setContentLengthLong(Files.size(tempFile));
            Files.copy(tempFile, response.getOutputStream());
        } finally {
            Files.deleteIfExists(tempFile);
        }
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
        if (updated == 0 && request.getPaths() != null && !request.getPaths().isEmpty()) {
            return ApiResponse.error(400, "No active alarm matched the given paths");
        }
        return ApiResponse.success(updated);
    }
}
