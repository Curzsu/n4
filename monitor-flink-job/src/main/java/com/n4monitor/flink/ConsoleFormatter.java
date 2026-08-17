package com.n4monitor.flink;

import com.n4monitor.alarm.model.AlarmSeverity;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;

/**
 * Console (stdout) log line formatting for the alarm pipeline, including the
 * severity ANSI colors. Format strings are extracted verbatim from
 * AlarmProcessFunction so the console output stays byte-identical.
 */
final class ConsoleFormatter {

    private static final DateTimeFormatter TS_FORMAT =
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss.SSS");

    private ConsoleFormatter() {
    }

    static String formatTimestamp(Long epochMs) {
        if (epochMs == null) return LocalDateTime.now().format(TS_FORMAT);
        return LocalDateTime.ofInstant(Instant.ofEpochMilli(epochMs), ZoneId.systemDefault())
                .format(TS_FORMAT);
    }

    /** ANSI color per severity; unknown or null severity gets no color. */
    private static String severityColor(String severity) {
        return switch (severity != null ? severity : "") {
            case AlarmSeverity.CRITICAL -> "\033[91m";
            case AlarmSeverity.MAJOR    -> "\033[93m";
            case AlarmSeverity.MINOR    -> "\033[33m";
            case AlarmSeverity.WARNING  -> "\033[94m";
            default         -> "";
        };
    }

    static String stringSkipped(String ts, String pointName, String value) {
        return String.format("\033[90m[%s] [%8s] %-40s | value=%s | string type, skipped\033[0m",
                ts, "STRING", pointName, value);
    }

    static String noAlarmCode(String ts, String pointName, String value, String type) {
        return String.format("[%s] [%8s] %-40s | value=%s | no alarm_code, point=%s, type=%s",
                ts, "NORULE", pointName, value, pointName, type);
    }

    static String unknownType(String ts, String pointName, String value, String type) {
        return String.format("[%s] [%8s] %-40s | value=%s | unknown type: %s",
                ts, "UNKNOWN", pointName, value, type);
    }

    static String noRuleFound(String ts, String pointName, String value, String type) {
        return String.format("[%s] [%8s] %-40s | value=%s | no rule found, point=%s, type=%s",
                ts, "NORULE", pointName, value, pointName, type);
    }

    static String unknownEnumSkipped(String ts, String pointName, String value) {
        return String.format("[%s] [%8s] %-40s | value=%s | unknown enum value, skipped",
                ts, "SKIP", pointName, value);
    }

    static String pendingElapsed(String ts, String pointName, String value,
                                 String level, long elapsedSeconds) {
        return String.format("\033[96m[%s] [%8s] %-40s | value=%s | pending %s, %ds elapsed\033[0m",
                ts, "PENDING", pointName, value, level, elapsedSeconds);
    }

    static String pendingReset(String ts, String pointName, String value, String level,
                               String previousLevel, int delaySeconds) {
        return String.format("\033[96m[%s] [%8s] %-40s | value=%s | pending %s (reset from %s), %ds delay\033[0m",
                ts, "PENDING", pointName, value, level, previousLevel, delaySeconds);
    }

    static String pendingCancelled(String ts, String pointName, String value, String level) {
        return String.format("\033[92m[%s] [%8s] %-40s | value=%s | pending %s cancelled (normal)\033[0m",
                ts, "CANCEL", pointName, value, level);
    }

    static String pendingDelay(String ts, String pointName, String value, String level,
                               int delaySeconds, double remainingSeconds) {
        return String.format("\033[96m[%s] [%8s] %-40s | value=%s | pending %s, %ds delay (%.1fs left)\033[0m",
                ts, "PENDING", pointName, value, level, delaySeconds, remainingSeconds);
    }

    static String alarm(String ts, String severity, String stage, String pointName,
                        String value, String level, String message) {
        return String.format("%s[%s] [%8s] [%-8s] %-40s | value=%s | level=%s | %s\033[0m",
                severityColor(severity), ts, severity, stage, pointName, value, level, message);
    }

    static String normal(String ts, String pointName, String value) {
        return String.format("\033[92m[%s] [%8s] %-40s | value=%s | normal\033[0m",
                ts, "NORMAL", pointName, value);
    }

    static String delayConfirmed(String ts, String severity, String stage, String pointName,
                                 String value, String level) {
        return String.format("%s[%s] [%8s] [%-8s] %-40s | value=%s | level=%s | delay confirmed, ALARM\033[0m",
                severityColor(severity), ts, severity, stage, pointName, value, level);
    }
}
