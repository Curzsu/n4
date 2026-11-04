package com.n4monitor.dataassistant;

import com.n4monitor.alarm.dto.AlarmRecordDto;
import com.n4monitor.alarm.service.AlarmService;
import com.n4monitor.deployment.service.DeploymentService;
import com.n4monitor.exception.BusinessException;
import com.n4monitor.tdengine.TdengineSyncService;
import org.junit.jupiter.api.Test;
import org.springframework.security.access.AccessDeniedException;

import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class DataAssistantServiceTest {
    private final GeminiClient gemini = mock(GeminiClient.class);
    private final AlarmService alarms = mock(AlarmService.class);
    private final TdengineSyncService history = mock(TdengineSyncService.class);
    private final DeploymentService deployments = mock(DeploymentService.class);
    private final DataAssistantService service = new DataAssistantService(gemini, alarms, history, deployments);

    @Test
    void rejectsModelSelectedDatasetOutsideUserPermissions() {
        when(gemini.plan(any(), any(), any())).thenReturn(new DataAssistantService.Query(
                "deployments", "count", null, null, null, null, null, null, null));

        assertThrows(AccessDeniedException.class, () -> service.ask(
                new DataAssistantService.Question("Count devices", List.of()), Set.of("alarm:view")));
        verifyNoInteractions(deployments, alarms, history);
    }

    @Test
    void summarizesOnlyMatchingAlarmRowsAndReturnsExactCounts() {
        when(gemini.plan(any(), any(), any())).thenReturn(new DataAssistantService.Query(
                "active_alarms", "group", "severity", null, null, "source", "ups", null, null));
        var unrelated = alarm("Cooling", "Critical");
        unrelated.setMessage("UPS room temperature");
        when(alarms.getActiveAlarms("active")).thenReturn(List.of(
                alarm("UPS-A", "Major"), alarm("UPS-B", "Major"),
                alarm("UPS-C", "Warning"), unrelated));
        when(gemini.answer(any(), any(), any())).thenReturn("Three UPS alarms.");

        var result = service.ask(new DataAssistantService.Question("Group UPS alarms", List.of()),
                Set.of("alarm:view"));

        assertEquals(3, result.evidence().matchedCount());
        assertEquals(List.of(Map.of("severity", "Major", "count", 2L),
                Map.of("severity", "Warning", "count", 1L)), result.evidence().rows());
        assertEquals("Three UPS alarms.", result.answer());
    }

    @Test
    void matchesAcknowledgedStatusExactly() {
        when(gemini.plan(any(), any(), any())).thenReturn(new DataAssistantService.Query(
                "active_alarms", "count", null, null, "Ack", null, null, null, null));
        var acknowledged = alarm("UPS-A", "Major");
        acknowledged.setStatus("Ack");
        var unacknowledged = alarm("UPS-B", "Major");
        unacknowledged.setStatus("Unack");
        when(alarms.getActiveAlarms("active")).thenReturn(List.of(acknowledged, unacknowledged));

        var result = service.ask(new DataAssistantService.Question("Count acknowledged alarms", List.of()),
                Set.of("alarm:view"));

        assertEquals(1, result.evidence().matchedCount());
    }

    @Test
    void rejectsHistoricalGroupingWhenTheReadCapWouldProducePartialCounts() {
        String yesterday = LocalDate.now(ZoneId.of("Asia/Shanghai")).minusDays(1).toString();
        when(gemini.plan(any(), any(), any())).thenReturn(new DataAssistantService.Query(
                "alarm_events", "group", "severity", null, null, null, null, yesterday, yesterday));
        when(history.queryAlarmEventsPage(any(), eq(0), eq(2001))).thenReturn(
                Collections.nCopies(2001, alarm("UPS-A", "Major")));

        var error = assertThrows(BusinessException.class, () -> service.ask(
                new DataAssistantService.Question("Group historical alarms", List.of()), Set.of("alarm:event:view")));

        assertEquals(422, error.getCode());
    }

    private AlarmRecordDto alarm(String source, String severity) {
        var alarm = new AlarmRecordDto();
        alarm.setSource(source);
        alarm.setSeverity(severity);
        return alarm;
    }
}
