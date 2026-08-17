package com.n4monitor.flink;

import com.n4monitor.alarm.model.AlarmStage;
import com.n4monitor.alarm.model.AlarmStatus;
import com.n4monitor.alarm.model.PointAlarmState;
import com.n4monitor.alarm.model.PointData;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.flink.api.common.functions.OpenContext;
import org.apache.flink.api.common.state.ValueState;
import org.apache.flink.api.common.state.ValueStateDescriptor;
import org.apache.flink.streaming.api.functions.KeyedProcessFunction;
import org.apache.flink.util.Collector;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import redis.clients.jedis.Jedis;
import redis.clients.jedis.JedisPool;
import redis.clients.jedis.JedisPoolConfig;

/**
 * Stage 3: rule lookup + alarm lifecycle evaluation (stateful, keyed by path).
 * <p>
 * Reads rules from Redis per-message (so dynamic rule updates are picked up
 * without restart), evaluates, and manages alarm lifecycle. The preceding
 * {@code keyBy(path)} guarantees all messages for one point reach the same
 * task instance, making the read-modify-write on alarm:active race-free.
 * <p>
 * This class is the orchestration layer; collaborators extracted from the
 * former monolith: {@link PointMetaParser} (metadata/path parsing),
 * {@link RuleEvaluator} (threshold/enum evaluation + deadband gate),
 * {@link RedisStateStore} (all Redis reads/writes/publishes) and
 * {@link ConsoleFormatter} (stdout lines).
 */
public class AlarmProcessFunction extends KeyedProcessFunction<String, PointData, String> {

    private static final Logger log = LoggerFactory.getLogger(AlarmProcessFunction.class);

    private final String redisHost;
    private final int redisPort;
    private final String redisPassword;
    private final String rulesNumericKey;
    private final String rulesEnumKey;
    private final String latestRedisKey;
    private final String activeRedisKey;
    private final String notificationChannel;
    private final String realtimeChannel;
    private final String pointRuleMapKey;

    private transient JedisPool jedisPool;
    private transient ObjectMapper objectMapper;
    private transient RuleEvaluator ruleEvaluator;
    private transient RedisStateStore stateStore;

    /**
     * Pending alarm state held per key (path) while a delay-confirmation
     * timer is running. Cleared once the timer fires (alarm confirmed) or
     * the value returns to normal / changes level (timer cancelled).
     * <p>
     * Stored in Flink keyed state rather than Redis so the timer
     * and its payload live together and add no Redis round-trips.
     */
    private transient ValueState<PendingAlarm> pendingState;

    /** Redis socket read/connect timeout (ms). The Jedis default of 2000ms is too
     *  tight for a remote Redis over the public internet, where occasional RTT
     *  spikes exceed 2s and trigger SocketTimeoutException. 5s absorbs the spikes. */
    private static final int REDIS_TIMEOUT_MS = 5000;

    public AlarmProcessFunction(String redisHost, int redisPort, String redisPassword,
                                String rulesNumericKey, String rulesEnumKey,
                                String latestRedisKey, String activeRedisKey,
                                String notificationChannel, String realtimeChannel,
                                String pointRuleMapKey) {
        this.redisHost = redisHost;
        this.redisPort = redisPort;
        this.redisPassword = redisPassword;
        this.rulesNumericKey = rulesNumericKey;
        this.rulesEnumKey = rulesEnumKey;
        this.latestRedisKey = latestRedisKey;
        this.activeRedisKey = activeRedisKey;
        this.notificationChannel = notificationChannel;
        this.realtimeChannel = realtimeChannel;
        this.pointRuleMapKey = pointRuleMapKey;
    }

