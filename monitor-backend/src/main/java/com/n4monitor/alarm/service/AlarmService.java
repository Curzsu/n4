package com.n4monitor.alarm.service;

import com.n4monitor.tdengine.TdengineSyncService;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.n4monitor.alarm.dto.AlarmHourChartDto;
import com.n4monitor.alarm.dto.AlarmRecordDto;
import com.n4monitor.alarm.dto.AlarmSeverityChartDto;
import com.n4monitor.alarm.dto.AlarmSourceChartDto;
import com.n4monitor.alarm.dto.AlarmSummaryDto;
import com.n4monitor.alarm.dto.AlarmSystemChartDto;
import com.n4monitor.alarm.dto.AlarmTrendChartDto;
import com.n4monitor.alarm.model.PointAlarmState;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.stream.Collectors;

/**
 * Alarm service: reads current alarm state through {@link ActiveAlarmStore}
 * (one shared short-TTL snapshot instead of a full Redis pull per endpoint)
 * and delegates all aggregation math to {@link AlarmAggregator}.
 * Acknowledgement is a read-modify-write path and reads fresh data itself.
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

    private final StringRedisTemplate redisTemplate;
    private final TdengineSyncService tdengineSyncService;
    private final ObjectMapper objectMapper;
    private final ActiveAlarmStore activeAlarmStore;

    /**
     * Get active alarms filtered by tab.
     * Non-alarm records (empty level) are excluded.
     */
    public List<AlarmRecordDto> getActiveAlarms(String tab) {
        List<AlarmRecordDto> allAlarms = activeAlarmStore.snapshot().stream()
                .map(ActiveAlarm::record)
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

        activeAlarmStore.invalidate();
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

    // ── Read-side aggregations (shared snapshot + single implementation) ────

    /** Get alarm summary counts by severity. */
    public AlarmSummaryDto getAlarmSummary() {
        return AlarmAggregator.summary(activeAlarmStore.snapshot());
    }

    /** Get alarm distribution by severity. */
    public AlarmSeverityChartDto getAlarmBySeverity() {
        return AlarmAggregator.bySeverity(activeAlarmStore.snapshot());
    }

    /** Get alarm trend data (24h severity buckets, current snapshot). */
    public AlarmTrendChartDto getAlarmTrend() {
        return AlarmAggregator.trend(activeAlarmStore.snapshot());
    }

    /** Get alarm distribution by hour. */
    public AlarmHourChartDto getAlarmByHour() {
        return AlarmAggregator.byHour(activeAlarmStore.snapshot());
    }

    /** Get alarm distribution by system (path-derived). */
    public AlarmSystemChartDto getAlarmBySystem() {
        return AlarmAggregator.bySystem(activeAlarmStore.snapshot());
    }

    /** Get top alarm sources. */
    public AlarmSourceChartDto getAlarmTopSources() {
        return AlarmAggregator.topSources(activeAlarmStore.snapshot());
    }
}
