package com.n4monitor.alarm.service;

import com.n4monitor.alarm.model.AlarmSeverity;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.n4monitor.alarm.dto.AlarmHourChartDto;
import com.n4monitor.alarm.dto.AlarmRecordDto;
import com.n4monitor.alarm.dto.AlarmSeverityChartDto;
import com.n4monitor.alarm.dto.AlarmSourceChartDto;
import com.n4monitor.alarm.dto.AlarmSummaryDto;
import com.n4monitor.alarm.dto.AlarmSystemChartDto;
import com.n4monitor.alarm.dto.AlarmTrendChartDto;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.stream.Collectors;

/**
 * Single home for every alarm aggregation (summary / severity / trend / hourly
 * / system / top-sources, plus the WebSocket aggregate payload). The counting
 * logic used to be copy-pasted across AlarmService methods and
 * AlarmNotificationListener; it now lives here as pure functions over the
 * parsed snapshot so all consumers share one implementation and one data read.
 * <p>
 * Behavioral notes preserved from the original implementations:
 * <ul>
 *   <li>Summary/severity count only the four configured severities; records
 *       with another (or empty) severity still count toward severity-total.</li>
 *   <li>The WebSocket payload counts missing severity as "Normal" and includes
 *       it in its total, matching the pre-refactor listener output.</li>
 *   <li>Top-sources excludes non-alarm records (severity empty or "Normal").</li>
 * </ul>
 */
public final class AlarmAggregator {

    private static final DateTimeFormatter HOUR_FORMAT = DateTimeFormatter.ofPattern("HH:00");

    private static final List<String> SEVERITIES = AlarmSeverity.ORDERED;

    private AlarmAggregator() {
    }

    static AlarmSummaryDto summary(List<ActiveAlarm> alarms) {
        AlarmSummaryDto dto = new AlarmSummaryDto();
        dto.setCritical(count(alarms, "Critical"));
        dto.setMajor(count(alarms, "Major"));
        dto.setMinor(count(alarms, "Minor"));
        dto.setWarning(count(alarms, "Warning"));
        return dto;
    }

    static AlarmSeverityChartDto bySeverity(List<ActiveAlarm> alarms) {
        int total = alarms.size();
        Map<String, Long> countBySeverity = alarms.stream()
                .collect(Collectors.groupingBy(a -> a.record().getSeverity(), Collectors.counting()));

        AlarmSeverityChartDto dto = new AlarmSeverityChartDto();
        dto.setTotal(total);
        List<AlarmSeverityChartDto.SeverityItem> items = new ArrayList<>();
        for (String severity : SEVERITIES) {
            long count = countBySeverity.getOrDefault(severity, 0L);
            AlarmSeverityChartDto.SeverityItem item = new AlarmSeverityChartDto.SeverityItem();
            item.setName(severity);
            item.setValue((int) count);
            item.setPercentage(total > 0 ? Math.round(count * 1000.0 / total) / 10.0 : 0);
            items.add(item);
        }
        dto.setItems(items);
        return dto;
    }

    static AlarmTrendChartDto trend(List<ActiveAlarm> alarms) {
        // 24 pre-seeded hour buckets x severity counts
        Map<String, Map<String, Integer>> hourlySeverity = new TreeMap<>();
        for (int h = 0; h < 24; h++) {
            hourlySeverity.put(String.format("%02d:00", h), new HashMap<>());
        }
        for (ActiveAlarm alarm : alarms) {
            if (alarm.epochMillis() == null) {
                continue;
            }
            String hourKey = LocalDateTime.ofInstant(
                    Instant.ofEpochMilli(alarm.epochMillis()), ZoneId.systemDefault()).format(HOUR_FORMAT);
            String severity = orNormal(alarm.record().getSeverity());
            hourlySeverity.computeIfAbsent(hourKey, k -> new HashMap<>()).merge(severity, 1, Integer::sum);
        }

        AlarmTrendChartDto dto = new AlarmTrendChartDto();
        List<String> timeLabels = new ArrayList<>(hourlySeverity.keySet());
        dto.setTimeLabels(timeLabels);
        List<AlarmTrendChartDto.TrendSeries> series = new ArrayList<>();
        for (String severity : SEVERITIES) {
            AlarmTrendChartDto.TrendSeries s = new AlarmTrendChartDto.TrendSeries();
            s.setName(severity);
            List<Integer> data = new ArrayList<>();
            for (String hour : timeLabels) {
                data.add(hourlySeverity.get(hour).getOrDefault(severity, 0));
            }
            s.setData(data);
            series.add(s);
        }
        dto.setSeries(series);
        return dto;
    }

    static AlarmHourChartDto byHour(List<ActiveAlarm> alarms) {
        Map<String, Integer> hourCount = new TreeMap<>();
        for (int h = 0; h < 24; h++) {
            hourCount.put(String.format("%02d:00", h), 0);
        }
        for (ActiveAlarm alarm : alarms) {
            if (alarm.epochMillis() == null) {
                continue;
            }
            String hourKey = LocalDateTime.ofInstant(
                    Instant.ofEpochMilli(alarm.epochMillis()), ZoneId.systemDefault()).format(HOUR_FORMAT);
            hourCount.merge(hourKey, 1, Integer::sum);
        }

        AlarmHourChartDto dto = new AlarmHourChartDto();
        List<String> hours = new ArrayList<>();
        List<Integer> counts = new ArrayList<>();
        for (int h = 0; h < 24; h += 4) {
            String hourKey = String.format("%02d:00", h);
            hours.add(hourKey);
            counts.add(hourCount.getOrDefault(hourKey, 0));
        }
        dto.setHours(hours);
        dto.setCounts(counts);
        return dto;
    }

