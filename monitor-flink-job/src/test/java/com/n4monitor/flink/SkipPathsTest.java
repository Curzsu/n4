package com.n4monitor.flink;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Characterization tests for the pre-evaluation skip paths: null path,
 * string-typed points, points without an alarm_code, unknown types and
 * missing metadata. All skip paths must still refresh alarm:latest.
 */
class SkipPathsTest {

    private static final String PATH = "/sites/A1/fcu1/temp1";
    private static final long TS = 1_700_000_012_345L;

    private final AlarmHarness h = new AlarmHarness();

    @Test
    void nullPathIsSkipped() throws Exception {
        h.process(h.newFunction(), h.point(null, "42", TS));
        assertThat(h.consoleOutput()).contains("[SKIP] path is null");
        assertThat(h.hashes.get(AlarmHarness.LATEST_KEY)).isNull();
    }

    @Test
    void stringTypeSkipsAlarmButWritesLatest() throws Exception {
        h.seedMeta(PATH, "string", "RULE1");
        h.process(h.newFunction(), h.point(PATH, "hello", TS));
        assertThat(h.consoleOutput()).contains("string type, skipped");
        assertThat(h.activeOf(PATH)).isNull();
        var latest = h.latestOf(PATH);
        assertThat(latest).isNotNull();
        assertThat(latest.getValue()).isEqualTo("hello");
        assertThat(latest.getType()).isEqualTo("string");
    }

    @Test
    void emptyAlarmCodeSkipsAlarmEvaluation() throws Exception {
        h.seedMeta(PATH, "numeric", "");
        h.process(h.newFunction(), h.point(PATH, "95", TS));
        assertThat(h.consoleOutput()).contains("no alarm_code");
        assertThat(h.activeOf(PATH)).isNull();
        assertThat(h.latestOf(PATH)).isNotNull();
    }

    @Test
    void missingMetadataBehavesLikeNoAlarmCode() throws Exception {
        // No alarm:point:rule entry at all: type and alarm_code stay empty.
        h.process(h.newFunction(), h.point(PATH, "95", TS));
        assertThat(h.consoleOutput()).contains("no alarm_code");
        assertThat(h.activeOf(PATH)).isNull();
        assertThat(h.latestOf(PATH)).isNotNull();
    }

    @Test
    void unknownTypeSkipsAlarmEvaluation() throws Exception {
        h.seedMeta(PATH, "complex", "RULE1");
        h.process(h.newFunction(), h.point(PATH, "3+4i", TS));
        assertThat(h.consoleOutput()).contains("unknown type: complex");
        assertThat(h.activeOf(PATH)).isNull();
        assertThat(h.latestOf(PATH)).isNotNull();
    }

    @Test
    void unparseableMetadataJsonFallsBackToNoAlarmCode() throws Exception {
        h.hashes.computeIfAbsent(AlarmHarness.POINT_RULE_MAP_KEY, k -> new java.util.HashMap<>())
                .put(PATH, "{not-json");
        h.process(h.newFunction(), h.point(PATH, "95", TS));
        assertThat(h.consoleOutput()).contains("no alarm_code");
        assertThat(h.activeOf(PATH)).isNull();
    }
}