    @Override
    public void open(OpenContext openContext) {
        // Use a pool instead of a bare Jedis: the bare connection has a 2s default
        // timeout (too tight for remote Redis) and a single broken connection is
        // reused after a SocketTimeoutException, causing cascading failures. The
        // pool validates/closes broken connections on return and gives each
        // message a fresh, healthy connection.
        JedisPoolConfig poolConfig = new JedisPoolConfig();
        poolConfig.setMaxTotal(8);
        poolConfig.setMaxIdle(4);
        poolConfig.setMinIdle(1);
        // Validate connections borrowed from the pool so a stale/broken socket
        // is detected (and replaced) before we issue a command on it.
        poolConfig.setTestOnBorrow(true);
        poolConfig.setTestWhileIdle(true);

        jedisPool = new JedisPool(poolConfig, redisHost, redisPort,
                REDIS_TIMEOUT_MS, redisPassword);
        objectMapper = new ObjectMapper().findAndRegisterModules();
        ruleEvaluator = new RuleEvaluator(objectMapper, rulesNumericKey, rulesEnumKey);
        stateStore = new RedisStateStore(objectMapper, latestRedisKey, activeRedisKey,
                notificationChannel, realtimeChannel);

        // Per-key pending-alarm state for delay confirmation. Keyed by the
        // process function's key (path). One pending record per point at most.
        pendingState = getRuntimeContext().getState(
                new ValueStateDescriptor<>("pending-alarm", PendingAlarm.class));

        log.info("Alarm processor started");
        log.info("Rules numeric key: {}", rulesNumericKey);
        log.info("Rules enum key: {}", rulesEnumKey);
        log.info("Latest state Redis key: {}", latestRedisKey);
        log.info("Active alarm Redis key: {}", activeRedisKey);
        log.info("Point rule map key: {}", pointRuleMapKey);
        log.info("Redis timeout: {}ms", REDIS_TIMEOUT_MS);
    }

    @Override
    public void processElement(PointData point, Context ctx, Collector<String> out) throws Exception {
        try {
            String logLine = processMessage(point, ctx, out);
            if (logLine != null) out.collect(logLine);
        } catch (Exception e) {
            // Never let an exception escape - an uncaught exception fails the
            // Flink task and tears down the whole job. Log and skip the offending
            // message so the stream keeps processing subsequent data.
            log.error("Uncaught error processing message, skipping: {} - {}",
                    e.getClass().getSimpleName(), e.getMessage(), e);
            out.collect("[SKIP] uncaught error: " + e.getMessage());
        }
    }

