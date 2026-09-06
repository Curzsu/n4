package com.n4monitor.alarm.dto;

import java.util.List;
import java.util.Objects;

/**
 * Filter parameters for the Event page Excel export: a write-time range
 * (required), an optional severity whitelist and the alarm-only switch.
 * <p>
 * The compact constructor validates eagerly so the controller can build the
 * filter straight from request params: invalid input surfaces as
 * {@link IllegalArgumentException}, which the global exception handler turns
 * into a 400 response. Severity values are checked against a fixed whitelist
 * before they ever reach SQL - the TDengine driver has no server-side
 * parameter binding, so user-supplied strings must never be concatenated
 * unvalidated.
 *
 * @param startTs   range start (inclusive), epoch millis, compared against the
 *                  write timestamp {@code ts}
 * @param endTs     range end (inclusive), epoch millis, compared against the
 *                  write timestamp {@code ts}
 * @param severities optional severity filter; blank entries are dropped,
 *                   values outside the whitelist are rejected, duplicates
 *                   collapsed
 * @param alarmOnly when true only alarm frames are exported (rows carrying a
 *                  non-empty {@code alarm_level}); when false every stored
 *                  frame in range is exported
 */
public record EventExportFilter(long startTs, long endTs, List<String> severities, boolean alarmOnly) {

    /** Severity values accepted from the outside; anything else is rejected. */
    public static final List<String> ALLOWED_SEVERITIES = List.of("Critical", "Major", "Minor", "Warning");

    public EventExportFilter {
        if (endTs < startTs) {
            throw new IllegalArgumentException("Export end time must not be earlier than the start time");
        }
        severities = severities == null ? List.of() : severities.stream()
                .filter(Objects::nonNull)
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .peek(s -> {
                    if (!ALLOWED_SEVERITIES.contains(s)) {
                        throw new IllegalArgumentException(
                                "Unknown severity '" + s + "', allowed values: " + ALLOWED_SEVERITIES);
                    }
                })
                .distinct()
                .toList();
    }
}
