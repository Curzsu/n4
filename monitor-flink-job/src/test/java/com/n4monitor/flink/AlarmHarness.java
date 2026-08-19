package com.n4monitor.flink;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.n4monitor.alarm.model.PointAlarmState;
import com.n4monitor.alarm.model.PointData;
import com.n4monitor.entity.AlarmRuleEnumEntity;
import com.n4monitor.entity.AlarmRuleNumericEntity;
import org.apache.flink.api.common.state.ValueState;
import org.apache.flink.streaming.api.TimerService;
import org.apache.flink.streaming.api.functions.KeyedProcessFunction;
import org.apache.flink.util.Collector;
import redis.clients.jedis.Jedis;
import redis.clients.jedis.JedisPool;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Test harness wiring a real {@link AlarmProcessFunction} to an in-memory Redis
 * fake, a deterministic timer service and an in-memory keyed state, so tests
 * drive the production processElement/onTimer flow without a Flink cluster or
 * a live Redis. All production fields are injected via reflection, mirroring
 * what open() would set up.
 */
final class AlarmHarness {

    static final String RULES_NUMERIC_KEY = "alarm:rules:numeric";
    static final String RULES_ENUM_KEY = "alarm:rules:enum";
    static final String LATEST_KEY = "alarm:latest";
    static final String ACTIVE_KEY = "alarm:active";
    static final String NOTIFY_CHANNEL = "alarm:notifications";
    static final String REALTIME_CHANNEL = "alarm:realtime";
    static final String PERSISTENCE_CHANNEL = "alarm:persist";
    static final String POINT_RULE_MAP_KEY = "alarm:point:rule";

    final ObjectMapper mapper = new ObjectMapper().findAndRegisterModules();
    /** In-memory Redis hashes: key -> (field -> value). */
    final Map<String, Map<String, String>> hashes = new HashMap<>();
    /** Published messages in order: [channel, message]. */
    final List<String[]> published = new ArrayList<>();
    final FakeTimerService timers = new FakeTimerService();
    final InMemoryValueState<PendingAlarm> pendingState = new InMemoryValueState<>();
    /** Everything collected by the operator (console lines). */
    final List<String> sink = new ArrayList<>();

    private final Jedis jedis = mock(Jedis.class);

    AlarmHarness() {
        when(jedis.hget(anyString(), anyString())).thenAnswer(inv ->
                hashes.getOrDefault(inv.getArgument(0, String.class), Map.of())
                        .get(inv.getArgument(1, String.class)));
        when(jedis.hset(anyString(), anyString(), anyString())).thenAnswer(inv -> {
            hashes.computeIfAbsent(inv.getArgument(0, String.class), k -> new HashMap<>())
                    .put(inv.getArgument(1, String.class), inv.getArgument(2, String.class));
            return 1L;
        });
        // Production always deletes exactly one hash field; a single-element
        // matcher is used because array matchers do not hit single vararg calls.
        when(jedis.hdel(anyString(), anyString())).thenAnswer(inv -> {
            Map<String, String> hash = hashes.get(inv.getArgument(0, String.class));
            if (hash == null) return 0L;
            return hash.remove(inv.getArgument(1, String.class)) != null ? 1L : 0L;
        });
        when(jedis.publish(anyString(), anyString())).thenAnswer(inv -> {
            published.add(new String[]{inv.getArgument(0, String.class), inv.getArgument(1, String.class)});
            return 1L;
        });
    }

    /** Build the production function with all transient fields replaced by test doubles. */
    @SuppressWarnings("unchecked")
    AlarmProcessFunction newFunction() throws Exception {
        AlarmProcessFunction fn = new AlarmProcessFunction("localhost", 6379, null,
                RULES_NUMERIC_KEY, RULES_ENUM_KEY, LATEST_KEY, ACTIVE_KEY,
                NOTIFY_CHANNEL, REALTIME_CHANNEL, PERSISTENCE_CHANNEL, POINT_RULE_MAP_KEY);
        JedisPool pool = mock(JedisPool.class);
        when(pool.getResource()).thenReturn(jedis);
        setField(fn, "jedisPool", pool);
        setField(fn, "objectMapper", mapper);
        setField(fn, "pendingState", pendingState);
        // Mirror the collaborator wiring that open() performs in production.
        setField(fn, "ruleEvaluator", new RuleEvaluator(mapper, RULES_NUMERIC_KEY, RULES_ENUM_KEY));
        setField(fn, "stateStore", new RedisStateStore(mapper, LATEST_KEY, ACTIVE_KEY,
                NOTIFY_CHANNEL, REALTIME_CHANNEL, PERSISTENCE_CHANNEL));
        return fn;
    }

    void process(AlarmProcessFunction fn, PointData point) throws Exception {
        fn.processElement(point, mockContext(), collector());
    }

    Collector<String> collector() {
        return new Collector<String>() {
            @Override
            public void collect(String record) {
                sink.add(record);
            }

            @Override
            public void close() {
            }
        };
    }

    @SuppressWarnings("unchecked")
    KeyedProcessFunction<String, PointData, String>.Context mockContext() {
        KeyedProcessFunction<String, PointData, String>.Context ctx =
                (KeyedProcessFunction<String, PointData, String>.Context)
                        mock(KeyedProcessFunction.Context.class);
        when(ctx.timerService()).thenReturn(timers);
        return ctx;
    }

