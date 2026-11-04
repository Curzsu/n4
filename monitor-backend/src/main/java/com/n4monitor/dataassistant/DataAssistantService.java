package com.n4monitor.dataassistant;

import com.n4monitor.alarm.dto.AlarmRecordDto;
import com.n4monitor.alarm.dto.EventExportFilter;
import com.n4monitor.alarm.model.AlarmStatus;
import com.n4monitor.alarm.service.AlarmService;
import com.n4monitor.deployment.entity.DeviceDeployment;
import com.n4monitor.deployment.service.DeploymentService;
import com.n4monitor.exception.BusinessException;
import com.n4monitor.tdengine.TdengineSyncService;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;

import java.time.DateTimeException;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

@Service
public class DataAssistantService {
    private static final ZoneId QUERY_ZONE = ZoneId.of("Asia/Shanghai");
    private static final DateTimeFormatter RECORD_TIME =
            DateTimeFormatter.ofPattern("uuuu-MM-dd HH:mm:ss").withZone(QUERY_ZONE);
    private static final int MAX_QUESTION_LENGTH = 500;
    private static final int MAX_CONTEXT = 3;
    private static final int MAX_RESULTS = 30;
    private static final int MAX_EVENT_ROWS = 2000;

    private static final String ACTIVE = "active_alarms";
    private static final String EVENTS = "alarm_events";
    private static final String DEVICES = "deployments";
    private static final String TODAY_ALARM_FRAMES_QUESTION = "How many alarm frames were recorded today?";
    private static final Map<String, String> DATASET_PERMISSIONS = Map.of(
            ACTIVE, "alarm:view", EVENTS, "alarm:event:view", DEVICES, "device:deploy:view");
    private static final Map<String, List<String>> GROUP_FIELDS = Map.of(
            ACTIVE, List.of("severity", "source", "location", "status"),
            EVENTS, List.of("severity", "source", "location", "status", "stage", "date"),
            DEVICES, List.of("deviceType", "system", "location", "status", "manufacturer"));
    private static final Map<String, List<String>> SEARCH_FIELDS = Map.of(
            ACTIVE, List.of("source", "location", "message"),
            EVENTS, List.of("source", "location", "message", "stage"),
            DEVICES, List.of("device", "alias", "deviceType", "system", "location", "manufacturer", "model"));
    private static final Set<String> ALARM_STATUSES = Set.of(AlarmStatus.ACK, AlarmStatus.UNACK, AlarmStatus.CLEARED);
    private static final List<String> ALARM_COLUMNS = List.of(
            "time", "severity", "source", "location", "status", "message");
    private static final List<String> EVENT_COLUMNS = List.of(
            "time", "severity", "source", "location", "status", "message", "stage", "date");
    private static final List<String> DEVICE_COLUMNS = List.of(
            "device", "alias", "deviceType", "system", "location", "status", "manufacturer", "model");

    private final GeminiClient gemini;
    private final AlarmService alarms;
    private final TdengineSyncService history;
    private final DeploymentService deployments;

    DataAssistantService(GeminiClient gemini, AlarmService alarms,
                         TdengineSyncService history, DeploymentService deployments) {
        this.gemini = gemini;
        this.alarms = alarms;
        this.history = history;
        this.deployments = deployments;
    }