    private String processMessage(PointData point, Context ctx, Collector<String> out) throws Exception {
        String path = point.getMqttPath();
        if (path == null) return "[SKIP] path is null";

        // Borrow a healthy connection from the pool for this message and return
        // it in finally. The pool closes broken connections on return, so a
        // SocketTimeoutException no longer leaves a stale socket reused for the
        // next message (which caused cascading failures with the bare Jedis).
        Jedis jedis = jedisPool.getResource();
        try {

        // 1. Resolve this point's metadata (type + alarm_code) from Redis in a single hget.
        //    The hash value is {"type":"numeric","alarm_code":"..."}; type drives rule
        //    evaluation routing and alarm_code selects the rule. Both come from the same
        //    point_deployment row so they stay consistent.
        PointMetaParser.PointMeta meta =
                PointMetaParser.parse(objectMapper, jedis.hget(pointRuleMapKey, path), path);
        String type = meta.type();
        String alarmName = meta.alarmName();
        // Backfill the type onto the point object so downstream state (latest/active/TDengine)
        // carries the resolved type instead of the (now absent) message type field.
        point.setType(type.isEmpty() ? null : type);

        String pointName = PointMetaParser.extractPointName(path);
        String ts = ConsoleFormatter.formatTimestamp(point.getTimestamp());

        // 2. Filter string type - no alarm rules
        if ("string".equalsIgnoreCase(type)) {
            // Still write latest state for TDengine sync
            PointAlarmState normalState = buildState(point, null, null, null, null, null);
            stateStore.writeLatestState(jedis, normalState);
            return ConsoleFormatter.stringSkipped(ts, pointName, point.getValue());
        }

        // 3. Empty alarm_code = no alarm rule = skip alarm evaluation (no _00 fallback).
        if (alarmName == null || alarmName.isEmpty()) {
            // No alarm_code configured for this point: skip alarm evaluation, write normal state.
            PointAlarmState normalState = buildState(point, null, null, null, null, null);
            stateStore.writeLatestState(jedis, normalState);
            return ConsoleFormatter.noAlarmCode(ts, pointName, point.getValue(), type);
        }

        // 4. Look up rule from Redis based on type
        RuleEvaluator.EvalResult triggered = null;
        String ruleType = type != null ? type.toLowerCase() : "";

        switch (ruleType) {
            case "boolean", "enum" -> triggered = ruleEvaluator.evaluateEnum(jedis, alarmName, point.getValue());
            case "numeric" -> triggered = ruleEvaluator.evaluateNumeric(jedis, alarmName, point.getValue());
            default -> {
                // Unknown type or null type
                PointAlarmState normalState = buildState(point, null, null, null, null, null);
                stateStore.writeLatestState(jedis, normalState);
                return ConsoleFormatter.unknownType(ts, pointName, point.getValue(), type);
            }
        }

        if (triggered == null) {
            // No rule found for this point
            PointAlarmState normalState = buildState(point, null, null, null, null, null);
            stateStore.writeLatestState(jedis, normalState);
            return ConsoleFormatter.noRuleFound(ts, pointName, point.getValue(), type);
        }

        // Unknown enum value: no configured rule matches this value. Skip
        // alarm evaluation entirely - do not touch alarm:active and do not
        // enter the lifecycle/pending state machine, otherwise an unknown
        // value would be treated as normal and wrongly recover an ongoing
        // alarm. Only refresh alarm:latest so the live display stays current.
        if (triggered.skipped()) {
            PointAlarmState latestState = buildState(point, null, null, null, null, null);
            stateStore.writeLatestState(jedis, latestState);
            return ConsoleFormatter.unknownEnumSkipped(ts, pointName, point.getValue());
        }

        boolean isAlarm = triggered.isAlarm();
        String level = triggered.level();
        String severity = triggered.severity();
        String alarmMessage = triggered.message();
        Integer timeDelay = triggered.timeDelay();
        String deadband = triggered.deadband();

        // 5. Build current state and resolve stage / duration against active history
        PointAlarmState current = buildState(point, level, severity, alarmMessage, timeDelay, deadband);
        PointAlarmState history = stateStore.readActive(jedis, path);
        long intervalSeconds = computeIntervalSeconds(history, current);

        // 5a. Deadband (hysteresis) gate for alarm recovery.
        // Only applies to numeric points: when the value no longer trips any
        // raw threshold (isAlarm == false) but an alarm is still active, the
        // deadband keeps the alarm alive until the value moves past the
        // recovery boundary (threshold -/+ deadband). This runs before the
        // pending state machine and lifecycle block so both treat a
        // within-deadband value as "still in alarm" (continue / keep timer)
        // instead of recovering. Level escalation/de-escalation is unaffected
        // because the gate only fires when isAlarm is already false.
        if (!isAlarm && "numeric".equals(ruleType)
                && history != null && !isBlank(history.getLevel())
                && !AlarmStage.END.equals(history.getStage())) {
            if (ruleEvaluator.isInDeadband(jedis, alarmName, history, current)) {
                isAlarm = true;
                level = history.getLevel();
                severity = history.getSeverity();
                alarmMessage = history.getMessage();
                current = buildState(point, level, severity, alarmMessage, timeDelay, deadband);
            }
        }

        // 5b. Delay-confirmation handling. If a pending (unconfirmed) alarm
        // exists for this key, the point is in its delay window: the alarm
        // has crossed the threshold but has not yet been confirmed for the
        // full timeDelay. While pending, no record is written to alarm:active
        // and nothing is published - confirmation happens in onTimer().
        //   - same level arrives  -> keep the timer, refresh value/timestamp
        //   - different level     -> cancel old timer, start a fresh delay
        //   - normal value        -> cancel timer, drop the pending alarm
        // A pending alarm only coexists with history==null or stage=="end"
        // (it is created before any active record is written), so the
        // lifecycle block below only runs once the alarm is confirmed.
        PendingAlarm pending = pendingState.value();
        if (pending != null) {
            if (isAlarm && level.equals(pending.state.getLevel())) {
                // Same level keeps counting. Refresh the latest value/timestamp
                // so onTimer fires the alarm with the freshest reading.
                pending.state.setValue(point.getValue());
                pending.state.setTimestamp(point.getTimestamp());
                pendingState.update(pending);
                // latest stays normal (no level) while still pending.
                PointAlarmState latestState = buildState(current, null, null, null, null, null);
                stateStore.writeLatestState(jedis, latestState);
                long elapsedMs = (point.getTimestamp() != null ? point.getTimestamp() : 0L) - pending.firstEventTime;
                return ConsoleFormatter.pendingElapsed(ts, pointName, point.getValue(),
                        level, Math.max(0, elapsedMs / 1000));
            } else if (isAlarm) {
                // Different alarm level: abandon the old pending alarm and
                // restart the delay for the new level (matches the spec:
                // reaching a different threshold resets the timer).
                ctx.timerService().deleteProcessingTimeTimer(pending.timerTarget);
                registerPendingDelay(ctx, current, timeDelay);
                PointAlarmState latestState = buildState(current, null, null, null, null, null);
                stateStore.writeLatestState(jedis, latestState);
                return ConsoleFormatter.pendingReset(ts, pointName, point.getValue(), level,
                        pending.state.getLevel(), timeDelay != null ? timeDelay : 0);
            } else {
                // Value returned to normal during the delay window: cancel
                // the pending alarm before it is ever confirmed.
                ctx.timerService().deleteProcessingTimeTimer(pending.timerTarget);
                pendingState.clear();
                stateStore.writeLatestState(jedis, current);
                return ConsoleFormatter.pendingCancelled(ts, pointName, point.getValue(),
                        pending.state.getLevel());
            }
        }

        // 6. Resolve alarm lifecycle and update alarm:active accordingly
        if (history == null || isBlank(history.getLevel())) {
            // No active alarm record (or last record was non-alarm)
            if (isAlarm) {
                // Fresh alarm start. If a delay is configured, enter the
                // pending state and register a processing-time timer instead
                // of firing immediately; onTimer() confirms the alarm once
                // the value has stayed above threshold for timeDelay seconds.
                if (timeDelay != null && timeDelay > 0) {
                    return enterPending(ctx, jedis, current, timeDelay, ts, pointName, out);
                }
                // No delay: fire immediately.
                markAlarmStart(current, "Start", current.getTimestamp());
                stateStore.writeActiveState(jedis, current);
                stateStore.publishNotificationChannel(jedis, current);
                stateStore.publishRealTimeChannel(jedis, current, current.getTimestamp());
            }
            // else: still normal, nothing to do in alarm:active
        } else if (AlarmStage.END.equals(history.getStage())) {
            // History is a frozen (recovered) alarm record
            if (isAlarm) {
                // Re-alarmed after recovery: new start, drop the frozen record.
                // Same delay rule as a fresh start.
                if (timeDelay != null && timeDelay > 0) {
                    return enterPending(ctx, jedis, current, timeDelay, ts, pointName, out);
                }
                // New alarm cycle begins: reset the start time to this tick
                markAlarmStart(current, "Start", current.getTimestamp());
                stateStore.writeActiveState(jedis, current);
                stateStore.publishNotificationChannel(jedis, current);
                stateStore.publishRealTimeChannel(jedis, current, current.getTimestamp());
            }
            // else: still normal after recovery -> idempotent skip (keep frozen record as-is)
        } else {
            // History is an ongoing alarm (stage start/continue)
            if (isAlarm) {
                if (history.getLevel().equals(current.getLevel())) {
                    // Same level continues: preserve ack state so it survives across ticks
                    current.setStage(AlarmStage.CONTINUE);
                    current.setStageMessage("Continue");
                    current.setDuration(String.valueOf(intervalSeconds));
                    current.setStatus(history.getStatus());
                    current.setAckBy(history.getAckBy());
                    // Continue uses the original alarm start time (historical timestamp)
                    current.setAlarmStartTime(history.getAlarmStartTime());
                } else {
                    // Level escalated (e.g. High -> High-High): close old phase, start new
                    history.setStage(AlarmStage.END);
                    history.setStageMessage("End - " + history.getLevel() + " to " + current.getLevel());
                    history.setDuration(String.valueOf(intervalSeconds));
                    stateStore.publishRealTimeChannel(jedis, history, current.getTimestamp());

                    // Escalation starts a new alarm phase: reset start time to this tick
                    markAlarmStart(current, "Start - " + history.getLevel() + " to " + current.getLevel(),
                            current.getTimestamp());
                    stateStore.publishNotificationChannel(jedis, current);
                    stateStore.publishRealTimeChannel(jedis, current, current.getTimestamp());
                }
                stateStore.writeActiveState(jedis, current);
            } else {
                // Alarm recovered
                if (AlarmStatus.ACK.equals(history.getStatus())) {
                    // Already acknowledged + recovered -> remove from active, signal frontend.
                    // Persist the end record to TDengine first (publishRealTimeChannel sets stage/duration
                    // and serializes), then broadcast a cleared signal to the frontend.
                    stateStore.removeActive(jedis, path);
                    history.setStage(AlarmStage.END);
                    history.setStageMessage("End");
                    history.setDuration(String.valueOf(intervalSeconds));
                    stateStore.publishNotificationChannel(jedis, history);
                    stateStore.publishRealTimeChannel(jedis, history, current.getTimestamp());
                } else {
                    // Not yet acknowledged -> freeze the alarm record with final duration
                    history.setStage(AlarmStage.END);
                    history.setStageMessage("End");
                    history.setDuration(String.valueOf(intervalSeconds));
                    stateStore.writeActiveState(jedis, history);
                    // Notify with the alarm's historical start time (current carries the recovered value
                    // but should report when the alarm began).
                    stateStore.publishNotificationChannel(jedis, history);
                    stateStore.publishRealTimeChannel(jedis, history, current.getTimestamp());
                }
            }
        }

        // 7. Always write the real-time value to alarm:latest
        stateStore.writeLatestState(jedis, current);

        // 8. Console output
        if (isAlarm) {
            return ConsoleFormatter.alarm(ts, severity, current.getStage(), pointName,
                    point.getValue(), level, alarmMessage);
        }
        return ConsoleFormatter.normal(ts, pointName, point.getValue());

        } finally {
            jedis.close();
        }
    }

