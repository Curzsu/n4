package com.n4monitor.tdengine;

import com.n4monitor.alarm.dto.EventExportFilter;

import java.util.stream.Collectors;

/**
 * Builds the TDengine COUNT / page SELECT statements behind the Event page
 * Excel export. Kept separate from {@link TdengineSyncService} so the SQL
 * shape stays unit-testable without a database.
 * <p>
 * String literals are escaped client-side via {@link TdengineSql#escapeLiteral}
 * (the REST driver has no server-side parameter binding); timestamp bounds are
 * bound as raw epoch-millis numbers, the same contract as the writer. Pages
 * order by the write timestamp {@code ts} ascending and page with OFFSET
 * rather than a {@code ts <} cursor: bursty batch writes put many rows on the
 * same millisecond, and a cursor would silently skip rows at page borders
 * (same reasoning as the descending history queries).
 */
final class EventExportSql {

    /**
     * Column list shared by the export page query and the existing
     * {@code queryAlarmEvents} select, so both feed the same row mapper.
     */
    static final String SELECT_COLUMNS = "ts, data_timestamp, point_value, alarm_level, alarm_severity, "
            + "alarm_source, alarm_message, alarm_location, alarm_status, ack_by, alarm_duration, "
            + "alarm_stage, alarm_stage_message, alarm_start_time, path";

    private EventExportSql() {
    }

    static String count(EventExportFilter filter, String supertable) {
        return "SELECT COUNT(*) FROM " + supertable + where(filter);
    }

    static String page(EventExportFilter filter, String supertable, int limit, int offset) {
        return "SELECT " + SELECT_COLUMNS + " FROM " + supertable + where(filter)
                + " ORDER BY ts ASC LIMIT " + limit + " OFFSET " + offset;
    }

    /**
     * Shared WHERE clause: the mandatory write-time range plus the optional
     * severity whitelist and alarm-only filter. Alarm rows always carry a
     * non-empty {@code alarm_level} (normal frames write NULL), but both
     * conditions are applied so an empty-string level can never leak normal
     * rows into an alarm-only export.
     */
    private static String where(EventExportFilter filter) {
        StringBuilder where = new StringBuilder(" WHERE ts >= ")
                .append(filter.startTs())
                .append(" AND ts <= ")
                .append(filter.endTs());
        if (!filter.severities().isEmpty()) {
            where.append(" AND alarm_severity IN (")
                    .append(filter.severities().stream()
                            .map(TdengineSql::escapeLiteral)
                            .collect(Collectors.joining(", ")))
                    .append(')');
        }
        if (filter.alarmOnly()) {
            where.append(" AND alarm_level IS NOT NULL AND alarm_level <> ''");
        }
        return where.toString();
    }
}
