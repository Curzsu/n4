package com.n4monitor.websocket;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.connection.Message;
import org.springframework.data.redis.connection.MessageListener;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.Map;

/**
 * Redis Pub/Sub listener for alarm notifications.
 * When Flink publishes a new alarm, this listener:
 * 1. Reads all current alarm states from Redis
 * 2. Computes aggregated data (summary, severity, hourly)
 * 3. Broadcasts the complete update via WebSocket
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class AlarmNotificationListener implements MessageListener {

    @Value("${alarm.redis.active-key}")
    private String activeRedisKey;

    private static final DateTimeFormatter HOUR_FORMAT = DateTimeFormatter.ofPattern("HH:00");

    private final StringRedisTemplate redisTemplate;
    private final AlarmWebSocketHandler webSocketHandler;
    private final ObjectMapper objectMapper = new ObjectMapper();

    @Override
    public void onMessage(Message message, byte[] pattern) {
        log.debug("Received Redis pub/sub message on channel: {}", new String(pattern));
        try {
            // The message body is the PointAlarmState JSON that triggered this notification
            String alarmJson = new String(message.getBody());
            log.debug("Alarm notification body: {}", alarmJson.substring(0, Math.min(100, alarmJson.length())));

            // Build aggregated data
            ObjectNode root = objectMapper.createObjectNode();
            root.put("type", "alarm_update");

            // The individual alarm that triggered this update. This must be sent
            // even when alarm:active is empty: a "cleared" signal (stage=end +
            // status=Ack) is published AFTER the record was removed from Redis,
            // so the active hash may be empty at this point.
            root.set("alarm", objectMapper.readTree(alarmJson));

            // Read all current alarm states from Redis (active alarm records)
            Map<Object, Object> allAlarms = redisTemplate.opsForHash().entries(activeRedisKey);

            ObjectNode summary = objectMapper.createObjectNode();
            ObjectNode severityData = objectMapper.createObjectNode();
            ObjectNode hourlyData = objectMapper.createObjectNode();

            if (allAlarms != null && !allAlarms.isEmpty()) {
                computeAggregations(allAlarms, summary, severityData, hourlyData);
            }

            root.set("summary", summary);
            root.set("severityData", severityData);
            root.set("hourlyData", hourlyData);

            // Broadcast to all WebSocket clients
            String json = objectMapper.writeValueAsString(root);
            webSocketHandler.broadcast(json);

        } catch (Exception e) {
            log.error("Failed to process alarm notification", e);
        }
    }

    /**
     * Compute aggregated alarm data from all current alarm states.
     */
    private void computeAggregations(Map<Object, Object> allAlarms,
                                      ObjectNode summary,
                                      ObjectNode severityData,
                                      ObjectNode hourlyData) {
        int critical = 0, major = 0, minor = 0, warning = 0, normal = 0;
        Map<String, Integer> severityCount = new HashMap<>();
        Map<String, Integer> hourCount = new HashMap<>();

        // Initialize hour buckets
        for (int h = 0; h < 24; h++) {
            hourCount.put(String.format("%02d:00", h), 0);
        }

        for (Map.Entry<Object, Object> entry : allAlarms.entrySet()) {
            String json = (String) entry.getValue();
            try {
                var node = objectMapper.readTree(json);

                // Count by severity
                String severity = node.has("severity") ? node.get("severity").asText() : "Normal";
                switch (severity) {
                    case "Critical" -> critical++;
                    case "Major" -> major++;
                    case "Minor" -> minor++;
                    case "Warning" -> warning++;
                    default -> normal++;
                }
                severityCount.merge(severity, 1, Integer::sum);

                // Count by hour
                if (node.has("timestamp") && !node.get("timestamp").isNull()) {
                    long ts = node.get("timestamp").asLong();
                    LocalDateTime ldt = LocalDateTime.ofInstant(
                            Instant.ofEpochMilli(ts), ZoneId.systemDefault());
                    String hourKey = ldt.format(HOUR_FORMAT);
                    hourCount.merge(hourKey, 1, Integer::sum);
                }
            } catch (Exception ignored) {
                // Skip malformed entries
            }
        }

        // Build summary
        summary.put("critical", critical);
        summary.put("major", major);
        summary.put("minor", minor);
        summary.put("warning", warning);

        // Build severity distribution
        int total = critical + major + minor + warning + normal;
        severityData.put("total", total);
        ArrayNode severityItems = objectMapper.createArrayNode();
        addSeverityItem(severityItems, "Critical", critical, total);
        addSeverityItem(severityItems, "Major", major, total);
        addSeverityItem(severityItems, "Minor", minor, total);
        addSeverityItem(severityItems, "Warning", warning, total);
        severityData.set("items", severityItems);

        // Build hourly distribution
        ArrayNode hours = objectMapper.createArrayNode();
        ArrayNode counts = objectMapper.createArrayNode();
        for (int h = 0; h < 24; h += 4) {
            String hourKey = String.format("%02d:00", h);
            hours.add(hourKey);
            counts.add(hourCount.getOrDefault(hourKey, 0));
        }
        hourlyData.set("hours", hours);
        hourlyData.set("counts", counts);
    }

    private void addSeverityItem(ArrayNode items, String name, int value, int total) {
        ObjectNode item = objectMapper.createObjectNode();
        item.put("name", name);
        item.put("value", value);
        item.put("percentage", total > 0 ? Math.round(value * 1000.0 / total) / 10.0 : 0);
        items.add(item);
    }
}