    static AlarmSystemChartDto bySystem(List<ActiveAlarm> alarms) {
        // System name = 3rd segment of the hash key (the point path),
        // e.g. "n4/Drivers/ModbusTcpNetwork/LVP_A4/..." -> "LVP_A4".
        Map<String, Integer> systemCount = new HashMap<>();
        for (ActiveAlarm alarm : alarms) {
            String[] parts = alarm.path().split("/");
            String system = parts.length >= 3 ? parts[2] : "Unknown";
            systemCount.merge(system, 1, Integer::sum);
        }

        int total = systemCount.values().stream().mapToInt(Integer::intValue).sum();
        AlarmSystemChartDto dto = new AlarmSystemChartDto();
        dto.setItems(systemCount.entrySet().stream()
                .sorted(Map.Entry.<String, Integer>comparingByValue().reversed())
                .limit(5)
                .map(e -> {
                    AlarmSystemChartDto.SystemItem item = new AlarmSystemChartDto.SystemItem();
                    item.setName(e.getKey());
                    item.setValue(e.getValue());
                    item.setPercentage(total > 0 ? Math.round(e.getValue() * 1000.0 / total) / 10.0 : 0);
                    return item;
                })
                .collect(Collectors.toList()));
        return dto;
    }

    static AlarmSourceChartDto topSources(List<ActiveAlarm> alarms) {
        Map<String, Integer> sourceCount = new HashMap<>();
        for (ActiveAlarm alarm : alarms) {
            String severity = alarm.record().getSeverity();
            // Only count actual alarm records: an empty severity comes from the
            // record DTO default and means "non-alarm", same as "Normal".
            if (severity == null || severity.isEmpty() || AlarmSeverity.NORMAL.equals(severity)) {
                continue;
            }
            String source = alarm.record().getSource();
            sourceCount.merge(source == null || source.isEmpty() ? "Unknown" : source, 1, Integer::sum);
        }

        AlarmSourceChartDto dto = new AlarmSourceChartDto();
        dto.setItems(sourceCount.entrySet().stream()
                .sorted(Map.Entry.<String, Integer>comparingByValue().reversed())
                .limit(5)
                .map(e -> {
                    AlarmSourceChartDto.SourceItem item = new AlarmSourceChartDto.SourceItem();
                    item.setName(e.getKey());
                    item.setCount(e.getValue());
                    return item;
                })
                .collect(Collectors.toList()));
        return dto;
    }

    /**
     * Fill the aggregate section of the WebSocket alarm_update payload
     * (summary / severityData / hourlyData) - the exact shapes the pre-refactor
     * AlarmNotificationListener produced.
     */
    public static void writeWebSocketAggregates(ObjectMapper objectMapper, List<ActiveAlarm> alarms,
                                                ObjectNode summary, ObjectNode severityData, ObjectNode hourlyData) {
        int critical = 0, major = 0, minor = 0, warning = 0, normal = 0;
        Map<String, Integer> hourCount = new HashMap<>();
        for (int h = 0; h < 24; h++) {
            hourCount.put(String.format("%02d:00", h), 0);
        }

        for (ActiveAlarm alarm : alarms) {
            String severity = orNormal(alarm.record().getSeverity());
            switch (severity) {
                case AlarmSeverity.CRITICAL -> critical++;
                case AlarmSeverity.MAJOR -> major++;
                case AlarmSeverity.MINOR -> minor++;
                case AlarmSeverity.WARNING -> warning++;
                default -> normal++;
            }
            if (alarm.epochMillis() != null) {
                String hourKey = LocalDateTime.ofInstant(
                        Instant.ofEpochMilli(alarm.epochMillis()), ZoneId.systemDefault()).format(HOUR_FORMAT);
                hourCount.merge(hourKey, 1, Integer::sum);
            }
        }

        summary.put("critical", critical);
        summary.put("major", major);
        summary.put("minor", minor);
        summary.put("warning", warning);

        int total = critical + major + minor + warning + normal;
        severityData.put("total", total);
        ArrayNode severityItems = objectMapper.createArrayNode();
        addSeverityItem(objectMapper, severityItems, AlarmSeverity.CRITICAL, critical, total);
        addSeverityItem(objectMapper, severityItems, AlarmSeverity.MAJOR, major, total);
        addSeverityItem(objectMapper, severityItems, AlarmSeverity.MINOR, minor, total);
        addSeverityItem(objectMapper, severityItems, AlarmSeverity.WARNING, warning, total);
        severityData.set("items", severityItems);

        ArrayNode hours = objectMapper.createArrayNode();
        ArrayNode counts = objectMapper.createArrayNode();
        for (int h = 0; h < 24; h += 4) {
            hours.add(String.format("%02d:00", h));
            counts.add(hourCount.getOrDefault(String.format("%02d:00", h), 0));
        }
        hourlyData.set("hours", hours);
        hourlyData.set("counts", counts);
    }

    private static void addSeverityItem(ObjectMapper objectMapper, ArrayNode items,
                                        String name, int value, int total) {
        ObjectNode item = objectMapper.createObjectNode();
        item.put("name", name);
        item.put("value", value);
        item.put("percentage", total > 0 ? Math.round(value * 1000.0 / total) / 10.0 : 0);
        items.add(item);
    }

    private static int count(List<ActiveAlarm> alarms, String severity) {
        return (int) alarms.stream()
                .filter(a -> severity.equals(a.record().getSeverity()))
                .count();
    }

    private static String orNormal(String severity) {
        return severity == null || severity.isEmpty() ? AlarmSeverity.NORMAL : severity;
    }
}
