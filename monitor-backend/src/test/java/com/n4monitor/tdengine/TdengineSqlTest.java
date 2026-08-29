package com.n4monitor.tdengine;

import com.n4monitor.alarm.model.PointAlarmState;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tests for the TDengine INSERT SQL builder: literal escaping, VALUES shape,
 * subtable grouping and chunking budgets.
 */
class TdengineSqlTest {

    private static final String SUPERTABLE = "alarm_event";
    private static final String SUFFIX = "_ev";

    private PointAlarmState state(String path, String value) {
        PointAlarmState state = new PointAlarmState();
        state.setMqttPath(path);
        state.setTimestamp(1_700_000_000_000L);
        state.setValue(value);
        state.setType("numeric");
        state.setStage("start");
        return state;
    }

    // ── escapeLiteral ──────────────────────────────────────────────────────

    @Test
    void nullLiteralBecomesSqlNull() {
        assertThat(TdengineSql.escapeLiteral(null)).isEqualTo("NULL");
    }

    @Test
    void plainStringIsSingleQuoted() {
        assertThat(TdengineSql.escapeLiteral("abc")).isEqualTo("'abc'");
    }

    @Test
    void quoteAndBackslashAreEscaped() {
        assertThat(TdengineSql.escapeLiteral("it's")).isEqualTo("'it\\'s'");
        assertThat(TdengineSql.escapeLiteral("a\\b")).isEqualTo("'a\\\\b'");
    }

    @Test
    void controlCharactersAreEscaped() {
        assertThat(TdengineSql.escapeLiteral("a\nb\rc\td"))
                .isEqualTo("'a\\nb\\rc\\td'");
    }

    // ── valuesFragment ─────────────────────────────────────────────────────

    @Test
    void valuesFragmentKeepsColumnOrderAndNulls() {
        PointAlarmState state = state("P/A", "12.5");
        String fragment = TdengineSql.valuesFragment(state, 1_700_000_000_500L);

        // ts, data_timestamp, value, type, then NULL for all unset alarm columns
        assertThat(fragment).startsWith("(1700000000500, 1700000000000, '12.5', 'numeric'");
        assertThat(fragment).contains(", NULL");
        // alarm_start_time (last column) is NULL for non-alarm rows
        assertThat(fragment).endsWith(", NULL)");
        // stage was set, so it must appear as a literal
        assertThat(fragment).contains("'start'");
    }

    @Test
    void dataTimestampFallsBackToWriteTsWhenAbsent() {
        PointAlarmState state = state("P/A", "1");
        state.setTimestamp(null);
        String fragment = TdengineSql.valuesFragment(state, 1_700_000_000_500L);
        assertThat(fragment).startsWith("(1700000000500, 1700000000500, ");
    }

    @Test
    void alarmStartTimeIsBoundAsMillis() {
        PointAlarmState state = state("P/A", "1");
        state.setAlarmStartTime(1_699_000_000_000L);
        String fragment = TdengineSql.valuesFragment(state, 1_700_000_000_500L);
        assertThat(fragment).endsWith(", 1699000000000)");
    }

    // ── buildChunks ────────────────────────────────────────────────────────

    @Test
    void singleRowBuildsOneMultiRowInsertStatement() {
        List<TdengineSql.Chunk> chunks =
                TdengineSql.buildChunks(SUPERTABLE, SUFFIX, List.of(state("A1/MVP/point_01", "3.14")));

        assertThat(chunks).hasSize(1);
        TdengineSql.Chunk chunk = chunks.get(0);
        assertThat(chunk.rows()).hasSize(1);
        // Multi-table auto-create shape: sanitized subtable name + ORIGINAL path as tag.
        assertThat(chunk.sql()).startsWith(
                "INSERT INTO A1_MVP_point_01_ev USING alarm_event TAGS('A1/MVP/point_01') VALUES (");
    }

    @Test
    void samePathRowsShareOneTagsClause() {
        List<PointAlarmState> states = List.of(state("P/A", "1"), state("P/A", "2"));
        List<TdengineSql.Chunk> chunks = TdengineSql.buildChunks(SUPERTABLE, SUFFIX, states);

        assertThat(chunks).hasSize(1);
        assertThat(chunks.get(0).rows()).hasSize(2);
        // one group header, two VALUES tuples
        assertThat(countOccurrences(chunks.get(0).sql(), "TAGS(")).isEqualTo(1);
        assertThat(countOccurrences(chunks.get(0).sql(), " VALUES ")).isEqualTo(2);
    }

    @Test
    void distinctPathsStayingInDistinctClausesEvenWithSameSanitizedName() {
        List<PointAlarmState> states = List.of(state("a/b", "1"), state("a-b", "2"));
        List<TdengineSql.Chunk> chunks = TdengineSql.buildChunks(SUPERTABLE, SUFFIX, states);

        assertThat(chunks).hasSize(1);
        // both sanitize to a_b_ev, but each path keeps its own clause and tag
        assertThat(countOccurrences(chunks.get(0).sql(), "TAGS(")).isEqualTo(2);
        assertThat(chunks.get(0).sql()).contains("TAGS('a/b')");
        assertThat(chunks.get(0).sql()).contains("TAGS('a-b')");
    }

    @Test
    void rowsAreChunkedByRowBudget() {
        List<PointAlarmState> states = new ArrayList<>();
        for (int i = 0; i < 700; i++) {
            states.add(state("P/" + (i % 3), String.valueOf(i)));
        }
        List<TdengineSql.Chunk> chunks = TdengineSql.buildChunks(SUPERTABLE, SUFFIX, states);

        assertThat(chunks).hasSize(3); // 300 + 300 + 100
        int totalRows = chunks.stream().mapToInt(c -> c.rows().size()).sum();
        assertThat(totalRows).isEqualTo(700);
        assertThat(chunks.get(0).rows()).hasSize(TdengineSql.MAX_ROWS_PER_STATEMENT);
        assertThat(chunks.get(2).rows()).hasSize(100);
        for (TdengineSql.Chunk chunk : chunks) {
            assertThat(chunk.sql()).startsWith("INSERT INTO ");
            assertThat(chunk.sql().length()).isLessThan(TdengineSql.MAX_SQL_CHARS_PER_STATEMENT + 10_000);
        }
    }

    @Test
    void rowsWithoutPathAreSkipped() {
        PointAlarmState noPath = state(null, "1");
        PointAlarmState blankPath = state("  ", "2");
        PointAlarmState valid = state("P/A", "3");

        List<TdengineSql.Chunk> chunks =
                TdengineSql.buildChunks(SUPERTABLE, SUFFIX, List.of(noPath, blankPath, valid));

        assertThat(chunks).hasSize(1);
        assertThat(chunks.get(0).rows()).hasSize(1);
        assertThat(chunks.get(0).sql()).contains("TAGS('P/A')");
    }

    @Test
    void emptyAndNullInputsProduceNoStatements() {
        assertThat(TdengineSql.buildChunks(SUPERTABLE, SUFFIX, List.of())).isEmpty();
        assertThat(TdengineSql.buildChunks(SUPERTABLE, SUFFIX, null)).isEmpty();
    }

    private static int countOccurrences(String haystack, String needle) {
        int count = 0;
        int index = 0;
        while ((index = haystack.indexOf(needle, index)) != -1) {
            count++;
            index += needle.length();
        }
        return count;
    }
}
