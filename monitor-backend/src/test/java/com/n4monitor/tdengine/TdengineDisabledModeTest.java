package com.n4monitor.tdengine;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.n4monitor.alarm.stream.AlarmStreamConsumer;
import com.n4monitor.exception.BusinessException;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.data.redis.core.StringRedisTemplate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;

class TdengineDisabledModeTest {
    @Test
    void disabledHistoryFailsBeforeOpeningAnyDatabaseConnection() {
        var configuration = new TdengineConfig();
        configuration.setEnabled(false);
        var service = new TdengineSyncService(configuration, new ObjectMapper());
        assertThatThrownBy(() -> service.queryAlarmEvents(null, 0, 1))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("disabled");
    }

    @Test
    void disabledModeDoesNotStartPersistenceConsumers() {
        new ApplicationContextRunner()
                .withBean(StringRedisTemplate.class, () -> mock(StringRedisTemplate.class))
                .withBean(TdengineSyncService.class, () -> mock(TdengineSyncService.class))
                .withBean(ObjectMapper.class, ObjectMapper::new)
                .withUserConfiguration(AlarmStreamConsumer.class)
                .withPropertyValues(
                        "tdengine.enabled=false",
                        "alarm.redis.realtime-stream=alarm:stream:realtime",
                        "alarm.redis.persistence-stream=alarm:stream:persist")
                .run(context -> assertThat(context).doesNotHaveBean(AlarmStreamConsumer.class));
    }
}
