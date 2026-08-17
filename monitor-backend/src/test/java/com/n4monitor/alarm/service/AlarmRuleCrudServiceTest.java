package com.n4monitor.alarm.service;

import com.n4monitor.alarm.mapper.AlarmRuleEnumMapper;
import com.n4monitor.alarm.mapper.AlarmRuleNumericMapper;
import com.n4monitor.entity.AlarmRuleNumericEntity;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DuplicateKeyException;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Tests the alarm-name collision retry: a concurrent create that grabbed the
 * same generated name surfaces as a DuplicateKeyException, which must be
 * retried with a fresh sequence instead of failing the request.
 */
@ExtendWith(MockitoExtension.class)
class AlarmRuleCrudServiceTest {

    @Mock
    private AlarmRuleNumericMapper numericMapper;
    @Mock
    private AlarmRuleEnumMapper enumMapper;
    @Mock
    private AlarmRuleSyncService syncService;

    @Test
    void createNumericRuleRegeneratesNameOnUniqueKeyCollision() {
        AlarmRuleCrudService service = new AlarmRuleCrudService(numericMapper, enumMapper, syncService);

        // First attempt sees no used names -> p_00 (collides with a concurrent
        // insert); second attempt sees the winner's committed row -> p_01.
        when(numericMapper.findAlarmNamesByPointNamePrefix("p"))
                .thenReturn(List.of())
                .thenReturn(List.of("p_00"));
        when(enumMapper.findAlarmNamesByPointNamePrefix("p")).thenReturn(List.of());
        when(numericMapper.insert(any(AlarmRuleNumericEntity.class)))
                .thenThrow(new DuplicateKeyException("uk_alarm_name"))
                .thenAnswer(inv -> null);

        AlarmRuleNumericEntity entity = new AlarmRuleNumericEntity();
        entity.setPointName("p");

        service.createNumericRule(entity);

        assertThat(entity.getAlarmName()).isEqualTo("p_01");
        verify(numericMapper, times(2)).insert(any(AlarmRuleNumericEntity.class));
        verify(syncService, atLeastOnce()).syncNumericRulesToRedis();
    }
}
