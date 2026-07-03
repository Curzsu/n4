package com.n4monitor.service;

import com.n4monitor.dto.*;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

@Service
public class AlarmService {

    public List<AlarmRecordDto> getActiveAlarms(String tab) {
        List<AlarmRecordDto> allAlarms = new ArrayList<>();
        allAlarms.add(createAlarm("2024-05-30 10:21:13", "Critical", "CHWS", "CHU-A3 High Pressure", "Building A / L2", "Unack", "—"));
        allAlarms.add(createAlarm("2024-05-30 10:18:45", "Major", "UPS-A", "UPS-A Battery Fault", "Building A / UPS Room", "Unack", "—"));
        allAlarms.add(createAlarm("2024-05-30 10:15:22", "Major", "PDU-B", "PDU-B Overload", "Building A / R2", "Unack", "—"));
        allAlarms.add(createAlarm("2024-05-30 09:59:54", "Warning", "CHWR", "CHWR Temp High", "Building A / L4", "Ack", "admin"));
        allAlarms.add(createAlarm("2024-05-30 09:45:30", "Minor", "AHU-3", "AHU-3 Filter Dirty", "Building A / L3", "Ack", "admin"));
        allAlarms.add(createAlarm("2024-05-30 09:21:03", "Warning", "PDU-C", "PDU-C Load High", "Building A / R2", "Ack", "operator2"));
        allAlarms.add(createAlarm("2024-05-30 08:42:55", "Warning", "CRAH-02", "CRAH Humidity High", "Data Hall 1", "Ack", "admin"));
        allAlarms.add(createAlarm("2024-05-30 08:15:10", "Minor", "CHU-A1", "CHU-A1 Compressor Fault", "Building A / L1", "Ack", "admin"));
        allAlarms.add(createAlarm("2024-05-30 07:50:28", "Warning", "UPS-B", "UPS-B Temp High", "Building B / UPS Room", "Unack", "—"));
        allAlarms.add(createAlarm("2024-05-30 07:22:41", "Minor", "CRAH-01", "CRAH-01 Fan Fault", "Data Hall 1", "Ack", "operator1"));
        allAlarms.add(createAlarm("2024-05-30 06:55:19", "Warning", "PDU-A", "PDU-A Voltage High", "Building A / R1", "Ack", "admin"));
        allAlarms.add(createAlarm("2024-05-30 06:18:07", "Minor", "AHU-1", "AHU-1 Coil Frost", "Building A / L2", "Cleared", "admin"));

        // Filter by tab
        switch (tab != null ? tab : "active") {
            case "unacknowledged":
                return allAlarms.stream().filter(a -> "Unack".equals(a.getStatus())).toList();
            case "acknowledged":
                return allAlarms.stream().filter(a -> "Ack".equals(a.getStatus())).toList();
            case "cleared":
                return allAlarms.stream().filter(a -> "Cleared".equals(a.getStatus())).toList();
            default:
                return allAlarms;
        }
    }

    private AlarmRecordDto createAlarm(String time, String severity, String source,
                                       String message, String location, String status, String ackBy) {
        AlarmRecordDto dto = new AlarmRecordDto();
        dto.setTime(time);
        dto.setSeverity(severity);
        dto.setSource(source);
        dto.setMessage(message);
        dto.setLocation(location);
        dto.setStatus(status);
        dto.setAckBy(ackBy);
        return dto;
    }

    public AlarmSummaryDto getAlarmSummary() {
        AlarmSummaryDto dto = new AlarmSummaryDto();
        dto.setCritical(3);
        dto.setMajor(14);
        dto.setMinor(27);
        dto.setWarning(44);
        return dto;
    }

    public AlarmSeverityChartDto getAlarmBySeverity() {
        AlarmSeverityChartDto dto = new AlarmSeverityChartDto();
        dto.setTotal(88);
        List<AlarmSeverityChartDto.SeverityItem> items = new ArrayList<>();
        items.add(createSeverityItem("Critical", 3, 3.4));
        items.add(createSeverityItem("Major", 14, 15.9));
        items.add(createSeverityItem("Minor", 27, 30.7));
        items.add(createSeverityItem("Warning", 44, 50.0));
        dto.setItems(items);
        return dto;
    }

