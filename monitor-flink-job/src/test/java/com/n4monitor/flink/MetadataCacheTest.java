package com.n4monitor.flink;

import com.n4monitor.entity.AlarmRuleEnumEntity;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Per-subtask TTL caches (rule definitions, point metadata) must cut the
 * Redis hget count to one per key per TTL window - including negative
 * (no-rule / unmapped-path) lookups - without changing evaluation results.
 */
class MetadataCacheTest {

    private static final String PATH = "/sites/A1/fcu1/temp1";
    private static final long TS = 1_700_000_012_345L;

    private final AlarmHarness h = new AlarmHarness();

    private int hgets(String key) {
        return h.hgetCounts.getOrDefault(key, 0);
    }

    @Test
    void pointMetadataIsCachedPerPath() throws Exception {
        h.seedMeta(PATH, "numeric", "RULE1");
        AlarmProcessFunction fn = h.newFunction();
        h.process(fn, h.point(PATH, "50", TS));
        h.process(fn, h.point(PATH, "51", TS + 1000));
        h.process(fn, h.point(PATH, "52", TS + 2000));
        assertThat(hgets(AlarmHarness.POINT_RULE_MAP_KEY)).isEqualTo(1);
    }

    @Test
    void missingPointMetadataIsNegativelyCached() throws Exception {
        // No seedMeta: the lookup misses in Redis too, and that miss is cached.
        AlarmProcessFunction fn = h.newFunction();
        h.process(fn, h.point(PATH, "50", TS));
        h.process(fn, h.point(PATH, "51", TS + 1000));
        assertThat(h.consoleOutput()).contains("no alarm_code");
        assertThat(hgets(AlarmHarness.POINT_RULE_MAP_KEY)).isEqualTo(1);
    }

    @Test
    void numericRuleIsCachedAcrossFramesAndPaths() throws Exception {
        h.seedMeta(PATH, "numeric", "RULE1");
        h.seedNumericRule("RULE1", h.numericRule("1", 0));
        String otherPath = "/sites/A1/fcu1/temp2";
        h.seedMeta(otherPath, "numeric", "RULE1");
        AlarmProcessFunction fn = h.newFunction();
        h.process(fn, h.point(PATH, "95", TS));          // alarm frame loads the rule
        h.process(fn, h.point(PATH, "50", TS + 1000));   // recovery: deadband re-check
        h.process(fn, h.point(otherPath, "95", TS + 2000)); // another path, same rule
        // One rule load total: evaluation + deadband re-check + second path.
        assertThat(hgets(AlarmHarness.RULES_NUMERIC_KEY)).isEqualTo(1);
        assertThat(h.consoleOutput()).contains("level=High");
    }

    @Test
    void enumRulesAreCached() throws Exception {
        AlarmRuleEnumEntity rule = new AlarmRuleEnumEntity();
        rule.setAlarmName("RULE1");
        rule.setEnumValue("1");
        rule.setSeverity("Major");
        rule.setMessage("Fault");
        h.seedMeta(PATH, "enum", "RULE1");
        h.seedEnumRules("RULE1", List.of(rule));
        AlarmProcessFunction fn = h.newFunction();
        h.process(fn, h.point(PATH, "1", TS));           // alarm frame loads the rules
        h.process(fn, h.point(PATH, "0", TS + 1000));    // unknown value: still cached
        assertThat(h.consoleOutput()).contains("unknown enum value, skipped");
        assertThat(hgets(AlarmHarness.RULES_ENUM_KEY)).isEqualTo(1);
    }

    @Test
    void cacheEntriesExpireAfterTtl() throws Exception {
        TtlCache<String> cache = new TtlCache<>(50, TimeUnit.MILLISECONDS);
        int[] loads = {0};
        assertThat(cache.get("k", key -> { loads[0]++; return "v"; })).isEqualTo("v");
        assertThat(cache.get("k", key -> { loads[0]++; return "v"; })).isEqualTo("v");
        assertThat(loads[0]).isEqualTo(1);
        Thread.sleep(120);
        // After expiry the loader runs again, picking up changed metadata.
        assertThat(cache.get("k", key -> { loads[0]++; return "v2"; })).isEqualTo("v2");
        assertThat(loads[0]).isEqualTo(2);
    }

    @Test
    void nullResultsAreCached() throws Exception {
        TtlCache<String> cache = new TtlCache<>(10, TimeUnit.SECONDS);
        int[] loads = {0};
        assertThat(cache.get("k", key -> { loads[0]++; return null; })).isNull();
        assertThat(cache.get("k", key -> { loads[0]++; return null; })).isNull();
        assertThat(loads[0]).isEqualTo(1);
    }
}