    Answer ask(Question request, Set<String> permissions) {
        Question question = validateQuestion(request);
        List<String> allowedDatasets = DATASET_PERMISSIONS.entrySet().stream()
                .filter(entry -> permissions.contains(entry.getValue()))
                .map(Map.Entry::getKey)
                .sorted()
                .toList();
        if (allowedDatasets.isEmpty()) {
            throw new AccessDeniedException("No Data Assistant datasets available");
        }
        long planningStart = System.nanoTime();
        LocalDate today = LocalDate.now(QUERY_ZONE);
        boolean presetQuery = TODAY_ALARM_FRAMES_QUESTION.equalsIgnoreCase(question.text());
        Query query = presetQuery
                ? new Query(EVENTS, "count", null, null, null, null, null, today.toString(), today.toString())
                : gemini.plan(question, allowedDatasets, today);
        validateQuery(query, allowedDatasets);
        long queryStart = System.nanoTime();
        Evidence evidence = execute(query, today);
        long summaryStart = System.nanoTime();
        String answer;
        boolean summaryGenerated;
        try {
            answer = gemini.answer(question, query, evidence);
            summaryGenerated = true;
        } catch (BusinessException e) {
            answer = "The query succeeded. The Gemini summary is unavailable. See the results below.";
            summaryGenerated = false;
        }
        var execution = new Execution(presetQuery, TimeUnit.NANOSECONDS.toMillis(queryStart - planningStart),
                TimeUnit.NANOSECONDS.toMillis(summaryStart - queryStart),
                TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - summaryStart), summaryGenerated);
        return new Answer(answer, query, evidence, execution);
    }

    private Question validateQuestion(Question request) {
        if (request == null || !validQuestionText(request.text())) {
            throw new IllegalArgumentException("Enter a question between 1 and 500 characters");
        }
        List<String> context = request.context() == null ? List.of() : request.context();
        if (context.size() > MAX_CONTEXT || context.stream().anyMatch(text -> !validQuestionText(text))) {
            throw new IllegalArgumentException("Question context must contain up to three valid questions");
        }
        return new Question(request.text().strip(), context);
    }

    private boolean validQuestionText(String text) {
        return text != null && !text.isBlank() && text.length() <= MAX_QUESTION_LENGTH;
    }

    private void validateQuery(Query query, List<String> allowedDatasets) {
        String dataset = query.dataset();
        if ("unsupported".equals(dataset)) {
            throw new BusinessException(422, "Ask about active alarms, historical alarm frames, or deployed devices available to your role");
        }
        if (dataset == null || !DATASET_PERMISSIONS.containsKey(dataset)) {
            throw new BusinessException(502, "Gemini returned an unknown dataset");
        }
        if (!allowedDatasets.contains(dataset)) {
            throw new AccessDeniedException("Data Assistant dataset access denied");
        }
        if (query.operation() == null || !Set.of("count", "list", "group").contains(query.operation())
                || ("group".equals(query.operation())
                && (query.groupBy() == null || !GROUP_FIELDS.get(dataset).contains(query.groupBy())))
                || (hasText(query.severity()) && (DEVICES.equals(dataset)
                || !EventExportFilter.ALLOWED_SEVERITIES.contains(query.severity())))
                || (hasText(query.status()) && (DEVICES.equals(dataset) || !ALARM_STATUSES.contains(query.status())))
                || (hasText(query.search()) && (!hasText(query.searchField())
                || !SEARCH_FIELDS.get(dataset).contains(query.searchField())))
                || (query.search() != null && query.search().length() > 120)
                || (!EVENTS.equals(dataset) && (hasText(query.startDate()) || hasText(query.endDate())))) {
            throw new BusinessException(422, "The question could not be mapped to a supported query");
        }
    }

    private Evidence execute(Query query, LocalDate today) {
        List<Map<String, String>> rows;
        List<String> columns;
        switch (query.dataset()) {
            case ACTIVE -> {
                rows = alarms.getActiveAlarms("active").stream().map(this::alarmRow).toList();
                columns = ALARM_COLUMNS;
            }
            case EVENTS -> {
                EventExportFilter filter = eventFilter(query, today);
                if ("count".equals(query.operation()) && !hasText(query.search()) && !hasText(query.status())) {
                    return count(history.countAlarmEvents(filter));
                }
                var events = history.queryAlarmEventsPage(filter, 0, MAX_EVENT_ROWS + 1);
                if (events.size() > MAX_EVENT_ROWS) {
                    throw new BusinessException(422, "Too many historical alarm frames. Choose a shorter date range or a severity.");
                }
                // The export page already orders these rows by write time ascending.
                rows = events.reversed().stream().map(this::eventRow).toList();
                columns = EVENT_COLUMNS;
            }
            case DEVICES -> {
                rows = deployments.listAll().stream().map(this::deviceRow).toList();
                columns = DEVICE_COLUMNS;
            }
            default -> throw new IllegalStateException("Unsupported dataset");
        }

        String search = hasText(query.search()) ? query.search().strip().toLowerCase(Locale.ROOT) : "";
        List<Map<String, String>> matches = rows.stream()
                .filter(row -> !hasText(query.severity()) || query.severity().equals(row.get("severity")))
                .filter(row -> !hasText(query.status()) || query.status().equals(row.get("status")))
                .filter(row -> search.isEmpty() || row.get(query.searchField()).toLowerCase(Locale.ROOT).contains(search))
                .toList();
        return switch (query.operation()) {
            case "count" -> count(matches.size());
            case "group" -> group(matches, query.groupBy());
            case "list" -> new Evidence(matches.size(), columns,
                    matches.stream().limit(MAX_RESULTS).map(row -> (Map<String, ?>) row)
                            .collect(Collectors.toList()), matches.size() > MAX_RESULTS);
            default -> throw new IllegalStateException("Unsupported operation");
        };
    }

    private EventExportFilter eventFilter(Query query, LocalDate today) {
        try {
            LocalDate start = LocalDate.parse(value(query.startDate()));
            LocalDate end = LocalDate.parse(value(query.endDate()));
            if (end.isBefore(start) || end.isAfter(today) || end.isAfter(start.plusDays(29))) {
                throw new BusinessException(422, "Choose a historical date range of up to 30 days ending today or earlier");
            }
            long startTs = start.atStartOfDay(QUERY_ZONE).toInstant().toEpochMilli();
            long endTs = end.plusDays(1).atStartOfDay(QUERY_ZONE).toInstant().toEpochMilli() - 1;
            List<String> severities = hasText(query.severity()) ? List.of(query.severity()) : List.of();
            return new EventExportFilter(startTs, endTs, severities, true);
        } catch (DateTimeException | ArithmeticException e) {
            throw new BusinessException(422, "Choose valid start and end dates for historical alarm frames");
        }
    }

    private Evidence count(long total) {
        return new Evidence(total, List.of("count"), List.of(Map.of("count", total)), false);
    }

    private Evidence group(List<Map<String, String>> rows, String field) {
        Map<String, Long> counts = rows.stream().collect(Collectors.groupingBy(row -> {
            String key = row.get(field);
            return hasText(key) ? key : "Unknown";
        }, Collectors.counting()));
        List<Map<String, ?>> groups = counts.entrySet().stream()
                .sorted(Map.Entry.<String, Long>comparingByValue(Comparator.reverseOrder())
                        .thenComparing(Map.Entry.comparingByKey()))
                .limit(MAX_RESULTS)
                .map(entry -> Map.<String, Object>of(field, entry.getKey(), "count", entry.getValue()))
                .collect(Collectors.toList());
        return new Evidence(rows.size(), List.of(field, "count"), groups, counts.size() > MAX_RESULTS);
    }

    private Map<String, String> alarmRow(AlarmRecordDto alarm) {
        return Map.of("time", value(alarm.getTime()), "severity", value(alarm.getSeverity()),
                "source", value(alarm.getSource()), "location", value(alarm.getLocation()),
                "status", value(alarm.getStatus()), "message", value(alarm.getMessage()));
    }

    private Map<String, String> eventRow(AlarmRecordDto alarm) {
        var row = new HashMap<>(alarmRow(alarm));
        row.put("stage", value(alarm.getStage()));
        Instant recordedAt = alarm.getTs() == null ? null : Instant.ofEpochMilli(alarm.getTs());
        row.put("time", recordedAt == null ? "" : RECORD_TIME.format(recordedAt));
        row.put("date", recordedAt == null ? "" : recordedAt.atZone(QUERY_ZONE).toLocalDate().toString());
        return row;
    }

    private Map<String, String> deviceRow(DeviceDeployment device) {
        return Map.of("device", value(device.getDeviceDeployName()), "alias", value(device.getDeviceAlias()),
                "deviceType", value(device.getDeviceType()), "system", value(device.getSystemName()),
                "location", value(device.getLocation()), "status", value(device.getStatus()),
                "manufacturer", value(device.getManufacturer()), "model", value(device.getModel()));
    }

    private boolean hasText(String text) {
        return text != null && !text.isBlank();
    }

    private String value(Object field) {
        return field == null ? "" : field.toString();
    }

    public record Question(String text, List<String> context) {}

    public record Query(String dataset, String operation, String groupBy, String severity,
                        String status, String searchField, String search, String startDate, String endDate) {}

    public record Evidence(long matchedCount, List<String> columns, List<Map<String, ?>> rows, boolean truncated) {}

    public record Execution(boolean presetQuery, long planningMillis, long queryMillis,
                            long summaryMillis, boolean summaryGenerated) {}

    public record Answer(String answer, Query query, Evidence evidence, Execution execution) {}
}
