package com.n4monitor.flink;

import com.n4monitor.alarm.model.PointAlarmState;
import com.n4monitor.alarm.model.PointData;
import com.n4monitor.entity.AlarmRuleEnumEntity;
import com.n4monitor.entity.AlarmRuleNumericEntity;
import com.fasterxml.jackson.core.type.TypeReference;
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

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.List;

/**
 * Stage 3: rule lookup + alarm lifecycle evaluation (stateful, keyed by path).
 * <p>
 * Reads rules from Redis per-message (so dynamic rule updates are picked up
 * without restart), evaluates, and manages alarm lifecycle. The preceding
 * {@code keyBy(path)} guarantees all messages for one point reach the same
 * task instance, making the read-modify-write on alarm:active race-free.
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

    private static final DateTimeFormatter TS_FORMAT =
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss.SSS");

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
        String pointMetaJson = jedis.hget(pointRuleMapKey, path);
        String type = "";
        String alarmName = "";
        if (pointMetaJson != null && !pointMetaJson.isEmpty()) {
            try {
                var metaNode = objectMapper.readTree(pointMetaJson);
                if (metaNode.has("type") && !metaNode.get("type").isNull()) {
                    type = metaNode.get("type").asText().trim();
                }
                if (metaNode.has("alarm_code") && !metaNode.get("alarm_code").isNull()) {
                    alarmName = metaNode.get("alarm_code").asText().trim();
                }
            } catch (Exception e) {
                log.warn("Failed to parse point metadata for path {}: {}", path, e.getMessage());
            }
        }
        // Backfill the type onto the point object so downstream state (latest/active/TDengine)
        // carries the resolved type instead of the (now absent) message type field.
        point.setType(type.isEmpty() ? null : type);

        String pointName = extractPointName(path);
        String ts = formatTimestamp(point.getTimestamp());

        // 2. Filter string type - no alarm rules
        if ("string".equalsIgnoreCase(type)) {
            // Still write latest state for TDengine sync
            PointAlarmState normalState = buildState(point, null, null, null, null, null);
            writeLatestState(jedis, normalState);
            return String.format("\033[90m[%s] [%8s] %-40s | value=%s | string type, skipped\033[0m",
                    ts, "STRING", pointName, point.getValue());
        }

        // 3. Empty alarm_code = no alarm rule = skip alarm evaluation (no _00 fallback).
        if (alarmName == null || alarmName.isEmpty()) {
            // No alarm_code configured for this point: skip alarm evaluation, write normal state.
            PointAlarmState normalState = buildState(point, null, null, null, null, null);
            writeLatestState(jedis, normalState);
            return String.format("[%s] [%8s] %-40s | value=%s | no alarm_code, point=%s, type=%s",
                    ts, "NORULE", pointName, point.getValue(), pointName, type);
        }

        // 4. Look up rule from Redis based on type
        EvalResult triggered = null;
        String ruleType = type != null ? type.toLowerCase() : "";

        switch (ruleType) {
            case "boolean", "enum" -> triggered = evaluateEnum(jedis, alarmName, point.getValue());
            case "numeric" -> triggered = evaluateNumeric(jedis, alarmName, point.getValue());
            default -> {
                // Unknown type or null type
                PointAlarmState normalState = buildState(point, null, null, null, null, null);
                writeLatestState(jedis, normalState);
                return String.format("[%s] [%8s] %-40s | value=%s | unknown type: %s",
                        ts, "UNKNOWN", pointName, point.getValue(), type);
            }
        }

        if (triggered == null) {
            // No rule found for this point
            PointAlarmState normalState = buildState(point, null, null, null, null, null);
            writeLatestState(jedis, normalState);
            return String.format("[%s] [%8s] %-40s | value=%s | no rule found, point=%s, type=%s",
                    ts, "NORULE", pointName, point.getValue(), pointName, type);
        }

        // Unknown enum value: no configured rule matches this value. Skip
        // alarm evaluation entirely - do not touch alarm:active and do not
        // enter the lifecycle/pending state machine, otherwise an unknown
        // value would be treated as normal and wrongly recover an ongoing
        // alarm. Only refresh alarm:latest so the live display stays current.
        if (triggered.skipped()) {
            PointAlarmState latestState = buildState(point, null, null, null, null, null);
            writeLatestState(jedis, latestState);
            return String.format("[%s] [%8s] %-40s | value=%s | unknown enum value, skipped",
                    ts, "SKIP", pointName, point.getValue());
        }

        boolean isAlarm = triggered.isAlarm();
        String level = triggered.level;
        String severity = triggered.severity;
        String alarmMessage = triggered.message;
        Integer timeDelay = triggered.timeDelay;
        String deadband = triggered.deadband;

        // 5. Build current state and resolve stage / duration against active history
        PointAlarmState current = buildState(point, level, severity, alarmMessage, timeDelay, deadband);
        PointAlarmState history = readActive(jedis, path);
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
                && !"end".equals(history.getStage())) {
            if (isInDeadband(jedis, alarmName, history, current)) {
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
                writeLatestState(jedis, latestState);
                long elapsedMs = (point.getTimestamp() != null ? point.getTimestamp() : 0L) - pending.firstEventTime;
                return String.format("\033[96m[%s] [%8s] %-40s | value=%s | pending %s, %ds elapsed\033[0m",
                        ts, "PENDING", pointName, point.getValue(), level,
                        Math.max(0, elapsedMs / 1000));
            } else if (isAlarm) {
                // Different alarm level: abandon the old pending alarm and
                // restart the delay for the new level (matches the spec:
                // reaching a different threshold resets the timer).
                ctx.timerService().deleteProcessingTimeTimer(pending.timerTarget);
                long firstEventTime = current.getTimestamp() != null ? current.getTimestamp() : 0L;
                long target = firstEventTime + (timeDelay != null ? timeDelay : 0) * 1000L;
                long nowProc = ctx.timerService().currentProcessingTime();
                if (target < nowProc) target = nowProc;
                // alarmStartTime is intentionally NOT set here; onTimer sets it
                // to the confirmation moment when the alarm truly begins.
                PendingAlarm np = new PendingAlarm(current, firstEventTime, target);
                pendingState.update(np);
                ctx.timerService().registerProcessingTimeTimer(target);
                PointAlarmState latestState = buildState(current, null, null, null, null, null);
                writeLatestState(jedis, latestState);
                return String.format("\033[96m[%s] [%8s] %-40s | value=%s | pending %s (reset from %s), %ds delay\033[0m",
                        ts, "PENDING", pointName, point.getValue(), level,
                        pending.state.getLevel(), timeDelay != null ? timeDelay : 0);
            } else {
                // Value returned to normal during the delay window: cancel
                // the pending alarm before it is ever confirmed.
                ctx.timerService().deleteProcessingTimeTimer(pending.timerTarget);
                pendingState.clear();
                writeLatestState(jedis, current);
                return String.format("\033[92m[%s] [%8s] %-40s | value=%s | pending %s cancelled (normal)\033[0m",
                        ts, "CANCEL", pointName, point.getValue(), pending.state.getLevel());
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
                current.setStage("start");
                current.setStageMessage("Start");
                current.setDuration("0");
                current.setAckBy("-");
                current.setStatus("Unack");
                // Alarm start time = this data tick's timestamp (the historical timestamp)
                current.setAlarmStartTime(current.getTimestamp());
                writeActiveState(jedis, current);
//                    publishAlarm(current);
                publishNotificationChannel(jedis, current);
                publishRealTimeChannel(jedis, current, current.getTimestamp());
            }
            // else: still normal, nothing to do in alarm:active
        } else if ("end".equals(history.getStage())) {
            // History is a frozen (recovered) alarm record
            if (isAlarm) {
                // Re-alarmed after recovery: new start, drop the frozen record.
                // Same delay rule as a fresh start.
                if (timeDelay != null && timeDelay > 0) {
                    return enterPending(ctx, jedis, current, timeDelay, ts, pointName, out);
                }
                current.setStage("start");
                current.setStageMessage("Start");
                current.setDuration("0");
                current.setAckBy("-");
                current.setStatus("Unack");
                // New alarm cycle begins: reset the start time to this tick
                current.setAlarmStartTime(current.getTimestamp());
                writeActiveState(jedis, current);
//                    publishAlarm(current);
                publishNotificationChannel(jedis, current);
                publishRealTimeChannel(jedis, current, current.getTimestamp());
            }
            // else: still normal after recovery -> idempotent skip (keep frozen record as-is)
        } else {
            // History is an ongoing alarm (stage start/continue)
            if (isAlarm) {
                if (history.getLevel().equals(current.getLevel())) {
                    // Same level continues: preserve ack state so it survives across ticks
                    current.setStage("continue");
                    current.setStageMessage("Continue");
                    current.setDuration(String.valueOf(intervalSeconds));
                    current.setStatus(history.getStatus());
                    current.setAckBy(history.getAckBy());
                    // Continue uses the original alarm start time (historical timestamp)
                    current.setAlarmStartTime(history.getAlarmStartTime());
                } else {
                    // Level escalated (e.g. High -> High-High): close old phase, start new
                    history.setStage("end");
                    history.setStageMessage("End - " + history.getLevel() + " to " + current.getLevel());
                    history.setDuration(String.valueOf(intervalSeconds));
                    publishRealTimeChannel(jedis, history, current.getTimestamp());

                    current.setStage("start");
                    current.setStageMessage("Start - "+history.getLevel() + " to " + current.getLevel());
                    current.setDuration("0");
                    current.setAckBy("-");
                    current.setStatus("Unack");
                    // Escalation starts a new alarm phase: reset start time to this tick
                    current.setAlarmStartTime(current.getTimestamp());
                    publishNotificationChannel(jedis, current);
                    publishRealTimeChannel(jedis, current, current.getTimestamp());
                }
                writeActiveState(jedis, current);
//                    publishAlarm(current);
            } else {
                // Alarm recovered
                if ("Ack".equals(history.getStatus())) {
                    // Already acknowledged + recovered -> remove from active, signal frontend.
                    // Persist the end record to TDengine first (publishRealTimeChannel sets stage/duration
                    // and serializes), then broadcast a cleared signal to the frontend.
                    removeActive(jedis, path);
                    history.setStage("end");
                    history.setStageMessage("End");
                    history.setDuration(String.valueOf(intervalSeconds));
                    publishNotificationChannel(jedis, history);
                    publishRealTimeChannel(jedis, history, current.getTimestamp());
                } else {
                    // Not yet acknowledged -> freeze the alarm record with final duration
                    history.setStage("end");
                    history.setStageMessage("End");
                    history.setDuration(String.valueOf(intervalSeconds));
                    writeActiveState(jedis, history);
//                        publishAlarm(history);   // broadcast frozen record (frontend freezes duration)
                    // Notify with the alarm's historical start time (current carries the recovered value
                    // but should report when the alarm began).
//                        current.setAlarmStartTime(history.getAlarmStartTime());
                    publishNotificationChannel(jedis, history);
                    publishRealTimeChannel(jedis, history, current.getTimestamp());
                }
            }
        }

        // 7. Always write the real-time value to alarm:latest
        writeLatestState(jedis, current);

        // 8. Console output
        if (isAlarm) {
            String color = switch (severity) {
                case "Critical" -> "\033[91m";
                case "Major"    -> "\033[93m";
                case "Minor"    -> "\033[33m";
                case "Warning"  -> "\033[94m";
                default         -> "";
            };
            return String.format("%s[%s] [%8s] [%-8s] %-40s | value=%s | level=%s | %s\033[0m",
                    color, ts, severity, current.getStage(), pointName,
                    point.getValue(), level, alarmMessage);
        } else {
            return String.format("\033[92m[%s] [%8s] %-40s | value=%s | normal\033[0m",
                    ts, "NORMAL", pointName, point.getValue());
        }
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
        long firstEventTime = current.getTimestamp() != null ? current.getTimestamp() : 0L;
        long target = firstEventTime + timeDelay * 1000L;
        // Floor the timer target to "now" so a message that arrived late (its
        // event time + delay already in the past) fires promptly instead of
        // registering a timer in the past (which would trigger immediately
        // anyway, but this makes the intent explicit and avoids log noise).
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

        // While pending, alarm:latest must reflect a NORMAL state: the alarm
        // has not been confirmed, so level/severity/message are empty. Only
        // the live value/path/type/timestamp are written so TDengine snapshots
        // and charts show the current reading without a phantom alarm.
        PointAlarmState latestState = buildState(current, null, null, null, null, null);
        writeLatestState(jedis, latestState);

        long remainingMs = target - nowProc;
        return String.format("\033[96m[%s] [%8s] %-40s | value=%s | pending %s, %ds delay (%.1fs left)\033[0m",
                ts, "PENDING", pointName, current.getValue(), current.getLevel(),
                timeDelay, remainingMs / 1000.0);
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
            state.setStage("start");
            state.setStageMessage("Start");
            state.setDuration("0");
            state.setAckBy("-");
            state.setStatus("Unack");
            state.setAlarmStartTime(confirmProcTime);
            // Force the state's own timestamp to the confirmation moment too,
            // so all four writes share one consistent timestamp:
            //   writeActiveState      -> getAlarmStartTime()        = confirmProcTime
            //   publishNotification   -> getAlarmStartTime()        = confirmProcTime
            //   publishRealTime       -> passed dataTimestamp       = confirmProcTime
            //   writeLatestState      -> state.getTimestamp()       = confirmProcTime (set here)
            // Without this, writeLatestState would carry the last pending tick's
            // event time, disagreeing with the other three.
            state.setTimestamp(confirmProcTime);
            writeActiveState(jedis, state);
            publishNotificationChannel(jedis, state);
            publishRealTimeChannel(jedis, state, confirmProcTime);
            writeLatestState(jedis, state);
        }

        pendingState.clear();

        String ts = formatTimestamp(confirmProcTime);
        String pointName = extractPointName(state.getMqttPath());
        String color = switch (state.getSeverity() != null ? state.getSeverity() : "") {
            case "Critical" -> "\033[91m";
            case "Major"    -> "\033[93m";
            case "Minor"    -> "\033[33m";
            case "Warning"  -> "\033[94m";
            default         -> "";
        };
        out.collect(String.format("%s[%s] [%8s] [%-8s] %-40s | value=%s | level=%s | delay confirmed, ALARM\033[0m",
                color, ts, state.getSeverity(), state.getStage(), pointName,
                state.getValue(), state.getLevel()));
    }

    // ── Rule evaluation (type-specific, reads from Redis) ──────────────────

    /** Result of evaluating a rule against a point value.
     *  <p>{@code skipped} distinguishes an unknown enum value (no rule
     *  matches) from a real normal value (a rule matched but its severity
     *  is empty). A skipped result must not touch alarm:active, otherwise
     *  it would wrongly recover an ongoing alarm. */
    record EvalResult(boolean isAlarm, boolean skipped, String level, String severity,
                      String message, Integer timeDelay, String deadband) {}

    private EvalResult evaluateNumeric(Jedis jedis, String alarmName, String pointValue) {
        String json = jedis.hget(rulesNumericKey, alarmName);
        if (json == null || json.isBlank()) return null;

        try {
            AlarmRuleNumericEntity rule = objectMapper.readValue(json, AlarmRuleNumericEntity.class);
            Double numVal = null;
            try {
                numVal = Double.parseDouble(pointValue != null ? pointValue.trim() : "");
            } catch (Exception ignored) {}
            if (numVal == null) {
                return new EvalResult(false, false, null, null, null, null, null);
            }

            Integer timeDelay = rule.getTimeDelay();
            String deadband = rule.getDeadband();

            // Priority order: High-High, High, Low-Low, Low
            // High-High
            if (rule.getHighHighValue() != null && !rule.getHighHighValue().isBlank()) {
                try {
                    if (numVal >= Double.parseDouble(rule.getHighHighValue())) {
                        return new EvalResult(true, false, "High-High", rule.getHighHighSeverity(),
                                rule.getHighHighMessage(), timeDelay, deadband);
                    }
                } catch (NumberFormatException ignored) {}
            }
            // High
            if (rule.getHighValue() != null && !rule.getHighValue().isBlank()) {
                try {
                    if (numVal >= Double.parseDouble(rule.getHighValue())) {
                        return new EvalResult(true, false, "High", rule.getHighSeverity(),
                                rule.getHighMessage(), timeDelay, deadband);
                    }
                } catch (NumberFormatException ignored) {}
            }
            // Low-Low
            if (rule.getLowLowValue() != null && !rule.getLowLowValue().isBlank()) {
                try {
                    if (numVal <= Double.parseDouble(rule.getLowLowValue())) {
                        return new EvalResult(true, false, "Low-Low", rule.getLowLowSeverity(),
                                rule.getLowLowMessage(), timeDelay, deadband);
                    }
                } catch (NumberFormatException ignored) {}
            }
            // Low
            if (rule.getLowValue() != null && !rule.getLowValue().isBlank()) {
                try {
                    if (numVal <= Double.parseDouble(rule.getLowValue())) {
                        return new EvalResult(true, false, "Low", rule.getLowSeverity(),
                                rule.getLowMessage(), timeDelay, deadband);
                    }
                } catch (NumberFormatException ignored) {}
            }

            // Value is normal (no threshold tripped). Still carry timeDelay and
            // deadband so the hysteresis gate in processMessage can read them
            // from the current state - without deadband here, recovery would
            // always be instantaneous regardless of the configured deadband.
            return new EvalResult(false, false, null, null, null, timeDelay, deadband);
        } catch (Exception e) {
            log.warn("Failed to deserialize numeric rule: {} - {}", alarmName, e.getMessage());
            return null;
        }
    }

    /** Resolve the raw threshold value string for a given alarm level. */
    private String getThresholdForLevel(AlarmRuleNumericEntity rule, String level) {
        if (level == null) return null;
        return switch (level) {
            case "High-High" -> rule.getHighHighValue();
            case "High"      -> rule.getHighValue();
            case "Low-Low"   -> rule.getLowLowValue();
            case "Low"       -> rule.getLowValue();
            default          -> null;
        };
    }

    /**
     * Deadband (hysteresis) gate for alarm recovery.
     * <p>
     * When the value no longer trips any raw threshold (the caller has
     * already determined isAlarm == false) but an alarm is still active,
     * the deadband keeps the alarm alive until the value moves past the
     * recovery boundary:
     * <ul>
     *   <li>High side (High-High / High): alarm persists while
     *       {@code value >= threshold - deadband}; recovers only when
     *       {@code value < threshold - deadband}.</li>
     *   <li>Low side (Low-Low / Low): alarm persists while
     *       {@code value <= threshold + deadband}; recovers only when
     *       {@code value > threshold + deadband}.</li>
     * </ul>
     * If the deadband is absent, zero, or unparseable, no hysteresis is
     * applied and the alarm recovers at the raw threshold (current
     * behavior), preserving backward compatibility.
     *
     * @param jedis    Redis connection for re-reading the rule
     * @param alarmName the rule's alarm code (hash field key)
     * @param history  the currently active alarm record (carries the level)
     * @param current  the latest point state (carries the value + deadband)
     * @return {@code true} if the value is still within the deadband band
     *         and the alarm should be kept alive; {@code false} to proceed
     *         with normal recovery
     */
    private boolean isInDeadband(Jedis jedis, String alarmName,
                                 PointAlarmState history, PointAlarmState current) {
        String deadbandStr = current.getDeadband();
        if (isBlank(deadbandStr)) return false;
        double deadband;
        try {
            deadband = Double.parseDouble(deadbandStr.trim());
        } catch (NumberFormatException e) {
            return false;
        }
        if (deadband <= 0) return false;

        // Re-read the rule to resolve the threshold for the active level.
        String json = jedis.hget(rulesNumericKey, alarmName);
        if (json == null || json.isBlank()) return false;
        AlarmRuleNumericEntity rule;
        try {
            rule = objectMapper.readValue(json, AlarmRuleNumericEntity.class);
        } catch (Exception e) {
            return false;
        }

        String level = history.getLevel();
        String thresholdStr = getThresholdForLevel(rule, level);
        if (thresholdStr == null || thresholdStr.isBlank()) return false;

        double threshold;
        try {
            threshold = Double.parseDouble(thresholdStr.trim());
        } catch (NumberFormatException e) {
            return false;
        }

        double value;
        try {
            value = Double.parseDouble(current.getValue().trim());
        } catch (Exception e) {
            return false; // cannot evaluate, let normal recovery proceed
        }

        // High side: alarm persists while value >= threshold - deadband.
        if ("High-High".equals(level) || "High".equals(level)) {
            return value >= threshold - deadband;
        }
        // Low side: alarm persists while value <= threshold + deadband.
        if ("Low-Low".equals(level) || "Low".equals(level)) {
            return value <= threshold + deadband;
        }
        return false;
    }

    private EvalResult evaluateEnum(Jedis jedis, String alarmName, String pointValue) {
        String json = jedis.hget(rulesEnumKey, alarmName);
        if (json == null || json.isBlank()) return null;

        try {
            List<AlarmRuleEnumEntity> rules = objectMapper.readValue(json,
                    new TypeReference<List<AlarmRuleEnumEntity>>() {});
            String normalizedValue = pointValue != null ? pointValue.trim() : null;

            for (AlarmRuleEnumEntity rule : rules) {
                if (rule.getEnumValue() != null && normalizedValue != null
                        && normalizedValue.equalsIgnoreCase(rule.getEnumValue())) {
                    // Matched the configured enum value
                    if (rule.getSeverity() != null && !rule.getSeverity().isBlank()) {
                        // level carries the enum value so the lifecycle state
                        // machine can distinguish alarms on different enum
                        // values (e.g. "Enum:0" vs "Enum:2"); otherwise every
                        // enum alarm shares the same level and severity changes
                        // are wrongly treated as "continue".
                        String level = "Enum:" + rule.getEnumValue();
                        return new EvalResult(true, false, level, rule.getSeverity(),
                                rule.getMessage(), rule.getTimeDelay(), null);
                    } else {
                        // Matched but severity empty -> explicitly normal value
                        return new EvalResult(false, false, null, null, null,
                                rule.getTimeDelay(), null);
                    }
                }
            }
            // No matching enum value: skip evaluation, do NOT treat as normal.
            // Writing a normal state here would wrongly recover an active alarm,
            // so only the latest real-time value should be refreshed by the caller.
            return new EvalResult(false, true, null, null, null, null, null);
        } catch (Exception e) {
            log.warn("Failed to deserialize enum rules: {} - {}", alarmName, e.getMessage());
            return null;
        }
    }

    // ── State construction ──────────────────────────────────────────────────

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

        state.setSource(extractSource(point.getMqttPath()));
        state.setLocation(extractLocation(point.getMqttPath()));
        state.setDuration("0");
        state.setStage("");
        state.setStageMessage("");
        return state;
    }

    private String extractPointName(String path) {
        if (path == null || path.isEmpty()) return "";
        String normalized = path.startsWith("/") ? path.substring(1) : path;
        String[] parts = normalized.split("/");
        return parts.length > 0 ? parts[parts.length - 1] : "";
    }

    private String extractLocation(String path) {
        if (path == null || path.isEmpty()) return "";
        String normalized = path.startsWith("/") ? path.substring(1) : path;
        String[] parts = normalized.split("/");
        return parts.length > 0 ? parts[0] : "";
    }

    private String extractSource(String path) {
        if (path == null || path.isEmpty()) return "";
        String normalized = path.startsWith("/") ? path.substring(1) : path;
        String[] parts = normalized.split("/");
        if (parts.length >= 2) {
            return parts[parts.length - 2] + "/" + parts[parts.length - 1];
        }
        return parts.length == 1 ? parts[0] : "";
    }

    // ── Redis writers ────────────────────────────────────────────────────────

    private void writeLatestState(Jedis jedis, PointAlarmState state) {
        try {
            String json = objectMapper.writeValueAsString(state);
            jedis.hset(latestRedisKey, state.getMqttPath(), json);
            log.debug("LATEST {} value={}", state.getMqttPath(), state.getValue());
        } catch (Exception e) {
            log.warn("Failed to write latest state for {}: {}", state.getMqttPath(), e.getMessage());
        }
    }

    private void publishAlarm(Jedis jedis, PointAlarmState state) {
        try {
            String json = objectMapper.writeValueAsString(state);
            jedis.publish(notificationChannel, json);
            jedis.publish(realtimeChannel, json);
        } catch (Exception e) {
            log.warn("Failed to publish alarm for {}: {}", state.getMqttPath(), e.getMessage());
        }
    }

    /**
     * Publish to the notification channel (frontend WebSocket).
     * The timestamp is forced to the alarm's historical start time so the
     * frontend always shows when the alarm began (start/continue/end alike).
     */
    private void publishNotificationChannel(Jedis jedis, PointAlarmState state) {
        try {
            String json = serializeWithTimestamp(state, state.getAlarmStartTime());
            jedis.publish(notificationChannel, json);
            log.debug("PUB-NOTIFY {} stage={} -> {}", state.getMqttPath(), state.getStage(), notificationChannel);
        } catch (Exception e) {
            log.warn("Failed to publish notification for {}: {}", state.getMqttPath(), e.getMessage());
        }
    }

    /**
     * Publish to the realtime channel (TDengine persistence).
     * The timestamp is forced to the current data tick's timestamp
     * (point.getTimestamp()), so each lifecycle event (start/continue/end/escalation)
     * is recorded at the moment it was observed - regardless of whether the state
     * object is the fresh current tick or a history record read back from Redis.
     * Accepts a nullable Long to avoid NPE from auto-unboxing when a Kafka message
     * lacks a timestamp; null falls back to the state's own timestamp.
     */
    private void publishRealTimeChannel(Jedis jedis, PointAlarmState state, Long dataTimestamp) {
        try {
            String json = serializeWithTimestamp(state, dataTimestamp);
            jedis.publish(realtimeChannel, json);
            log.debug("PUB-REALTIME {} stage={} -> {}", state.getMqttPath(), state.getStage(), realtimeChannel);
        } catch (Exception e) {
            log.warn("Failed to publish realtime for {}: {}", state.getMqttPath(), e.getMessage());
        }
    }

    /**
     * Write a state to alarm:active with its timestamp forced to the alarm's
     * historical start time (so continue/end records keep the start timestamp).
     */
    private void writeActiveState(Jedis jedis, PointAlarmState state) {
        try {
            String json = serializeWithTimestamp(state, state.getAlarmStartTime());
            jedis.hset(activeRedisKey, state.getMqttPath(), json);
            log.debug("ACTIVE {} stage={} level={}", state.getMqttPath(), state.getStage(), state.getLevel());
        } catch (Exception e) {
            log.warn("Failed to write active state for {}: {}", state.getMqttPath(), e.getMessage());
        }
    }

    private void removeActive(Jedis jedis, String path) {
        try {
            jedis.hdel(activeRedisKey, path);
            log.debug("ACTIVE-REMOVE {}", path);
        } catch (Exception e) {
            log.warn("Failed to remove active alarm for {}: {}", path, e.getMessage());
        }
    }

    private PointAlarmState readActive(Jedis jedis, String path) {
        try {
            String json = jedis.hget(activeRedisKey, path);
            if (json == null || json.isBlank()) return null;
            return objectMapper.readValue(json, PointAlarmState.class);
        } catch (Exception e) {
            log.warn("Failed to read active alarm for {}: {}", path, e.getMessage());
            return null;
        }
    }

    /**
     * Serialize a state with a specific timestamp, without mutating the original.
     * If overrideTs is null, the state's own timestamp is kept.
     */
    private String serializeWithTimestamp(PointAlarmState state, Long overrideTs) throws Exception {
        if (overrideTs == null) {
            return objectMapper.writeValueAsString(state);
        }
        // Clone via JSON round-trip to avoid mutating the shared state object.
        var node = (com.fasterxml.jackson.databind.node.ObjectNode) objectMapper.readTree(objectMapper.writeValueAsString(state));
        node.put("timestamp", overrideTs);
        return objectMapper.writeValueAsString(node);
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

    private boolean isBlank(String value) {
        return value == null || value.isBlank();
    }

    private String formatTimestamp(Long epochMs) {
        if (epochMs == null) return LocalDateTime.now().format(TS_FORMAT);
        return LocalDateTime.ofInstant(Instant.ofEpochMilli(epochMs), ZoneId.systemDefault())
                .format(TS_FORMAT);
    }
}
