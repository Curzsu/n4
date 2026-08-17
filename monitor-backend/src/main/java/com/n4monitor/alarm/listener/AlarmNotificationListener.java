package com.n4monitor.alarm.listener;

import com.n4monitor.alarm.service.ActiveAlarmStore;
import com.n4monitor.alarm.service.AlarmAggregator;
import com.n4monitor.websocket.AlarmWebSocketHandler;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.connection.Message;
import org.springframework.data.redis.connection.MessageListener;
import org.springframework.stereotype.Component;

/**
 * Redis Pub/Sub listener for alarm notifications.
 * When Flink publishes an alarm, this listener:
 * 1. Parses the triggering alarm record
 * 2. Reads aggregated data from the shared {@link ActiveAlarmStore} snapshot
 * 3. Broadcasts the complete update via WebSocket
 * <p>
 * Every event is still broadcast individually (the frontend updates alarm rows
 * from the per-event {@code alarm} field), but the expensive part - pulling and
 * aggregating the whole alarm:active hash - now goes through the shared
 * short-TTL snapshot, so an alarm burst no longer triggers one full pull +
 * re-aggregation per event.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class AlarmNotificationListener implements MessageListener {

    private final ActiveAlarmStore activeAlarmStore;
    private final AlarmWebSocketHandler webSocketHandler;
    private final ObjectMapper objectMapper;

    @Override
    public void onMessage(Message message, byte[] pattern) {
        String channel = pattern != null ? new String(pattern) : "unknown";
        log.info("Received Redis pub/sub message on channel: {}", channel);
        try {
            // The message body is the PointAlarmState JSON that triggered this notification
            String alarmJson = new String(message.getBody());
            log.debug("Alarm notification body: {}", alarmJson.substring(0, Math.min(100, alarmJson.length())));

            // Parse the triggering alarm early so its identity can be logged at each step
            var triggerNode = objectMapper.readTree(alarmJson);
            String triggerPath = triggerNode.has("mqttPath") ? triggerNode.get("mqttPath").asText() : "?";
            String triggerStage = triggerNode.has("stage") ? triggerNode.get("stage").asText() : "?";
            log.info("Processing alarm notification: path={}, stage={}", triggerPath, triggerStage);

            // Build aggregated data from the shared snapshot (no per-event full pull)
            ObjectNode root = objectMapper.createObjectNode();
            root.put("type", "alarm_update");

            // The individual alarm that triggered this update. This must be sent
            // even when alarm:active is empty: a "cleared" signal (stage=end +
            // status=Ack) is published AFTER the record was removed from Redis,
            // so the active hash may be empty at this point.
            root.set("alarm", triggerNode);

            ObjectNode summary = objectMapper.createObjectNode();
            ObjectNode severityData = objectMapper.createObjectNode();
            ObjectNode hourlyData = objectMapper.createObjectNode();
            AlarmAggregator.writeWebSocketAggregates(
                    objectMapper, activeAlarmStore.snapshot(), summary, severityData, hourlyData);

            root.set("summary", summary);
            root.set("severityData", severityData);
            root.set("hourlyData", hourlyData);

            // Broadcast to all WebSocket clients
            String json = objectMapper.writeValueAsString(root);
            int clientCount = webSocketHandler.getConnectedCount();
            webSocketHandler.broadcast(json);
            log.info("Alarm notification dispatched: path={}, stage={}, websocketClients={}",
                    triggerPath, triggerStage, clientCount);

        } catch (Exception e) {
            log.error("Failed to process alarm notification", e);
        }
    }
}
