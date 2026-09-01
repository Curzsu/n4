package com.n4monitor.alarm.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.n4monitor.alarm.dto.AlarmRecordDto;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit tests for the single aggregation implementation shared by the dashboard
 * endpoints and the WebSocket notification listener.
 */
class AlarmAggregatorTest {

    private static final DateTimeFormatter HOUR_FORMAT = DateTimeFormatter.ofPattern("HH:00");

    private final ObjectMapper mapper = new ObjectMapper();

    private ActiveAlarm alarm(String severity, Long epochMillis, String path, String source) {
        AlarmRecordDto dto = new AlarmRecordDto();
        dto.setSeverity(severity);
        dto.setSource(source);
        dto.setLevel("High");
        dto.setStatus("Unack");
        if (epochMillis != null) {
            dto.setTime(LocalDateTime.ofInstant(Instant.ofEpochMilli(epochMillis), ZoneId.systemDefault())
                    .format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")));
        } else {
            dto.setTime("-");
        }
        return new ActiveAlarm(dto, epochMillis, path);
    }

    @Test
    void summaryCountsOnlyConfiguredSeverities() {
        var alarms = List.of(
                alarm("Critical", 1L, "p1", "s"),
                alarm("Critical", 2L, "p2", "s"),
                alarm("Major", 3L, "p3", "s"),
                alarm("", 4L, "p4", "s"));
        var summary = AlarmAggregator.summary(alarms);
        assertThat(summary.getCritical()).isEqualTo(2);
        assertThat(summary.getMajor()).isEqualTo(1);
        assertThat(summary.getMinor()).isEqualTo(0);
        assertThat(summary.getWarning()).isEqualTo(0);
    }

    @Test
    void severityTotalIncludesNonMatchingRecords() {
        var alarms = List.of(
                alarm("Critical", 1L, "p1", "s"),
                alarm("", 2L, "p2", "s"));
        var dto = AlarmAggregator.bySeverity(alarms);
        // Total counts every active record, not just the four severities.
        assertThat(dto.getTotal()).isEqualTo(2);
        assertThat(dto.getItems()).hasSize(4);
        assertThat(dto.getItems().get(0).getName()).isEqualTo("Critical");
        assertThat(dto.getItems().get(0).getValue()).isEqualTo(1);
        assertThat(dto.getItems().get(0).getPercentage()).isEqualTo(50.0);
    }

    @Test
    void trendBucketsByLocalHourAndSkipsRecordsWithoutTimestamp() {
        long epoch = System.currentTimeMillis();
        String hourKey = LocalDateTime.ofInstant(Instant.ofEpochMilli(epoch), ZoneId.systemDefault())
                .format(HOUR_FORMAT);
        var alarms = List.of(
                alarm("Major", epoch, "p1", "s"),
                alarm("Major", null, "p2", "s"));
        var dto = AlarmAggregator.trend(alarms);
        assertThat(dto.getTimeLabels()).hasSize(24);
        int hourIndex = dto.getTimeLabels().indexOf(hourKey);
        var majorSeries = dto.getSeries().stream()
                .filter(s -> "Major".equals(s.getName())).findFirst().orElseThrow();
        assertThat(majorSeries.getData().get(hourIndex)).isEqualTo(1);
    }

    @Test
    void byHourCountsInFourHourSteps() {
        // The chart samples every 4th hour ("00:00", "04:00", ...), so pin the
        // records inside the "00:00" sampled bucket. Deriving the epoch from
        // System.currentTimeMillis() made this test fail whenever it ran during
        // a non-sampled hour (e.g. 01:xx).
        long epoch = LocalDateTime.now(ZoneId.systemDefault()).withHour(0).withMinute(30)
                .atZone(ZoneId.systemDefault()).toInstant().toEpochMilli();
        // The record without a timestamp is not counted at all.
        var dto = AlarmAggregator.byHour(List.of(
                alarm("Major", epoch, "p1", "s"),
                alarm("Major", epoch, "p2", "s"),
                alarm("Major", null, "p3", "s")));
        assertThat(dto.getHours()).containsExactly("00:00", "04:00", "08:00", "12:00", "16:00", "20:00");
        assertThat(dto.getCounts().get(0)).isEqualTo(2);
        assertThat(dto.getCounts().stream().mapToInt(Integer::intValue).sum()).isEqualTo(2);
    }

    @Test
    void bySystemUsesThirdPathSegmentAndFallsBackToUnknown() {
        var dto = AlarmAggregator.bySystem(List.of(
                alarm("Major", 1L, "Drivers/ModbusTcpNetwork/LVP_A4/dev/point", "s"),
                alarm("Major", 2L, "Drivers/ModbusTcpNetwork/LVP_A4/dev/point2", "s"),
                alarm("Major", 3L, "short", "s")));
        assertThat(dto.getItems()).hasSize(2);
        assertThat(dto.getItems().get(0).getName()).isEqualTo("LVP_A4");
        assertThat(dto.getItems().get(0).getValue()).isEqualTo(2);
        assertThat(dto.getItems().get(1).getName()).isEqualTo("Unknown");
    }

    @Test
    void topSourcesExcludesNonAlarmRecordsAndDefaultsSource() {
        var dto = AlarmAggregator.topSources(List.of(
                alarm("Major", 1L, "p1", "fcu/point1"),
                alarm("Major", 2L, "p2", ""),
                alarm("", 3L, "p3", "fcu/point1"),
                alarm("Normal", 4L, "p4", "fcu/point1")));
        // Only the two real alarm records count; empty source becomes "Unknown".
        assertThat(dto.getItems()).hasSize(2);
        assertThat(dto.getItems().get(0).getName()).isEqualTo("Unknown");
        assertThat(dto.getItems().get(0).getCount()).isEqualTo(1);
        assertThat(dto.getItems().get(1).getName()).isEqualTo("fcu/point1");
        assertThat(dto.getItems().get(1).getCount()).isEqualTo(1);
    }

    @Test
    void webSocketAggregatesCountMissingSeverityAsNormalInTotal() throws Exception {
        var alarms = List.of(
                alarm("Critical", 1L, "p1", "s"),
                alarm("", 2L, "p2", "s"));
        ObjectNode summary = mapper.createObjectNode();
        ObjectNode severityData = mapper.createObjectNode();
        ObjectNode hourlyData = mapper.createObjectNode();
        AlarmAggregator.writeWebSocketAggregates(mapper, alarms, summary, severityData, hourlyData);

        assertThat(summary.get("critical").asInt()).isEqualTo(1);
        // Total includes the "Normal" bucket (5 categories), matching the
        // pre-refactor listener payload the frontend consumes.
        assertThat(severityData.get("total").asInt()).isEqualTo(2);
        assertThat(severityData.get("items").size()).isEqualTo(4);
        assertThat(hourlyData.get("hours").size()).isEqualTo(6);
        assertThat(hourlyData.get("counts").size()).isEqualTo(6);
    }
}
