package com.n4monitor.alarm.listener;

import com.n4monitor.alarm.model.PointAlarmState;
import com.n4monitor.tdengine.TdengineSyncService;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.connection.Message;
import org.springframework.data.redis.connection.MessageListener;
import org.springframework.stereotype.Component;

/**
 * Redis Pub/Sub listener for full-detail point persistence.
 * <p>
 * Flink publishes every processed data frame (each point's latest reading,
 * alarm or not) to the {@code alarm:persist} channel. This listener receives
 * the frame and hands it to {@link TdengineSyncService}, which buffers frames
 * and flushes them to the TDengine {@code alarm_event} supertable in batches,
 * so the per-frame write rate does not translate into one JDBC round-trip
 * per frame.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class AlarmPersistenceListener implements MessageListener {

    private final TdengineSyncService tdengineSyncService;
    private final ObjectMapper objectMapper;

    @Override
    public void onMessage(Message message, byte[] pattern) {
        String channel = pattern != null ? new String(pattern) : "unknown";
        try {
            PointAlarmState state = objectMapper.readValue(message.getBody(), PointAlarmState.class);
            tdengineSyncService.enqueuePersistence(state);
            log.debug("Persist event enqueued: path={}, stage={}, value={}",
                    state.getMqttPath(), state.getStage(), state.getValue());
        } catch (Exception e) {
            log.error("Failed to process persistence message on channel '{}'", channel, e);
        }
    }
}
