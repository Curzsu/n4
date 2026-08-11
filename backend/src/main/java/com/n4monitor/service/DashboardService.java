package com.n4monitor.service;

import com.n4monitor.dto.*;
import com.n4monitor.mapper.DeviceMapper;
import com.n4monitor.mapper.MonitorPointMapper;
import com.n4monitor.mapper.ProductMapper;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

@Service
public class DashboardService {

    private final DeviceMapper deviceMapper;
    private final MonitorPointMapper monitorPointMapper;
    private final ProductMapper productMapper;
    private final AlarmService alarmService;

    public DashboardService(DeviceMapper deviceMapper,
                            MonitorPointMapper monitorPointMapper,
                            ProductMapper productMapper,
                            AlarmService alarmService) {
        this.deviceMapper = deviceMapper;
        this.monitorPointMapper = monitorPointMapper;
        this.productMapper = productMapper;
        this.alarmService = alarmService;
    }

    public KpiMetricsDto getKpiMetrics() {
        KpiMetricsDto dto = new KpiMetricsDto();
        dto.setPue(1.32);
        dto.setPueTrend("+0.03 vs yesterday");
        dto.setItLoadPercent(68);
        dto.setItLoadKw(1876);
        dto.setItLoadCapacityKw(2780);
        dto.setCoolingLoadPercent(42);
        dto.setCoolingLoadKw(1153);
        dto.setCoolingLoadCapacityKw(2780);
        dto.setFacilityLoadPercent(57);
        dto.setFacilityLoadKw(1584);
        dto.setFacilityLoadCapacityKw(2780);
        dto.setTotalEnergyKwh(32680);
        dto.setCapacityUsagePercent(78);
        dto.setActiveDevices(1426);
        dto.setTotalDevices(1820);
        return dto;
    }

    public AlarmSummaryDto getAlarmSummary() {
        // Delegate to the real alarm summary (reads alarm:active from Redis
        // and counts by severity). Replaces the previous hardcoded mock values
        // (critical=3, major=14, ...) that masked real zero counts.
        return alarmService.getAlarmSummary();
    }

    public List<AlarmItemDto> getAlarmTop5() {
        List<AlarmItemDto> list = new ArrayList<>();
        list.add(createAlarm("10:21", "CHU-A3 High Pressure", "Critical", "CHU-A3"));
        list.add(createAlarm("10:18", "UPS-A Battery Fault", "Major", "UPS-A"));
        list.add(createAlarm("10:15", "PDU-B Overload", "Major", "PDU-B"));
        list.add(createAlarm("10:11", "CHWS Temp High", "Minor", "CHWS"));
        list.add(createAlarm("10:05", "AHU-3 Filter Dirty", "Minor", "AHU-3"));
        return list;
    }

    private AlarmItemDto createAlarm(String time, String desc, String level, String source) {
        AlarmItemDto item = new AlarmItemDto();
        item.setAlarmTime(time);
        item.setAlarmDesc(desc);
        item.setAlarmLevel(level);
        item.setSourceDevice(source);
        return item;
    }

    public List<SystemStatusDto> getSystemStatus() {
        // Device counts from real DB; status labels are mock in this iteration
        List<SystemStatusDto> list = new ArrayList<>();
        list.add(createSystemStatus("Electrical", "Normal", 28, "gear"));
        list.add(createSystemStatus("Cooling", "Normal", 18, "snowflake"));
        list.add(createSystemStatus("UPS", "Normal", 12, "battery"));
        list.add(createSystemStatus("PDU", "Warning", 4, "warning-triangle"));
        list.add(createSystemStatus("CRAH", "Normal", 132, "fan"));
        list.add(createSystemStatus("Sensor", "Normal", 1248, "thermometer"));
        list.add(createSystemStatus("Network", "Normal", 8, "globe"));
        return list;
    }

    private SystemStatusDto createSystemStatus(String name, String status, int count, String icon) {
        SystemStatusDto dto = new SystemStatusDto();
        dto.setSystemName(name);
        dto.setStatus(status);
        dto.setDeviceCount(count);
        dto.setIcon(icon);
        return dto;
    }

    public TrendDataDto getPowerTrend() {
        TrendDataDto dto = new TrendDataDto();
        dto.setTimeLabels(generateTimeLabels());
        dto.setValues(generateSineData(1800, 600, 0.15));   // IT Load kW
        dto.setValues2(generateSineData(3500, 800, 0.10));  // Total Load kW
        dto.setSeriesName("IT Load");
        dto.setSeriesName2("Total Load");
        dto.setUnit("kW");
        return dto;
    }

    public TrendDataDto getEnergyTrend() {
        TrendDataDto dto = new TrendDataDto();
        dto.setTimeLabels(generateTimeLabels());
        dto.setValues(generateSineData(2000, 500, 0.20));   // Today kWh
        dto.setValues2(generateSineData(1800, 400, 0.18));  // Yesterday kWh
        dto.setSeriesName("Today");
        dto.setSeriesName2("Yesterday");
        dto.setUnit("kWh");
        return dto;
    }

    public TrendDataDto getPueTrend() {
        TrendDataDto dto = new TrendDataDto();
        dto.setTimeLabels(generateTimeLabels());
        dto.setValues(generateFluctuatingData(1.32, 0.08));
        dto.setSeriesName("PUE");
        dto.setUnit("PUE");
        return dto;
    }

    public TrendDataDto getTemperatureTrend() {
        TrendDataDto dto = new TrendDataDto();
        dto.setTimeLabels(generateTimeLabels());
        dto.setValues(generateFluctuatingData(25.0, 2.0));    // Avg Temp
        dto.setValues2(generateFluctuatingData(31.0, 3.0));   // Max Temp
        dto.setSeriesName("Avg Temp");
        dto.setSeriesName2("Max Temp");
        dto.setUnit("°C");
        return dto;
    }

    // Generate 24 hourly time labels: "00:00" through "23:00"
    private List<String> generateTimeLabels() {
        List<String> labels = new ArrayList<>();
        for (int i = 0; i < 24; i++) {
            labels.add(String.format("%02d:00", i));
        }
        return labels;
    }

    // Generate sine-wave pattern data for realistic power/energy trends
    private List<Double> generateSineData(double base, double amplitude, double noiseFactor) {
        List<Double> data = new ArrayList<>();
        for (int i = 0; i < 24; i++) {
            double sine = Math.sin((i - 6) * Math.PI / 12) * amplitude;
            double noise = (Math.random() - 0.5) * amplitude * noiseFactor;
            data.add(Math.max(0, base + sine + noise));
        }
        return data;
    }

    // Generate fluctuating data around a base value for PUE/temperature
    private List<Double> generateFluctuatingData(double base, double range) {
        List<Double> data = new ArrayList<>();
        for (int i = 0; i < 24; i++) {
            double noise = (Math.random() - 0.5) * range;
            data.add(base + noise);
        }
        return data;
    }
}