    @Override
    public void close() {
        if (jedisPool != null) jedisPool.close();
    }

    // ── Delay-confirmation helpers ─────────────────────────────────────────

    /**
     * Enter the pending (delay-confirmation) state for the current key:
     * register a processing-time timer {@code timeDelay} seconds ahead and
     * stash the would-be alarm state so {@link #onTimer} can fire it later.
     * Returns the console log line for this message.
     */
    private String enterPending(Context ctx, Jedis jedis, PointAlarmState current,
                                int timeDelay, String ts, String pointName, Collector<String> out) throws Exception {
        // The confirmation window starts from the data's own timestamp (first
        // threshold crossing), not from processing time. Because the producer
        // stamps messages with wall-clock time at send, event time ~= processing
        // time in practice, but using the event time keeps the delay anchored to
        // the data domain as required.
        PendingAlarm pending = registerPendingDelay(ctx, current, timeDelay);

        // While pending, alarm:latest must reflect a NORMAL state: the alarm
        // has not been confirmed, so level/severity/message are empty. Only
        // the live value/path/type/timestamp are written so TDengine snapshots
        // and charts show the current reading without a phantom alarm.
        PointAlarmState latestState = buildState(current, null, null, null, null, null);
        stateStore.writeLatestState(jedis, latestState);

        long remainingMs = pending.timerTarget - ctx.timerService().currentProcessingTime();
        return ConsoleFormatter.pendingDelay(ts, pointName, current.getValue(), current.getLevel(),
                timeDelay, remainingMs / 1000.0);
    }

