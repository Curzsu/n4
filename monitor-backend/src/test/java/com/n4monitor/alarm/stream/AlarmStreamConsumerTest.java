package com.n4monitor.alarm.stream;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.n4monitor.alarm.model.PointAlarmState;
import com.n4monitor.tdengine.TdengineSyncService;
import io.lettuce.core.RedisCommandExecutionException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.RedisSystemException;
import org.springframework.data.redis.connection.stream.MapRecord;
import org.springframework.data.redis.connection.stream.ReadOffset;
import org.springframework.data.redis.connection.stream.StreamRecords;
import org.springframework.data.redis.core.StreamOperations;
import org.springframework.data.redis.core.StringRedisTemplate;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * The stream consumer is the audit guarantee: a record may only be
 * acknowledged AFTER its TDengine write succeeded, a failed write must leave
 * the batch pending (retried), and an unparseable record is acknowledged and
 * skipped (payload preserved in the error log) instead of blocking the lane
 * forever. These tests pin that acknowledgement contract.
 */
class AlarmStreamConsumerTest {

    private static final String STREAM = "alarm:stream:realtime";
    private static final String GROUP = "n4-backend";

    private final StringRedisTemplate redisTemplate = mock(StringRedisTemplate.class);
    private final TdengineSyncService tdengine = mock(TdengineSyncService.class);
    @SuppressWarnings("unchecked")
    private final StreamOperations<String, Object, Object> streamOps = mock(StreamOperations.class);
    private final ObjectMapper objectMapper = new ObjectMapper().findAndRegisterModules();

    private AlarmStreamConsumer consumer;

    @BeforeEach
    void setUp() {
        when(redisTemplate.<Object, Object>opsForStream()).thenReturn(streamOps);
        consumer = new AlarmStreamConsumer(redisTemplate, tdengine, objectMapper,
                STREAM, "alarm:stream:persist", GROUP, GROUP, 500L, 500);
    }

    @Test
    void realtimeRecordIsAckedOnlyAfterSuccessfulWrite() {
        when(tdengine.writeRealtimeAlarm(any(PointAlarmState.class))).thenReturn(true);

        int handled = consumer.handleRealtime(STREAM, List.of(record("1-1", stateJson("/p1"))));

        assertThat(handled).isEqualTo(1);
        verify(streamOps).acknowledge(eq(STREAM), eq(GROUP), eq("1-1"));
    }

    @Test
    void realtimeFailedWriteLeavesRecordUnacked() {
        when(tdengine.writeRealtimeAlarm(any(PointAlarmState.class))).thenReturn(false);

        int handled = consumer.handleRealtime(STREAM, List.of(record("2-1", stateJson("/p1"))));

        assertThat(handled).isEqualTo(-1);
        verify(streamOps, never()).acknowledge(eq(STREAM), eq(GROUP), eq("2-1"));
    }

    @Test
    void realtimeUnparseableRecordIsAckedAndSkippedWithoutWrite() {
        int handled = consumer.handleRealtime(STREAM, List.of(record("3-1", "{not-json")));

        assertThat(handled).isEqualTo(1);
        verify(tdengine, never()).writeRealtimeAlarm(any());
        verify(streamOps).acknowledge(eq(STREAM), eq(GROUP), eq("3-1"));
    }

    @Test
    void persistBatchIsAckedOnlyWhenEveryRowIsStored() {
        when(tdengine.writeEventFrames(anyList())).thenReturn(true);

        int handled = consumer.handlePersist(STREAM,
                List.of(record("4-1", stateJson("/p1")), record("4-2", stateJson("/p2"))));

        assertThat(handled).isEqualTo(2);
        verify(streamOps).acknowledge(eq(STREAM), eq(GROUP), eq("4-1"));
        verify(streamOps).acknowledge(eq(STREAM), eq(GROUP), eq("4-2"));
    }

