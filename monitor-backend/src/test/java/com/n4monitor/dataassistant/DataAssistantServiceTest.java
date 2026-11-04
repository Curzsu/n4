package com.n4monitor.dataassistant;

import com.n4monitor.alarm.dto.AlarmRecordDto;
import com.n4monitor.alarm.service.AlarmService;
import com.n4monitor.deployment.service.DeploymentService;
import com.n4monitor.exception.BusinessException;
import com.n4monitor.tdengine.TdengineSyncService;
import org.apache.ibatis.builder.xml.XMLMapperBuilder;
import org.apache.ibatis.io.Resources;
import org.apache.ibatis.session.Configuration;
import org.apache.ibatis.session.SqlSessionFactory;
import org.junit.jupiter.api.Test;
import org.springframework.security.access.AccessDeniedException;

import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class DataAssistantServiceTest {
    private final GeminiClient gemini = mock(GeminiClient.class);
    private final AlarmService alarms = mock(AlarmService.class);
    private final TdengineSyncService history = mock(TdengineSyncService.class);
    private final DeploymentService deployments = mock(DeploymentService.class);
    private final SqlSessionFactory sqlSessions = mock(SqlSessionFactory.class);
    private final DataAssistantService service = new DataAssistantService(
            gemini, alarms, history, deployments, sqlSessions);

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
        assertFalse(result.execution().presetQuery());
        assertTrue(result.execution().summaryGenerated());
        assertEquals("Redis snapshot", result.execution().dataRead().source());
        assertNull(result.execution().dataRead().sql());
        assertTrue(result.execution().dataRead().postProcessed());
    }

    @Test
    void exposesTheDeviceDeploymentMapperSql() throws Exception {
        var configuration = new Configuration();
        String resource = "mapper/DeviceDeploymentMapper.xml";
        try (var mapper = Resources.getResourceAsStream(resource)) {
            new XMLMapperBuilder(mapper, configuration, resource, configuration.getSqlFragments()).parse();
        }
        when(sqlSessions.getConfiguration()).thenReturn(configuration);
        when(gemini.plan(any(), any(), any())).thenReturn(new DataAssistantService.Query(
                "deployments", "count", null, null, null, null, null, null, null));
        when(deployments.listAll()).thenReturn(List.of());

        var result = service.ask(new DataAssistantService.Question("Count deployed devices", List.of()),
                Set.of("device:deploy:view"));

        var read = result.execution().dataRead();
        assertEquals("MySQL", read.source());
        assertTrue(read.sql().contains("FROM device_deployment dd"));
        assertTrue(read.sql().contains("WHERE dd.is_deleted = 0"));
        assertTrue(read.postProcessed());
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
    void usesAnUnfilteredDayCountForTheHistoricalStarter() {
        String sql = "SELECT COUNT(*) FROM alarm_event";
        when(history.countAlarmEventsWithSql(any())).thenReturn(new TdengineSyncService.SqlResult<>(17L, sql));
        when(gemini.answer(any(), any(), any())).thenReturn("17 alarm frames.");

        var result = service.ask(
                new DataAssistantService.Question("How many alarm frames were recorded today?", List.of()),
                Set.of("alarm:event:view"));

        assertEquals(17, result.evidence().matchedCount());
        assertEquals("alarm_events", result.query().dataset());
        assertEquals("count", result.query().operation());
        assertEquals(result.query().startDate(), result.query().endDate());
        assertTrue(result.execution().presetQuery());
        assertEquals(sql, result.execution().dataRead().sql());
        assertFalse(result.execution().dataRead().postProcessed());
        verify(gemini, never()).plan(any(), any(), any());
    }

    @Test
    void retainsQueryResultsAndReportsWhenSummaryFails() {
        when(history.countAlarmEventsWithSql(any())).thenReturn(
                new TdengineSyncService.SqlResult<>(17L, "SELECT COUNT(*) FROM alarm_event"));
        when(gemini.answer(any(), any(), any())).thenThrow(new BusinessException(502, "Summary unavailable"));

        var result = service.ask(
                new DataAssistantService.Question("How many alarm frames were recorded today?", List.of()),
                Set.of("alarm:event:view"));

        assertEquals(17, result.evidence().matchedCount());
        assertFalse(result.execution().summaryGenerated());
    }

    @Test
    void rejectsHistoricalGroupingWhenTheReadCapWouldProducePartialCounts() {
        String yesterday = LocalDate.now(ZoneId.of("Asia/Shanghai")).minusDays(1).toString();
        when(gemini.plan(any(), any(), any())).thenReturn(new DataAssistantService.Query(
                "alarm_events", "group", "severity", null, null, null, null, yesterday, yesterday));
        when(history.queryAlarmEventsPageWithSql(any(), eq(0), eq(2001))).thenReturn(
                new TdengineSyncService.SqlResult<>(Collections.nCopies(2001, alarm("UPS-A", "Major")),
                        "SELECT ts FROM alarm_event"));

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
