package com.n4monitor.alarm.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.core.HashOperations;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.LinkedHashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Tests for the alarm:active snapshot cache: parsing, sorting and the TTL
 * single-read behavior.
 */
class ActiveAlarmStoreTest {

    private static final String ACTIVE_KEY = "alarm:active";

    private final StringRedisTemplate redisTemplate = mock(StringRedisTemplate.class);
    @SuppressWarnings("unchecked")
    private final HashOperations<String, Object, Object> hashOps = mock(HashOperations.class);
    private final ObjectMapper objectMapper = new ObjectMapper();
    private ActiveAlarmStore store;

    @BeforeEach
    void setUp() {
        when(redisTemplate.opsForHash()).thenReturn(hashOps);
        store = new ActiveAlarmStore(redisTemplate, objectMapper);
        ReflectionTestUtils.setField(store, "activeRedisKey", ACTIVE_KEY);
    }

    private Map<Object, Object> sampleData() {
        Map<Object, Object> data = new LinkedHashMap<>();
        data.put("/sites/A1/fcu1/temp1",
                "{\"mqttPath\":\"/sites/A1/fcu1/temp1\",\"severity\":\"Critical\",\"level\":\"High\","
                        + "\"status\":\"Unack\",\"ackBy\":\"-\",\"timestamp\":3000,\"source\":\"fcu1/temp1\","
                        + "\"duration\":\"5\",\"stage\":\"start\",\"stageMessage\":\"Start\"}");
        data.put("/sites/A1/fcu1/temp2",
                "{\"mqttPath\":\"/sites/A1/fcu1/temp2\",\"severity\":\"Major\",\"level\":\"Low\","
                        + "\"status\":\"Ack\",\"ackBy\":\"op\",\"timestamp\":1000}");
        data.put("/sites/A1/fcu1/temp3", "{\"mqttPath\":\"/sites/A1/fcu1/temp3\",\"severity\":\"\"}");
        return data;
    }

    @Test
    void snapshotParsesFieldsWithOriginalDefaultsAndSortsNewestFirst() {
        when(hashOps.entries(anyString())).thenReturn(sampleData());

        var alarms = store.snapshot();
        assertThat(alarms).hasSize(3);
        // Newest (epoch 3000) first; the record without a timestamp goes last.
        assertThat(alarms.get(0).path()).isEqualTo("/sites/A1/fcu1/temp1");
        assertThat(alarms.get(0).record().getSeverity()).isEqualTo("Critical");
        assertThat(alarms.get(0).record().getStatus()).isEqualTo("Unack");
        assertThat(alarms.get(0).record().getAckBy()).isEqualTo("-");
        assertThat(alarms.get(1).path()).isEqualTo("/sites/A1/fcu1/temp2");
        assertThat(alarms.get(1).record().getAckBy()).isEqualTo("op");
        assertThat(alarms.get(2).epochMillis()).isNull();
        assertThat(alarms.get(2).record().getTime()).isEqualTo("-");
        // Empty severity falls back to the DTO default (""), not "Normal".
        assertThat(alarms.get(2).record().getSeverity()).isEmpty();
    }

    @Test
    void snapshotWithinTtlPullsRedisOnlyOnce() {
        when(hashOps.entries(anyString())).thenReturn(sampleData());
        store.snapshot();
        store.snapshot();
        store.snapshot();
        verify(hashOps, times(1)).entries(ACTIVE_KEY);
    }

    @Test
    void invalidateForcesReloadOnNextSnapshot() {
        when(hashOps.entries(anyString())).thenReturn(sampleData());
        store.snapshot();
        store.invalidate();
        store.snapshot();
        verify(hashOps, times(2)).entries(ACTIVE_KEY);
    }
}