    /**
     * Stash the would-be alarm in keyed state and register its confirmation
     * timer at {@code firstEventTime + delay}, floored to the current
     * processing time (a late message whose delay window already passed
     * fires promptly instead of registering a timer in the past).
     * Shared by the fresh-pending entry and the level-change reset path.
     */
    private PendingAlarm registerPendingDelay(Context ctx, PointAlarmState current, Integer timeDelay) throws Exception {
        long firstEventTime = current.getTimestamp() != null ? current.getTimestamp() : 0L;
        long target = firstEventTime + (timeDelay != null ? timeDelay : 0) * 1000L;
        long nowProc = ctx.timerService().currentProcessingTime();
        if (target < nowProc) {
            target = nowProc;
        }
        // alarmStartTime is NOT set here: the alarm has not truly started yet.
        // It is set in onTimer when the alarm is confirmed, to the moment of
        // confirmation (the triggering tick's time).
        PendingAlarm pending = new PendingAlarm(current, firstEventTime, target);
        pendingState.update(pending);
        ctx.timerService().registerProcessingTimeTimer(target);
        return pending;
    }

    /**
     * Processing-time timer callback: a pending alarm's delay window has
     * elapsed with no normal value (and no level change) observed, so the
     * alarm is now confirmed. Promote the pending state into alarm:active
     * and publish the start notification + TDengine realtime record.
     * <p>
     * Uses processing time (wall clock) rather than event time so the alarm
     * still fires when no further data arrives during the delay window -
     * matching the spec: "if no new data arrives, the value is assumed to
     * hold and the alarm fires after the delay".
     */
    @Override
    public void onTimer(long timestamp, OnTimerContext ctx, Collector<String> out) throws Exception {
        PendingAlarm pending = pendingState.value();
        if (pending == null) {
            // Timer fired but pending was already cleared (e.g. value went
            // normal and the delete raced). Nothing to do.
            return;
        }
        // Defensive: only fire the timer we registered. A stale timer for a
        // previously cancelled pending (timerTarget mismatch) is ignored.
        if (pending.timerTarget != timestamp) {
            return;
        }

        PointAlarmState state = pending.state;
        // The alarm is confirmed NOW (timer fired). alarmStartTime is the
        // confirmation moment, not the first-threshold-crossing time: the
        // delay window means the threshold crossing alone did not constitute
        // an alarm. Use the timer's processing time as the authoritative
        // "alarm began" instant.
        long confirmProcTime = timestamp;  // == pending.timerTarget, verified above
        try (Jedis jedis = jedisPool.getResource()) {
            // Confirm the alarm: mark as a fresh start and persist to active.
            markAlarmStart(state, "Start", confirmProcTime);
            // Force the state's own timestamp to the confirmation moment too,
            // so all four writes share one consistent timestamp:
            //   writeActiveState      -> getAlarmStartTime()        = confirmProcTime
            //   publishNotification   -> getAlarmStartTime()        = confirmProcTime
            //   publishRealTime       -> passed dataTimestamp       = confirmProcTime
            //   writeLatestState      -> state.getTimestamp()       = confirmProcTime (set here)
            // Without this, writeLatestState would carry the last pending tick's
            // event time, disagreeing with the other three.
            state.setTimestamp(confirmProcTime);
            stateStore.writeActiveState(jedis, state);
            stateStore.publishNotificationChannel(jedis, state);
            stateStore.publishRealTimeChannel(jedis, state, confirmProcTime);
            stateStore.writeLatestState(jedis, state);
        }

        pendingState.clear();

        String ts = ConsoleFormatter.formatTimestamp(confirmProcTime);
        String pointName = PointMetaParser.extractPointName(state.getMqttPath());
        out.collect(ConsoleFormatter.delayConfirmed(ts, state.getSeverity(), state.getStage(),
                pointName, state.getValue(), state.getLevel()));
    }