    @Test
    void persistFailedBatchKeepsEveryRecordPending() {
        when(tdengine.writeEventFrames(anyList())).thenReturn(false);

        int handled = consumer.handlePersist(STREAM,
                List.of(record("5-1", stateJson("/p1")), record("5-2", stateJson("/p2"))));

        assertThat(handled).isEqualTo(-1);
        verify(streamOps, never()).acknowledge(eq(STREAM), eq(GROUP), any(String.class));
    }

    @Test
    void persistPoisonRecordIsIsolatedFromValidOnes() {
        when(tdengine.writeEventFrames(anyList())).thenReturn(true);

        int handled = consumer.handlePersist(STREAM,
                List.of(record("6-1", "{broken"), record("6-2", stateJson("/p2"))));

        // The poison record is acked immediately; the valid one is written and
        // acked through the normal batch path.
        assertThat(handled).isEqualTo(2);
        verify(streamOps).acknowledge(eq(STREAM), eq(GROUP), eq("6-1"));
        verify(tdengine, never()).writeRealtimeAlarm(any());
        verify(streamOps).acknowledge(eq(STREAM), eq(GROUP), eq("6-2"));
    }

    @Test
    void ensureGroupAcceptsBusyGroupHiddenBehindSpringWrapper() throws Exception {
        // Redis replies BUSYGROUP for an existing group; Spring Data Redis
        // wraps it in a RedisSystemException whose own message is the generic
        // "Error in execution" - the BUSYGROUP text only sits in the cause.
        // This exact shape wedged the lanes on every backend restart (the
        // group always exists after the first deploy).
        when(streamOps.createGroup(eq(STREAM), eq(ReadOffset.from("0")), eq(GROUP)))
                .thenThrow(new RedisSystemException("Error in execution",
                        new RedisCommandExecutionException("BUSYGROUP Consumer Group name already exists")));

        consumer.ensureGroup(STREAM);
        consumer.ensureGroup(STREAM);

        // Both calls survive, and the group is cached: createGroup ran once.
        verify(streamOps, times(1)).createGroup(eq(STREAM), eq(ReadOffset.from("0")), eq(GROUP));
    }

    @Test
    void ensureGroupRethrowsRealCreateFailures() {
        when(streamOps.createGroup(eq(STREAM), eq(ReadOffset.from("0")), eq(GROUP)))
                .thenThrow(new RedisSystemException("Error in execution",
                        new RedisCommandExecutionException(
                                "WRONGTYPE Operation against a key holding the wrong kind of value")));

        assertThatThrownBy(() -> consumer.ensureGroup(STREAM))
                .isInstanceOf(RedisSystemException.class);
    }

    @Test
    void exceptionMessagesJoinsSpringWrappedCauseChain() {
        // The recovery contract: NOGROUP detection and the poll-failed log
        // must see the real server reply even though it is buried in the
        // cause chain behind the generic wrapper message.
        RedisSystemException wrapped = new RedisSystemException("Error in execution",
                new RedisCommandExecutionException(
                        "NOGROUP No such key 'alarm:stream:realtime' or consumer group 'n4-backend' "
                                + "in XREADGROUP with GROUP option"));

        String messages = AlarmStreamConsumer.exceptionMessages(wrapped);

        assertThat(messages).contains("Error in execution").contains("NOGROUP");
    }

    @SuppressWarnings("unchecked")
    private MapRecord<String, Object, Object> record(String id, String payload) {
        return (MapRecord<String, Object, Object>) (MapRecord<?, ?, ?>)
                StreamRecords.newRecord().in(STREAM).withId(id).ofMap(Map.of("data", payload));
    }

    private String stateJson(String path) {
        PointAlarmState state = new PointAlarmState();
        state.setMqttPath(path);
        state.setValue("1");
        state.setTimestamp(1_700_000_000_000L);
        try {
            return objectMapper.writeValueAsString(state);
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }
}
