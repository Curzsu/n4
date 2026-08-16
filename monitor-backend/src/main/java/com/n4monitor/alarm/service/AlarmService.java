package com.n4monitor.alarm.service;

import com.n4monitor.tdengine.TdengineSyncService;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.n4monitor.alarm.model.PointAlarmState;
import com.n4monitor.alarm.dto.*;
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

    @Value("${alarm.redis.active-key}")
    private String activeRedisKey;

    @Value("${alarm.redis.latest-key}")
    private String latestRedisKey;

    @Value("${alarm.redis.notification-channel}")
    private String notificationChannel;

    private static final DateTimeFormatter TIME_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
    private static final DateTimeFormatter HOUR_FORMAT = DateTimeFormatter.ofPattern("HH:00");

    private final StringRedisTemplate redisTemplate;
    private final TdengineSyncService tdengineSyncService;
    private final ObjectMapper objectMapper;

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
                dto.setPath(getTextField(node, "mqttPath", ""));
                dto.setStatus(getTextField(node, "status", "Unack"));
                dto.setAckBy(getTextField(node, "ackBy", "-"));
                dto.setDuration(getTextField(node, "duration", ""));
                dto.setStage(getTextField(node, "stage", ""));
                dto.setStageMessage(getTextField(node, "stageMessage", ""));

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
     * Get all alarm history records from TDengine (newest first).
     * Returns every record stored in alarm_realtime regardless of stage/status,
     * mirroring the Active Alarms default time ordering.
     */
    public List<AlarmRecordDto> getClearedAlarms() {
        return tdengineSyncService.queryClearedAlarms();
    }

    /**
     * Acknowledge alarms by updating their status and ackBy in Redis.
     * <p>
     * Timestamp rules:
     * <ul>
     *   <li>alarm:active + notification broadcast: keep the alarm's historical start
     *       timestamp (the record already stores alarmStartTime as its timestamp).</li>
     *   <li>TDengine realtime: write with the ack moment (now) as the data timestamp.</li>
     *   <li>alarm:latest: update only if the latest entry is still in an alarm state
     *       (level non-empty). If the point has already recovered (level empty),
     *       the ack targets the previous alarm record, so latest must NOT be touched.
     *       When updated, latest uses the ack moment (now) as its timestamp.</li>
     * </ul>
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
        long ackTimestamp = System.currentTimeMillis();

        for (Map.Entry<Object, Object> entry : rawData.entrySet()) {
            String key = (String) entry.getKey();
            String json = (String) entry.getValue();
            try {
                var node = objectMapper.readTree(json);
                String path = node.has("mqttPath") && !node.get("mqttPath").isNull()
                        ? node.get("mqttPath").asText()
                        : key;
                if (!pathSet.contains(path)) {
                    continue;
                }

                String stage = node.has("stage") ? node.get("stage").asText() : "";

                boolean recovered = "end".equals(stage);

                // Duration handling differs by stage:
                //  - stage=end (recovered): the alarm already ended; Flink froze the final
                //    duration at recovery time. Keep that value as-is, do NOT recompute.
                //  - stage=start/continue (still alarming): recompute the live duration as
                //    (ackMoment - alarmStart) / 1000 so the displayed duration is current.
                String ackDuration;
                long ackDurationSeconds = 0L;
                if (recovered) {
                    // Preserve the frozen duration written at recovery time.
                    ackDuration = node.has("duration") && !node.get("duration").isNull()
                            ? node.get("duration").asText() : "0";
                    try {
                        ackDurationSeconds = Long.parseLong(ackDuration);
                    } catch (NumberFormatException ignored) {
                        ackDurationSeconds = 0L;
                    }
                } else {
                    long alarmStartTs = 0L;
                    if (node.has("alarmStartTime") && !node.get("alarmStartTime").isNull()) {
                        alarmStartTs = node.get("alarmStartTime").asLong();
                    } else if (node.has("timestamp") && !node.get("timestamp").isNull()) {
                        alarmStartTs = node.get("timestamp").asLong();
                    }
                    ackDurationSeconds = alarmStartTs > 0
                            ? Math.max(0, (ackTimestamp - alarmStartTs) / 1000)
                            : 0L;
                    ackDuration = String.valueOf(ackDurationSeconds);
                }

                // Update status/ackBy on the active record. For ongoing alarms the duration
                // is refreshed; for recovered alarms it is left at the frozen value. The
                // timestamp is left untouched so it keeps the alarm's historical start time.
                // transition=Ack marks this record as a user-acknowledgement event.
                ((com.fasterxml.jackson.databind.node.ObjectNode) node)
                        .put("status", "Ack")
                        .put("ackBy", ackBy)
                        .put("stageMessage", "Ack");
                if (!recovered) {
                    ((com.fasterxml.jackson.databind.node.ObjectNode) node)
                            .put("duration", ackDuration);
                }
                String activeJson = objectMapper.writeValueAsString(node);

                if (recovered) {
                    // Already recovered (stage=end): mark as Ack and remove from Redis
                    // so a page refresh does not resurrect it. The cleared signal is
                    // still broadcast so the frontend removes the row immediately.
                    redisTemplate.opsForHash().delete(activeRedisKey, key);
                } else {
                    // Still alarming (stage=start/continue): update status in place,
                    // the record stays in alarm:active for continued display.
                    redisTemplate.opsForHash().put(activeRedisKey, key, activeJson);
                }
                // notification broadcast uses the historical timestamp (activeJson keeps it)
                ackedJsons.add(activeJson);

                // Persist the ack event to TDengine with the ack moment as the timestamp.
                try {
                    PointAlarmState ackedState = objectMapper.readValue(activeJson, PointAlarmState.class);
                    ackedState.setTimestamp(ackTimestamp);
                    tdengineSyncService.writeRealtimeAlarm(ackedState);
                } catch (Exception te) {
                    log.warn("Failed to persist acked alarm to TDengine for key: {}", key, te);
                }

                // Update alarm:latest only for ongoing alarms (stage=start/continue).
                // A recovered alarm (stage=end) has already ended; its latest entry reflects
                // the recovered (normal) state, so the ack must NOT touch latest at all.
                if (!recovered) {
                    updateLatestOnAck(path, ackBy, ackTimestamp, ackDurationSeconds);
                }

                updated++;
            } catch (Exception e) {
                log.warn("Failed to acknowledge alarm for key: {}", key, e);
            }
        }

        // Broadcast each acked record so connected WebSocket clients update immediately.
        // A recovered+acked alarm carries stage=end + status=Ack; the frontend removes it.
        for (String ackedJson : ackedJsons) {
            redisTemplate.convertAndSend(notificationChannel, ackedJson);
        }

        return updated;
    }

    /**
     * Update alarm:latest for an acknowledged ongoing alarm (stage=start/continue).
     * Only called when the alarm is still active. The latest record's status/ackBy are
     * set to Ack/ackBy, its timestamp to the ack moment, and its duration to the
     * recomputed value (ack moment - alarm start).
     * <p>
     * Recovered alarms (stage=end) never reach this method: their latest entry already
     * reflects the normal state, so the ack must not modify it.
     */
    private void updateLatestOnAck(String path, String ackBy, long ackTimestamp, long ackDurationSeconds) {
        try {
            String latestJson = (String) redisTemplate.opsForHash().get(latestRedisKey, path);
            if (latestJson == null || latestJson.isBlank()) {
                return;
            }
            var latestNode = (com.fasterxml.jackson.databind.node.ObjectNode) objectMapper.readTree(latestJson);
            String level = latestNode.has("level") ? latestNode.get("level").asText() : "";
            // Only update when the point is still alarming. If it has recovered (level
            // empty), this ack is for the previous alarm and latest must not change.
            if (level == null || level.isEmpty()) {
                return;
            }
            latestNode.put("status", "Ack");
            latestNode.put("ackBy", ackBy);
            latestNode.put("timestamp", ackTimestamp);
            latestNode.put("duration", String.valueOf(ackDurationSeconds));
            redisTemplate.opsForHash().put(latestRedisKey, path, objectMapper.writeValueAsString(latestNode));
        } catch (Exception e) {
            log.warn("Failed to update latest on ack for path: {}", path, e);
        }
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
                    // Use the path-derived `source` (not the removed `name` field) as the
                    // bucket key so top-sources ranking stays meaningful without name.
                    String src = node.has("source") && !node.get("source").isNull()
                            ? node.get("source").asText() : "Unknown";
                    String severity = node.has("severity") ? node.get("severity").asText() : "Normal";
                    // Only count non-normal alarms
                    if (!"Normal".equals(severity)) {
                        sourceCount.merge(src, 1, Integer::sum);
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