    private AlarmSeverityChartDto.SeverityItem createSeverityItem(String name, int value, double percentage) {
        AlarmSeverityChartDto.SeverityItem item = new AlarmSeverityChartDto.SeverityItem();
        item.setName(name);
        item.setValue(value);
        item.setPercentage(percentage);
        return item;
    }

    public AlarmTrendChartDto getAlarmTrend() {
        AlarmTrendChartDto dto = new AlarmTrendChartDto();
        List<String> timeLabels = new ArrayList<>();
        for (int i = 0; i < 12; i++) {
            int hour = (i * 2 + 12) % 24;
            timeLabels.add(String.format("%02d:00", hour));
        }
        dto.setTimeLabels(timeLabels);

        List<AlarmTrendChartDto.TrendSeries> series = new ArrayList<>();
        series.add(createTrendSeries("Critical", 3, 5));
        series.add(createTrendSeries("Major", 14, 20));
        series.add(createTrendSeries("Minor", 27, 40));
        series.add(createTrendSeries("Warning", 44, 50));
        dto.setSeries(series);
        return dto;
    }

    private AlarmTrendChartDto.TrendSeries createTrendSeries(String name, int base, int range) {
        AlarmTrendChartDto.TrendSeries s = new AlarmTrendChartDto.TrendSeries();
        s.setName(name);
        List<Integer> data = new ArrayList<>();
        for (int i = 0; i < 12; i++) {
            int noise = (int) (Math.random() * range * 0.4);
            data.add(Math.max(0, base - range / 2 + noise));
        }
        s.setData(data);
        return s;
    }

    public AlarmHourChartDto getAlarmByHour() {
        AlarmHourChartDto dto = new AlarmHourChartDto();
        List<String> hours = new ArrayList<>();
        hours.add("00:00"); hours.add("04:00"); hours.add("08:00");
        hours.add("12:00"); hours.add("16:00"); hours.add("20:00"); hours.add("24:00");
        dto.setHours(hours);

        List<Integer> counts = new ArrayList<>();
        for (int i = 0; i < 7; i++) {
            counts.add((int) (Math.random() * 20) + 2);
        }
        dto.setCounts(counts);
        return dto;
    }

    public AlarmSystemChartDto getAlarmBySystem() {
        AlarmSystemChartDto dto = new AlarmSystemChartDto();
        List<AlarmSystemChartDto.SystemItem> items = new ArrayList<>();
        items.add(createSystemItem("Cooling", 28, 31.8));
        items.add(createSystemItem("Electrical", 24, 27.3));
        items.add(createSystemItem("Mechanical", 18, 20.5));
        items.add(createSystemItem("Data Hall", 12, 13.6));
        items.add(createSystemItem("Others", 6, 6.8));
        dto.setItems(items);
        return dto;
    }

    private AlarmSystemChartDto.SystemItem createSystemItem(String name, int value, double percentage) {
        AlarmSystemChartDto.SystemItem item = new AlarmSystemChartDto.SystemItem();
        item.setName(name);
        item.setValue(value);
        item.setPercentage(percentage);
        return item;
    }

    public AlarmSourceChartDto getAlarmTopSources() {
        AlarmSourceChartDto dto = new AlarmSourceChartDto();
        List<AlarmSourceChartDto.SourceItem> items = new ArrayList<>();
        items.add(createSourceItem("CHU-A3", 7));
        items.add(createSourceItem("UPS-A", 6));
        items.add(createSourceItem("PDU-B", 5));
        items.add(createSourceItem("CHWS", 4));
        items.add(createSourceItem("AHU-3", 3));
        dto.setItems(items);
        return dto;
    }

    private AlarmSourceChartDto.SourceItem createSourceItem(String name, int count) {
        AlarmSourceChartDto.SourceItem item = new AlarmSourceChartDto.SourceItem();
        item.setName(name);
        item.setCount(count);
        return item;
    }
}
