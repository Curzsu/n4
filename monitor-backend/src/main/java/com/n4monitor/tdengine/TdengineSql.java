package com.n4monitor.tdengine;

import com.n4monitor.alarm.model.PointAlarmState;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Builds TDengine INSERT SQL for alarm rows.
 * <p>
 * Rows are grouped by point path and chunked into multi-table, multi-row
 * statements of the shape:
 * <pre>
 * INSERT INTO t1 USING stable TAGS('path1') VALUES (...)(...)
 *            t2 USING stable TAGS('path2') VALUES (...)
 * </pre>
 * so a buffered batch becomes a handful of round trips instead of one INSERT
 * per frame. All string literals are escaped client-side (the REST driver has
 * no server-side parameter binding), including quotes, backslashes and control
 * characters. Timestamps are bound as raw epoch-millis numbers, which TDengine
 * interprets as absolute UTC instants (timezone-safe, same contract as the
 * previous PreparedStatement-based writer).
 */
final class TdengineSql {

    /** Maximum rows per INSERT statement. */
    static final int MAX_ROWS_PER_STATEMENT = 300;

    /**
     * Maximum accumulated SQL characters per statement. The server accepts up
     * to 1MB of SQL text; this leaves headroom for multi-byte characters.
     */
    static final int MAX_SQL_CHARS_PER_STATEMENT = 250_000;

    private TdengineSql() {
    }

    /** One rendered INSERT statement plus the rows it contains, for fallback. */
    record Chunk(String sql, List<PointAlarmState> rows) {
    }

    /**
     * Render {@code states} into chunked INSERT statements. Rows with a null or
     * blank mqttPath are skipped (they have no subtable identity). Rows for the
     * same point path are grouped under one TAGS clause; distinct paths stay in
     * distinct clauses even when they sanitize to the same subtable name, so
     * the created tag always matches the path that created the subtable.
     */
    static List<Chunk> buildChunks(String supertable, String subtableSuffix, List<PointAlarmState> states) {
        List<Chunk> chunks = new ArrayList<>();
        if (states == null || states.isEmpty()) {
            return chunks;
        }
        Map<String, StringBuilder> groups = new LinkedHashMap<>();
        List<PointAlarmState> pendingRows = new ArrayList<>();
        int pendingChars = 0;

        for (PointAlarmState state : states) {
            if (state == null) {
                continue;
            }
            String path = state.getMqttPath();
            if (path == null || path.isBlank()) {
                continue;
            }
            StringBuilder group = groups.computeIfAbsent(path, key ->
                    new StringBuilder(subtableName(path, subtableSuffix))
                            .append(" USING ").append(supertable)
                            .append(" TAGS(").append(escapeLiteral(path)).append(')'));
            String values = valuesFragment(state, System.currentTimeMillis());
            group.append(" VALUES ").append(values);
            pendingRows.add(state);
            pendingChars += values.length() + 8;

            if (pendingRows.size() >= MAX_ROWS_PER_STATEMENT || pendingChars >= MAX_SQL_CHARS_PER_STATEMENT) {
                chunks.add(assemble(groups, pendingRows));
                groups = new LinkedHashMap<>();
                pendingRows = new ArrayList<>();
                pendingChars = 0;
            }
        }
        if (!pendingRows.isEmpty()) {
            chunks.add(assemble(groups, pendingRows));
        }
        return chunks;
    }

    private static Chunk assemble(Map<String, StringBuilder> groups, List<PointAlarmState> rows) {
        StringBuilder sql = new StringBuilder("INSERT INTO ");
        boolean first = true;
        for (StringBuilder group : groups.values()) {
            if (!first) {
                sql.append(' ');
            }
            sql.append(group);
            first = false;
        }
        return new Chunk(sql.toString(), List.copyOf(rows));
    }

    /**
     * Sanitize a point path into a valid subtable name and append the
     * realtime/event suffix.
     */
    static String subtableName(String path, String subtableSuffix) {
        if (path == null || path.isEmpty()) {
            return "unknown_point" + subtableSuffix;
        }
        return path.replaceAll("[^a-zA-Z0-9_]", "_") + subtableSuffix;
    }

    /**
     * Render one VALUES tuple for {@code state}. {@code writeTs} is the write
     * moment (primary ts); the observation timestamp falls back to it when the
     * state carries none.
     */
    static String valuesFragment(PointAlarmState state, long writeTs) {
        Long dataTimestamp = state.getTimestamp();
        long dataTs = dataTimestamp != null ? dataTimestamp : writeTs;

        StringBuilder sb = new StringBuilder(256);
        sb.append('(').append(writeTs).append(", ").append(dataTs).append(", ");
        appendLiteral(sb, state.getValue());
        sb.append(", ");
        appendLiteral(sb, state.getType());
        sb.append(", ");
        appendLiteral(sb, state.getLevel());
        sb.append(", ");
        appendLiteral(sb, state.getSeverity());
        sb.append(", ");
        appendLiteral(sb, state.getMessage());
        sb.append(", ");
        appendLiteral(sb, state.getTimeDelay() != null ? String.valueOf(state.getTimeDelay()) : null);
        sb.append(", ");
        appendLiteral(sb, state.getDeadband());
        sb.append(", ");
        appendLiteral(sb, state.getAckBy());
        sb.append(", ");
        appendLiteral(sb, state.getStatus());
        sb.append(", ");
        appendLiteral(sb, state.getSource());
        sb.append(", ");
        appendLiteral(sb, state.getLocation());
        sb.append(", ");
        appendLiteral(sb, state.getStage());
        sb.append(", ");
        appendLiteral(sb, state.getStageMessage());
        sb.append(", ");
        appendLiteral(sb, state.getDuration());
        sb.append(", ");
        Long alarmStart = state.getAlarmStartTime();
        if (alarmStart != null) {
            sb.append(alarmStart);
        } else {
            sb.append("NULL");
        }
        sb.append(')');
        return sb.toString();
    }

    /**
     * Escape a Java string as a TDengine single-quoted string literal, or the
     * SQL NULL keyword when the value is absent. Backslash escapes cover
     * quotes, backslashes and control characters; TDengine interprets them
     * back to the original bytes.
     */
    static String escapeLiteral(String value) {
        if (value == null) {
            return "NULL";
        }
        StringBuilder sb = new StringBuilder(value.length() + 8);
        appendLiteral(sb, value);
        return sb.toString();
    }

    private static void appendLiteral(StringBuilder sb, String value) {
        if (value == null) {
            sb.append("NULL");
            return;
        }
        sb.append('\'');
        for (int i = 0; i < value.length(); i++) {
            char c = value.charAt(i);
            switch (c) {
                case '\\' -> sb.append("\\\\");
                case '\'' -> sb.append("\\'");
                case '\n' -> sb.append("\\n");
                case '\r' -> sb.append("\\r");
                case '\t' -> sb.append("\\t");
                default -> sb.append(c);
            }
        }
        sb.append('\'');
    }
}
