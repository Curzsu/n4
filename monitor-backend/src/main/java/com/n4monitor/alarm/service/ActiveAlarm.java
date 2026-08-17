package com.n4monitor.alarm.service;

import com.n4monitor.alarm.dto.AlarmRecordDto;

/**
 * One parsed entry of the alarm:active hash. Carries the display DTO plus the
 * raw epoch timestamp (null when the record has none) and the hash key (the
 * point path) so aggregation does not need to re-parse JSON.
 */
public record ActiveAlarm(AlarmRecordDto record, Long epochMillis, String path) {
}
