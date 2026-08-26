package com.n4monitor.alarm.listener;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.n4monitor.alarm.model.PointAlarmState;
import com.n4monitor.tdengine.TdengineSyncService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.connection.Message;
import org.springframework.data.redis.connection.MessageListener;
import org.springframework.stereotype.Component;

/**
 * Redis Pub/Sub listener for real-time alarm records.
 * <p>
 * When Flink confirms an alarm (after the time_delay timeout), it publishes
 * the {@link PointAlarmState} to the {@code alarm:realtime} channel.
 * This listener receives the message and writes it to TDengine immediately.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class AlarmRealtimeListener implements MessageListener {

    private final TdengineSyncService tdengineSyncService;
    private final ObjectMapper objectMapper;

    @Override
    public void onMessage(Message message, byte[] pattern) {
        String channel = pattern != null ? new String(pattern) : "unknown";
        log.info("Received Redis pub/sub message on channel: {}", channel);
        try {
            String alarmJson = new String(message.getBody());

            PointAlarmState state = objectMapper.readValue(alarmJson, PointAlarmState.class);
            log.info("Persisting realtime alarm to TDengine: path={}, stage={}, level={}",
                    state.getPath(), state.getStage(), state.getLevel());
            tdengineSyncService.writeRealtimeAlarm(state);
        } catch (Exception e) {
            log.error("Failed to process realtime alarm message", e);
        }
    }
}
