package com.n4monitor.alarm.stream;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.n4monitor.alarm.model.PointAlarmState;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.connection.RedisStreamCommands;
import org.springframework.data.redis.connection.stream.MapRecord;
import org.springframework.data.redis.connection.stream.StreamRecords;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.util.Map;

/**
 * Backend-side producer for the two alarm persistence Streams (the Flink job
 * is the primary producer; this component covers records originating in the
 * backend itself - currently alarm acknowledgements). XADD with approximate
 * MAXLEN keeps the stream bounded even if the backend is the surviving side
 * of an outage.
 */
@Slf4j
@Component
public class AlarmStreamPublisher {

    private final StringRedisTemplate redisTemplate;
    private final ObjectMapper objectMapper;
    private final String realtimeStream;
    private final String persistenceStream;
    private final long realtimeStreamMaxlen;
    private final long persistenceStreamMaxlen;

    public AlarmStreamPublisher(StringRedisTemplate redisTemplate,
                                ObjectMapper objectMapper,
                                @Value("${alarm.redis.realtime-stream}") String realtimeStream,
                                @Value("${alarm.redis.persistence-stream}") String persistenceStream,
                                @Value("${alarm.redis.realtime-stream-maxlen:100000}") long realtimeStreamMaxlen,
                                @Value("${alarm.redis.persistence-stream-maxlen:1000000}") long persistenceStreamMaxlen) {
        this.redisTemplate = redisTemplate;
        this.objectMapper = objectMapper;
        this.realtimeStream = realtimeStream;
        this.persistenceStream = persistenceStream;
        this.realtimeStreamMaxlen = realtimeStreamMaxlen;
        this.persistenceStreamMaxlen = persistenceStreamMaxlen;
    }

    /** Append an alarm lifecycle record (alarm_realtime audit path). */
    public void publishRealtime(PointAlarmState state) {
        xadd(realtimeStream, realtimeStreamMaxlen, state);
    }

    /** Append a full-detail frame (alarm_event audit path). */
    public void publishPersistence(PointAlarmState state) {
        xadd(persistenceStream, persistenceStreamMaxlen, state);
    }

    private void xadd(String stream, long maxlen, PointAlarmState state) {
        try {
            String json = objectMapper.writeValueAsString(state);
            MapRecord<String, String, String> record = StreamRecords.newRecord()
                    .in(stream)
                    .ofMap(Map.of("data", json));
            redisTemplate.opsForStream().add(record,
                    RedisStreamCommands.XAddOptions.maxlen(maxlen).approximateTrimming(true));
        } catch (Exception e) {
            log.error("Failed to XADD into stream '{}' for path={}: {}",
                    stream, state.getMqttPath(), e.getMessage(), e);
        }
    }
}
