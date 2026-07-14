package com.n4monitor.websocket;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.n4monitor.alarm.model.PointAlarmState;
import com.n4monitor.service.TdengineSyncService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.connection.Message;
import org.springframework.data.redis.connection.MessageListener;
import org.springframework.stereotype.Component;

/**
 * Redis Pub/Sub listener for real-time alarm records.
 * <p>
 * When Flink confirms an alarm (after the hysteresis timeout), it publishes
 * the {@link PointAlarmState} to the {@code alarm:realtime} channel.
 * This listener receives the message and writes it to TDengine immediately.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class AlarmRealtimeListener implements MessageListener {

    private final TdengineSyncService tdengineSyncService;
    private final ObjectMapper objectMapper = new ObjectMapper();

    @Override
    public void onMessage(Message message, byte[] pattern) {
        try {
            String alarmJson = new String(message.getBody());
            log.debug("Received realtime alarm from Redis: {}",
                    alarmJson.substring(0, Math.min(100, alarmJson.length())));

            PointAlarmState state = objectMapper.readValue(alarmJson, PointAlarmState.class);
            tdengineSyncService.writeRealtimeAlarm(state);
        } catch (Exception e) {
            log.error("Failed to process realtime alarm message", e);
        }
    }
}
