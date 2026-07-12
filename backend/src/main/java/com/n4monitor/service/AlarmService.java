package com.n4monitor.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.n4monitor.dto.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Alarm service that reads real-time data from Redis (alarm:latest).
 * Replaces the previous mock data implementation.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AlarmService {

    @Value("${alarm.redis.active-key:alarm:active}")
    private String activeRedisKey;

    @Value("${alarm.redis.notification-channel:alarm:notifications}")
    private String notificationChannel;

    private static final DateTimeFormatter TIME_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
    private static final DateTimeFormatter HOUR_FORMAT = DateTimeFormatter.ofPattern("HH:00");

    private final StringRedisTemplate redisTemplate;
    private final ObjectMapper objectMapper = new ObjectMapper();

    /**
     * Read all alarm states from Redis and convert to AlarmRecordDto list.
     */
    private List<AlarmRecordDto> readAllAlarms() {
        Map<Object, Object> rawData = redisTemplate.opsForHash().entries(activeRedisKey);
        if (rawData == null || rawData.isEmpty()) {
            return Collections.emptyList();
        }

        List<AlarmRecordDto> alarms = new ArrayList<>();
        for (Map.Entry<Object, Object> entry : rawData.entrySet()) {
            String json = (String) entry.getValue();
            try {
                var node = objectMapper.readTree(json);

                AlarmRecordDto dto = new AlarmRecordDto();

                // Format timestamp
                if (node.has("timestamp") && !node.get("timestamp").isNull()) {
                    long ts = node.get("timestamp").asLong();
                    LocalDateTime ldt = LocalDateTime.ofInstant(
                            Instant.ofEpochMilli(ts), ZoneId.systemDefault());
                    dto.setTime(ldt.format(TIME_FORMAT));
                } else {
                    dto.setTime("-");
                }

                dto.setLevel(getTextField(node, "level", ""));
                dto.setSeverity(getTextField(node, "severity", ""));
                dto.setSource(getTextField(node, "source", ""));
                dto.setMessage(getTextField(node, "message", ""));
                dto.setLocation(getTextField(node, "location", ""));
                dto.setPath(getTextField(node, "path", ""));
                dto.setStatus(getTextField(node, "status", "Unack"));
                dto.setAckBy(getTextField(node, "ackBy", "-"));
                dto.setDuration(getTextField(node, "duration", ""));
                dto.setStage(getTextField(node, "stage", ""));

                alarms.add(dto);
            } catch (Exception e) {
                log.warn("Failed to parse alarm JSON for key: {}", entry.getKey());
            }
        }

        // Sort by time descending (newest first)
        alarms.sort((a, b) -> b.getTime().compareTo(a.getTime()));
        return alarms;
    }

    private String getTextField(com.fasterxml.jackson.databind.JsonNode node, String field, String defaultValue) {
        if (node.has(field) && !node.get(field).isNull()) {
            String val = node.get(field).asText();
            return (val == null || val.isEmpty()) ? defaultValue : val;
        }
        return defaultValue;
    }

    /**
     * Get active alarms filtered by tab.
     * Non-alarm records (severity = Normal) are excluded.
     */
    public List<AlarmRecordDto> getActiveAlarms(String tab) {
        List<AlarmRecordDto> allAlarms = readAllAlarms().stream()
                .filter(a -> a.getLevel() != null && !"".equals(a.getLevel()))
                .collect(Collectors.toList());

        return switch (tab != null ? tab : "active") {
            case "unacknowledged" -> allAlarms.stream()
                    .filter(a -> "Unack".equals(a.getStatus())).collect(Collectors.toList());
            case "acknowledged" -> allAlarms.stream()
                    .filter(a -> "Ack".equals(a.getStatus())).collect(Collectors.toList());
            case "cleared" -> allAlarms.stream()
                    .filter(a -> "Cleared".equals(a.getStatus())).collect(Collectors.toList());
            default -> allAlarms;
        };
    }

    /**
     * Acknowledge alarms by updating their status and ackBy in Redis.
     *
     * @param paths point paths to acknowledge
     * @param ackBy user who acknowledges the alarms
     * @return number of alarms updated
     */
    public int acknowledgeAlarms(List<String> paths, String ackBy) {
        if (paths == null || paths.isEmpty()) {
            return 0;
        }

        Map<Object, Object> rawData = redisTemplate.opsForHash().entries(activeRedisKey);
        if (rawData == null || rawData.isEmpty()) {
            return 0;
        }

        Set<String> pathSet = new HashSet<>(paths);
        int updated = 0;
        List<String> ackedJsons = new ArrayList<>();

        for (Map.Entry<Object, Object> entry : rawData.entrySet()) {
            String key = (String) entry.getKey();
            String json = (String) entry.getValue();
            try {
                var node = objectMapper.readTree(json);
                String path = node.has("path") && !node.get("path").isNull()
                        ? node.get("path").asText()
                        : key;
                if (!pathSet.contains(path)) {
                    continue;
                }

                ((com.fasterxml.jackson.databind.node.ObjectNode) node)
                        .put("status", "Ack")
                        .put("ackBy", ackBy);

                String updatedJson = objectMapper.writeValueAsString(node);
                redisTemplate.opsForHash().put(activeRedisKey, key, updatedJson);
                ackedJsons.add(updatedJson);
                updated++;
            } catch (Exception e) {
                log.warn("Failed to acknowledge alarm for key: {}", key, e);
            }
        }

        // Broadcast each acked record so connected WebSocket clients update immediately.
        // A recovered+acked alarm carries stage=end; the frontend will remove it.
        for (String ackedJson : ackedJsons) {
            redisTemplate.convertAndSend(notificationChannel, ackedJson);
        }

        return updated;
    }

    /**
     * Get alarm summary counts by severity.
     */
    public AlarmSummaryDto getAlarmSummary() {
        List<AlarmRecordDto> allAlarms = readAllAlarms();

        AlarmSummaryDto dto = new AlarmSummaryDto();
        dto.setCritical((int) allAlarms.stream().filter(a -> "Critical".equals(a.getSeverity())).count());
        dto.setMajor((int) allAlarms.stream().filter(a -> "Major".equals(a.getSeverity())).count());
        dto.setMinor((int) allAlarms.stream().filter(a -> "Minor".equals(a.getSeverity())).count());
        dto.setWarning((int) allAlarms.stream().filter(a -> "Warning".equals(a.getSeverity())).count());
        return dto;
    }

    /**
     * Get alarm distribution by severity.
     */
    public AlarmSeverityChartDto getAlarmBySeverity() {
        List<AlarmRecordDto> allAlarms = readAllAlarms();
        int total = allAlarms.size();

        Map<String, Long> countBySeverity = allAlarms.stream()
                .collect(Collectors.groupingBy(AlarmRecordDto::getSeverity, Collectors.counting()));

        AlarmSeverityChartDto dto = new AlarmSeverityChartDto();
        dto.setTotal(total);

        List<AlarmSeverityChartDto.SeverityItem> items = new ArrayList<>();
        for (String severity : List.of("Critical", "Major", "Minor", "Warning")) {
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

    /**
     * Get alarm trend data (simplified - current snapshot).
     */
    public AlarmTrendChartDto getAlarmTrend() {
        List<AlarmRecordDto> allAlarms = readAllAlarms();

        // Group alarms by hour
        Map<String, Map<String, Integer>> hourlySeverity = new TreeMap<>();
        for (int h = 0; h < 24; h++) {
            hourlySeverity.put(String.format("%02d:00", h), new HashMap<>());
        }

        // Read raw data to get timestamps
        Map<Object, Object> rawData = redisTemplate.opsForHash().entries(activeRedisKey);
        if (rawData != null) {
            for (Object value : rawData.values()) {
                try {
                    var node = objectMapper.readTree((String) value);
                    if (node.has("timestamp") && !node.get("timestamp").isNull()) {
                        long ts = node.get("timestamp").asLong();
                        LocalDateTime ldt = LocalDateTime.ofInstant(
                                Instant.ofEpochMilli(ts), ZoneId.systemDefault());
                        String hourKey = ldt.format(HOUR_FORMAT);
                        String severity = node.has("severity") ? node.get("severity").asText() : "Normal";
                        hourlySeverity.computeIfAbsent(hourKey, k -> new HashMap<>())
                                .merge(severity, 1, Integer::sum);
                    }
                } catch (Exception ignored) {
                }
            }
        }

        AlarmTrendChartDto dto = new AlarmTrendChartDto();
        List<String> timeLabels = new ArrayList<>(hourlySeverity.keySet());
        dto.setTimeLabels(timeLabels);

        List<AlarmTrendChartDto.TrendSeries> series = new ArrayList<>();
        for (String severity : List.of("Critical", "Major", "Minor", "Warning")) {
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

    /**
     * Get alarm distribution by hour.
     */
    public AlarmHourChartDto getAlarmByHour() {
        Map<Object, Object> rawData = redisTemplate.opsForHash().entries(activeRedisKey);

        // Count by hour
        Map<String, Integer> hourCount = new TreeMap<>();
        for (int h = 0; h < 24; h++) {
            hourCount.put(String.format("%02d:00", h), 0);
        }

        if (rawData != null) {
            for (Object value : rawData.values()) {
                try {
                    var node = objectMapper.readTree((String) value);
                    if (node.has("timestamp") && !node.get("timestamp").isNull()) {
                        long ts = node.get("timestamp").asLong();
                        LocalDateTime ldt = LocalDateTime.ofInstant(
                                Instant.ofEpochMilli(ts), ZoneId.systemDefault());
                        String hourKey = ldt.format(HOUR_FORMAT);
                        hourCount.merge(hourKey, 1, Integer::sum);
                    }
                } catch (Exception ignored) {
                }
            }
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

    /**
     * Get alarm distribution by system (using point name prefix).
     */
    public AlarmSystemChartDto getAlarmBySystem() {
        Map<Object, Object> rawData = redisTemplate.opsForHash().entries(activeRedisKey);
        Map<String, Integer> systemCount = new HashMap<>();

        if (rawData != null) {
            for (Object key : rawData.keySet()) {
                String path = (String) key;
                // Extract system name from path (e.g., "Drivers/ModbusTcpNetwork/LVP_A4/..." -> "LVP_A4")
                String[] parts = path.split("/");
                String system = parts.length >= 3 ? parts[2] : "Unknown";
                systemCount.merge(system, 1, Integer::sum);
            }
        }

        int total = systemCount.values().stream().mapToInt(Integer::intValue).sum();

        AlarmSystemChartDto dto = new AlarmSystemChartDto();
        List<AlarmSystemChartDto.SystemItem> items = systemCount.entrySet().stream()
                .sorted(Map.Entry.<String, Integer>comparingByValue().reversed())
                .limit(5)
                .map(e -> {
                    AlarmSystemChartDto.SystemItem item = new AlarmSystemChartDto.SystemItem();
                    item.setName(e.getKey());
                    item.setValue(e.getValue());
                    item.setPercentage(total > 0 ? Math.round(e.getValue() * 1000.0 / total) / 10.0 : 0);
                    return item;
                })
                .collect(Collectors.toList());
        dto.setItems(items);
        return dto;
    }

    /**
     * Get top alarm sources.
     */
    public AlarmSourceChartDto getAlarmTopSources() {
        Map<Object, Object> rawData = redisTemplate.opsForHash().entries(activeRedisKey);
        Map<String, Integer> sourceCount = new HashMap<>();

        if (rawData != null) {
            for (Object value : rawData.values()) {
                try {
                    var node = objectMapper.readTree((String) value);
                    String name = node.has("name") ? node.get("name").asText() : "Unknown";
                    String severity = node.has("severity") ? node.get("severity").asText() : "Normal";
                    // Only count non-normal alarms
                    if (!"Normal".equals(severity)) {
                        sourceCount.merge(name, 1, Integer::sum);
                    }
                } catch (Exception ignored) {
                }
            }
        }

        AlarmSourceChartDto dto = new AlarmSourceChartDto();
        List<AlarmSourceChartDto.SourceItem> items = sourceCount.entrySet().stream()
                .sorted(Map.Entry.<String, Integer>comparingByValue().reversed())
                .limit(5)
                .map(e -> {
                    AlarmSourceChartDto.SourceItem item = new AlarmSourceChartDto.SourceItem();
                    item.setName(e.getKey());
                    item.setCount(e.getValue());
                    return item;
                })
                .collect(Collectors.toList());
        dto.setItems(items);
        return dto;
    }
}