    // ── State construction ──────────────────────────────────────────────────

    /**
     * Mark a state as a fresh alarm start: stage, duration, ack fields and
     * the alarm start time. Shared by immediate starts, re-alarms after
     * recovery, level escalations and the onTimer confirmation.
     */
    private static void markAlarmStart(PointAlarmState state, String stageMessage, Long alarmStartTs) {
        state.setStage(AlarmStage.START);
        state.setStageMessage(stageMessage);
        state.setDuration("0");
        state.setAckBy("-");
        state.setStatus(AlarmStatus.UNACK);
        state.setAlarmStartTime(alarmStartTs);
    }

    private PointAlarmState buildState(PointData point, String level, String severity,
                                       String message, Integer timeDelay, String deadband) {
        PointAlarmState state = new PointAlarmState();
        state.setClientId(point.getClientId());
        state.setMqttPath(point.getMqttPath());
        state.setPath(point.getPath());
        state.setValue(point.getValue());
        state.setType(point.getType());
        state.setTimestamp(point.getTimestamp());

        if (level != null) {
            state.setLevel(level);
            state.setSeverity(severity);
            state.setMessage(message);
        }
        // timeDelay and deadband are rule metadata needed regardless of
        // whether this tick is an alarm - the deadband hysteresis gate reads
        // current.getDeadband() even when level is null (normal value), so
        // they must always be set.
        state.setTimeDelay(timeDelay);
        state.setDeadband(deadband);

        state.setSource(PointMetaParser.extractSource(point.getMqttPath()));
        state.setLocation(PointMetaParser.extractLocation(point.getMqttPath()));
        state.setDuration("0");
        state.setStage("");
        state.setStageMessage("");
        return state;
    }

    /**
     * Compute the alarm duration in seconds from the alarm start to the current tick.
     * <p>
     * Since the active table now persists the alarm start time as the record's
     * timestamp (historical timestamp), history.getTimestamp() equals the alarm
     * start time. The duration is therefore the elapsed seconds from that start
     * time to the current data tick. No accumulation of a previous duration
     * value is needed (that would double-count).
     */
    private long computeIntervalSeconds(PointAlarmState history, PointAlarmState current) {
        if (history == null || history.getTimestamp() == null || current.getTimestamp() == null) {
            return 0;
        }
        long diff = current.getTimestamp() - history.getTimestamp();
        return Math.max(0, diff / 1000);
    }

    private static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }
}
