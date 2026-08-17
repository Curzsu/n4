package com.n4monitor.alarm.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.n4monitor.alarm.dto.AlarmRecordDto;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Map;

/**
 * Read-through snapshot cache for the alarm:active hash.
 * <p>
 * Dashboard endpoints and the WebSocket notification listener used to pull and
 * JSON-parse the whole hash independently (a dashboard refresh hit Redis 4-8
 * times; every alarm event pulled it again). This store parses the hash once
 * and serves a short-lived snapshot (default 800ms) to all consumers, so the
 * read cost collapses to at most one full pull per TTL window.
 * <p>
 * The cache is READ-side only: write paths (e.g. alarm acknowledgement) must
 * read fresh data themselves and call {@link #invalidate()} afterwards.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ActiveAlarmStore {

    private static final DateTimeFormatter TIME_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    /** Snapshot lifetime. Short enough that charts stay near-live, long enough
     *  to absorb a dashboard refresh (8 parallel endpoints) and alarm bursts. */
    private static final long SNAPSHOT_TTL_MS = 800;

    @Value("${alarm.redis.active-key}")
    private String activeRedisKey;

    private final StringRedisTemplate redisTemplate;
    private final ObjectMapper objectMapper;

    private record Snapshot(long loadedAt, List<ActiveAlarm> alarms) {
    }

    private volatile Snapshot cached;
    private final Object loadLock = new Object();

    /** Return the (possibly cached) parsed snapshot, sorted newest first. */
    public List<ActiveAlarm> snapshot() {
        Snapshot snap = cached;
        if (snap != null && System.currentTimeMillis() - snap.loadedAt() < SNAPSHOT_TTL_MS) {
            return snap.alarms();
        }
        synchronized (loadLock) {
            snap = cached;
            if (snap != null && System.currentTimeMillis() - snap.loadedAt() < SNAPSHOT_TTL_MS) {
                return snap.alarms();
            }
            List<ActiveAlarm> loaded = load();
            cached = new Snapshot(System.currentTimeMillis(), loaded);
            return loaded;
        }
    }

    /** Drop the cached snapshot so the next read reflects recent writes. */
    public void invalidate() {
        cached = null;
    }

    private List<ActiveAlarm> load() {
        Map<Object, Object> rawData = redisTemplate.opsForHash().entries(activeRedisKey);
        if (rawData == null || rawData.isEmpty()) {
            return Collections.emptyList();
        }

        List<ActiveAlarm> alarms = new ArrayList<>(rawData.size());
        for (Map.Entry<Object, Object> entry : rawData.entrySet()) {
            String json = (String) entry.getValue();
            try {
                JsonNode node = objectMapper.readTree(json);

                AlarmRecordDto dto = new AlarmRecordDto();
                Long epoch = null;
                if (node.has("timestamp") && !node.get("timestamp").isNull()) {
                    epoch = node.get("timestamp").asLong();
                    LocalDateTime ldt = LocalDateTime.ofInstant(
                            Instant.ofEpochMilli(epoch), ZoneId.systemDefault());
                    dto.setTime(ldt.format(TIME_FORMAT));
                } else {
                    dto.setTime("-");
                }

                dto.setLevel(text(node, "level", ""));
                dto.setSeverity(text(node, "severity", ""));
                dto.setSource(text(node, "source", ""));
                dto.setMessage(text(node, "message", ""));
                dto.setLocation(text(node, "location", ""));
                dto.setPath(text(node, "mqttPath", ""));
                dto.setStatus(text(node, "status", "Unack"));
                dto.setAckBy(text(node, "ackBy", "-"));
                dto.setDuration(text(node, "duration", ""));
                dto.setStage(text(node, "stage", ""));
                dto.setStageMessage(text(node, "stageMessage", ""));

                alarms.add(new ActiveAlarm(dto, epoch, (String) entry.getKey()));
            } catch (Exception e) {
                log.warn("Failed to parse alarm JSON for key: {}", entry.getKey());
            }
        }

        // Sort by the raw epoch so records without a timestamp ("-" display)
        // land deterministically at the end instead of confusing string order.
        alarms.sort(Comparator.comparing(ActiveAlarm::epochMillis,
                Comparator.nullsLast(Comparator.reverseOrder())));
        return alarms;
    }

    private String text(JsonNode node, String field, String defaultValue) {
        if (node.has(field) && !node.get(field).isNull()) {
            String val = node.get(field).asText();
            return (val == null || val.isEmpty()) ? defaultValue : val;
        }
        return defaultValue;
    }
}
