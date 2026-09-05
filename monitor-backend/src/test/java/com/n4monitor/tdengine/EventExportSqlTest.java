package com.n4monitor.tdengine;

import com.n4monitor.alarm.dto.EventExportFilter;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Tests for the Event export SQL builder: WHERE composition, the severity
 * whitelist, the alarm-only filter and the ascending OFFSET page shape.
 */
class EventExportSqlTest {

    private static final String SUPERTABLE = "alarm_event";

    @Test
    void rangeAndAlarmOnlyBuildCountQuery() {
        EventExportFilter filter = new EventExportFilter(1000L, 2000L, List.of(), true);

        assertThat(EventExportSql.count(filter, SUPERTABLE))
                .isEqualTo("SELECT COUNT(*) FROM alarm_event"
                        + " WHERE ts >= 1000 AND ts <= 2000"
                        + " AND alarm_level IS NOT NULL AND alarm_level <> ''");
    }

    @Test
    void pageQueryOrdersAscendingAndPagesByOffset() {
        EventExportFilter filter = new EventExportFilter(0L, 10L, List.of(), false);

        assertThat(EventExportSql.page(filter, SUPERTABLE, 100, 200))
                .isEqualTo("SELECT " + EventExportSql.SELECT_COLUMNS
                        + " FROM alarm_event WHERE ts >= 0 AND ts <= 10"
                        + " ORDER BY ts ASC LIMIT 100 OFFSET 200");
    }

    @Test
    void severityListIsQuotedAndJoined() {
        EventExportFilter filter = new EventExportFilter(0L, 10L, List.of("Critical", "Major"), false);

        String sql = EventExportSql.page(filter, SUPERTABLE, 10, 0);
        assertThat(sql).contains(" AND alarm_severity IN ('Critical', 'Major')");
    }

    @Test
    void unknownSeverityIsRejectedBeforeReachingSql() {
        assertThatThrownBy(() -> new EventExportFilter(0L, 1L, List.of("Critical'; DROP TABLE x"), true))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Unknown severity");
    }

    @Test
    void blankSeveritiesAreDroppedAndDuplicatesCollapsed() {
        EventExportFilter filter =
                new EventExportFilter(0L, 1L, Arrays.asList(" Critical ", "", null, "Critical"), false);

        // A null element survives construction only via Arrays.asList; the
        // canonical list keeps one trimmed entry.
        assertThat(filter.severities()).containsExactly("Critical");
        assertThat(EventExportSql.page(filter, SUPERTABLE, 10, 0))
                .contains(" IN ('Critical')")
                .doesNotContain("', 'Critical'");
    }

    @Test
    void endBeforeStartIsRejected() {
        assertThatThrownBy(() -> new EventExportFilter(2000L, 1000L, List.of(), true))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("end time");
    }

    @Test
    void nullSeverityListIsTreatedAsEmpty() {
        EventExportFilter filter = new EventExportFilter(0L, 1L, null, false);

        assertThat(filter.severities()).isEmpty();
        assertThat(EventExportSql.count(filter, SUPERTABLE))
                .doesNotContain("alarm_severity");
    }
}