    @SuppressWarnings("unchecked")
    KeyedProcessFunction<String, PointData, String>.OnTimerContext mockTimerContext() {
        KeyedProcessFunction<String, PointData, String>.OnTimerContext ctx =
                (KeyedProcessFunction<String, PointData, String>.OnTimerContext)
                        mock(KeyedProcessFunction.OnTimerContext.class);
        when(ctx.timerService()).thenReturn(timers);
        return ctx;
    }

    // ── Seeding helpers ──────────────────────────────────────────────────────

    void seedMeta(String path, String type, String alarmCode) throws Exception {
        String json = String.format("{\"type\":\"%s\",\"alarm_code\":\"%s\"}", type, alarmCode);
        hash(POINT_RULE_MAP_KEY).put(path, json);
    }

    void seedNumericRule(String alarmName, AlarmRuleNumericEntity rule) throws Exception {
        hash(RULES_NUMERIC_KEY).put(alarmName, mapper.writeValueAsString(rule));
    }

    void seedEnumRules(String alarmName, List<AlarmRuleEnumEntity> rules) throws Exception {
        hash(RULES_ENUM_KEY).put(alarmName, mapper.writeValueAsString(rules));
    }

    /** Seed alarm:active directly (the JSON timestamp is whatever the state carries). */
    void seedActive(PointAlarmState state) throws Exception {
        hash(ACTIVE_KEY).put(state.getMqttPath(), mapper.writeValueAsString(state));
    }

    // ── Read-back helpers ────────────────────────────────────────────────────

    PointAlarmState activeOf(String path) throws Exception {
        String json = hashes.getOrDefault(ACTIVE_KEY, Map.of()).get(path);
        return json == null ? null : mapper.readValue(json, PointAlarmState.class);
    }

    PointAlarmState latestOf(String path) throws Exception {
        String json = hashes.getOrDefault(LATEST_KEY, Map.of()).get(path);
        return json == null ? null : mapper.readValue(json, PointAlarmState.class);
    }

    /** Published messages (as JSON trees) for one channel, in publish order. */
    List<JsonNode> publishedOn(String channel) throws Exception {
        List<JsonNode> result = new ArrayList<>();
        for (String[] msg : published) {
            if (channel.equals(msg[0])) result.add(mapper.readTree(msg[1]));
        }
        return result;
    }

    String consoleOutput() {
        return String.join("\n", sink);
    }

    // ── Fixture builders ─────────────────────────────────────────────────────

    PointData point(String path, String value, Long timestamp) {
        PointData p = new PointData();
        p.setClientId("test-client");
        p.setMqttPath(path);
        p.setValue(value);
        p.setTimestamp(timestamp);
        return p;
    }

    PointAlarmState activeState(String path, String level, String severity, String stage,
                                String status, long alarmStartTs) {
        PointAlarmState s = new PointAlarmState();
        s.setMqttPath(path);
        s.setValue("0");
        s.setType("numeric");
        s.setLevel(level);
        s.setSeverity(severity);
        s.setMessage("msg-" + level);
        s.setStage(stage);
        s.setStatus(status);
        s.setAckBy("Ack".equals(status) ? "op" : "-");
        s.setAlarmStartTime(alarmStartTs);
        // Production serializes active records with timestamp forced to the
        // alarm start time, so a seeded history must mirror that.
        s.setTimestamp(alarmStartTs);
        return s;
    }

    AlarmRuleNumericEntity numericRule(String deadband, Integer timeDelay) {
        AlarmRuleNumericEntity rule = new AlarmRuleNumericEntity();
        rule.setAlarmName("RULE1");
        rule.setDeadband(deadband);
        rule.setTimeDelay(timeDelay);
        rule.setHighHighValue("90");
        rule.setHighHighSeverity("Critical");
        rule.setHighHighMessage("HH exceeded");
        rule.setHighValue("80");
        rule.setHighSeverity("Major");
        rule.setHighMessage("High exceeded");
        rule.setLowValue("20");
        rule.setLowSeverity("Minor");
        rule.setLowMessage("Low below");
        rule.setLowLowValue("10");
        rule.setLowLowSeverity("Warning");
        rule.setLowLowMessage("LL below");
        return rule;
    }

    private Map<String, String> hash(String key) {
        return hashes.computeIfAbsent(key, k -> new HashMap<>());
    }

    private static void setField(Object target, String name, Object value) throws Exception {
        Field field = target.getClass().getDeclaredField(name);
        field.setAccessible(true);
        field.set(target, value);
    }

    /** Deterministic timer service capturing register/delete calls. */
    static class FakeTimerService implements TimerService {
        long now = 1_700_000_000_000L;
        final List<Long> registered = new ArrayList<>();
        final List<Long> deleted = new ArrayList<>();

        @Override
        public long currentProcessingTime() {
            return now;
        }

        @Override
        public long currentWatermark() {
            return Long.MIN_VALUE;
        }

        @Override
        public void registerProcessingTimeTimer(long time) {
            registered.add(time);
        }

        @Override
        public void deleteProcessingTimeTimer(long time) {
            deleted.add(time);
        }

        @Override
        public void registerEventTimeTimer(long time) {
        }

        @Override
        public void deleteEventTimeTimer(long time) {
        }
    }

    /** Minimal in-memory ValueState. */
    static class InMemoryValueState<T> implements ValueState<T> {
        private T value;

        @Override
        public T value() {
            return value;
        }

        @Override
        public void update(T value) {
            this.value = value;
        }

        @Override
        public void clear() {
            value = null;
        }
    }
}
