package com.n4monitor.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.fasterxml.jackson.core.StreamReadConstraints;
import com.n4monitor.config.FuxaProperties;
import com.n4monitor.dto.FuxaCabinSessionDto;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;
import org.springframework.web.util.UriComponentsBuilder;
import org.springframework.scheduling.annotation.Scheduled;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Activates the preconfigured FUXA MQTT client for one cabin.  It deliberately
 * does not create MQTT connections itself: FUXA remains the MQTT client.
 */
@Service
public class FuxaCabinClientService {
    private static final Logger LOGGER = LoggerFactory.getLogger(FuxaCabinClientService.class);
    private final FuxaProperties properties;
    private final ObjectMapper objectMapper;
    private final RestClient fuxaClient;
    private final Map<String, Object> cabinLocks = new ConcurrentHashMap<>();
    private final Map<String, Map<String, Instant>> sessionsByCabin = new ConcurrentHashMap<>();
    private final Map<String, Instant> idleSinceByCabin = new ConcurrentHashMap<>();

    public FuxaCabinClientService(FuxaProperties properties, ObjectMapper objectMapper) {
        this.properties = properties;
        this.objectMapper = objectMapper.copy();
        this.objectMapper.getFactory().setStreamReadConstraints(
            StreamReadConstraints.builder()
                .maxStringLength(properties.getProjectMaxStringLength())
                .build()
        );
        SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
        requestFactory.setConnectTimeout(Duration.ofSeconds(5));
        requestFactory.setReadTimeout(Duration.ofSeconds(15));
        this.fuxaClient = RestClient.builder()
            .baseUrl(properties.getBaseUrl())
            .requestFactory(requestFactory)
            .defaultHeader(HttpHeaders.CONNECTION, "close")
            .build();
    }

    public List<String> getConfiguredCabins() {
        return new ArrayList<>(properties.getCabinClients().keySet());
    }

    public FuxaCabinSessionDto openSession(String cabinName, String sessionId) {
        requireSessionId(sessionId);
        return withCabinLock(cabinName, () -> {
            FuxaProperties.CabinClient cabin = getCabin(cabinName);
            Map<String, Instant> sessions = sessionsByCabin.computeIfAbsent(
                normalize(cabinName), ignored -> new ConcurrentHashMap<>());
            discardExpiredSessions(cabinName, sessions, Instant.now());
            sessions.put(sessionId, Instant.now());
            idleSinceByCabin.remove(normalize(cabinName));

            ObjectNode device = findDevice(getProject(), cabin);
            if (!device.path("enabled").asBoolean(false)) {
                device.put("enabled", true);
                setProjectData("set-device", device);
            }
            return toDto(cabinName, device, cabin, sessionId, true, sessions.size());
        });
    }

    public FuxaCabinSessionDto heartbeat(String cabinName, String sessionId) {
        requireSessionId(sessionId);
        return withCabinLock(cabinName, () -> {
            FuxaProperties.CabinClient cabin = getCabin(cabinName);
            Map<String, Instant> sessions = getSessions(cabinName);
            discardExpiredSessions(cabinName, sessions, Instant.now());
            if (!sessions.containsKey(sessionId)) {
                throw new IllegalArgumentException("Cabin monitor session has expired");
            }
            sessions.put(sessionId, Instant.now());
            ObjectNode device = findDevice(getProject(), cabin);
            return toDto(cabinName, device, cabin, sessionId, true, sessions.size());
        });
    }

    public FuxaCabinSessionDto releaseSession(String cabinName, String sessionId) {
        requireSessionId(sessionId);
        return withCabinLock(cabinName, () -> {
            FuxaProperties.CabinClient cabin = getCabin(cabinName);
            Map<String, Instant> sessions = getSessions(cabinName);
            sessions.remove(sessionId);
            if (sessions.isEmpty()) {
                idleSinceByCabin.putIfAbsent(normalize(cabinName), Instant.now());
            }
            ObjectNode device = findDevice(getProject(), cabin);
            return toDto(cabinName, device, cabin, sessionId, false, sessions.size());
        });
    }

    public FuxaCabinSessionDto status(String cabinName) {
        return withCabinLock(cabinName, () -> {
            FuxaProperties.CabinClient cabin = getCabin(cabinName);
            Map<String, Instant> sessions = getSessions(cabinName);
            discardExpiredSessions(cabinName, sessions, Instant.now());
            ObjectNode device = findDevice(getProject(), cabin);
            return toDto(cabinName, device, cabin, null, false, sessions.size());
        });
    }

    @Scheduled(fixedDelayString = "${fuxa.cabin-session-reaper-ms:10000}")
    public void releaseExpiredSessions() {
        var cabinKeys = new HashSet<String>();
        cabinKeys.addAll(sessionsByCabin.keySet());
        cabinKeys.addAll(idleSinceByCabin.keySet());
        for (String cabinKey : cabinKeys) {
            try {
                withCabinLock(cabinKey, () -> {
                    FuxaProperties.CabinClient cabin = getCabin(cabinKey);
                    Map<String, Instant> sessions = getSessions(cabinKey);
                    Instant now = Instant.now();
                    discardExpiredSessions(cabinKey, sessions, now);
                    if (!sessions.isEmpty()) {
                        idleSinceByCabin.remove(normalize(cabinKey));
                        return null;
                    }

                    Instant idleSince = idleSinceByCabin.putIfAbsent(normalize(cabinKey), now);
                    if (idleSince != null && Duration.between(idleSince, now).toSeconds()
                        >= properties.getCabinIdleShutdownDelaySeconds()) {
                        ObjectNode device = findDevice(getProject(), cabin);
                        if (device.path("enabled").asBoolean(false)) {
                            device.put("enabled", false);
                            setProjectData("set-device", device);
                            LOGGER.info("Disabled idle FUXA cabin MQTT client '{}'", device.path("name").asText());
                        }
                        sessionsByCabin.remove(normalize(cabinKey));
                        idleSinceByCabin.remove(normalize(cabinKey));
                    }
                    return null;
                });
            } catch (Exception ex) {
                LOGGER.warn("Could not reap FUXA cabin session '{}': {}", cabinKey, ex.getMessage());
            }
        }
    }

    private FuxaProperties.CabinClient getCabin(String cabinName) {
        if (!properties.isEnabled()) {
            throw new IllegalStateException("FUXA provisioning is disabled");
        }
        String requested = normalize(cabinName);
        return properties.getCabinClients().entrySet().stream()
            .filter(entry -> normalize(entry.getKey()).equals(requested))
            .map(Map.Entry::getValue)
            .findFirst()
            .orElseThrow(() -> new IllegalArgumentException(
                "No FUXA cabin client mapping exists for '" + cabinName + "'"));
    }

    private ObjectNode getProject() {
        try {
            String result = fuxaClient.get().uri("/api/project").retrieve().body(String.class);
            JsonNode project = objectMapper.readTree(result);
            if (project == null || !project.isObject()) {
                throw new IllegalStateException("FUXA returned an invalid project document");
            }
            return (ObjectNode) project;
        } catch (Exception ex) {
            throw new IllegalStateException("Cannot read the FUXA project: " + ex.getMessage(), ex);
        }
    }

    private ObjectNode findDevice(ObjectNode project, FuxaProperties.CabinClient cabin) {
        JsonNode devices = project.path("devices");
        if (!devices.isObject()) {
            throw new IllegalStateException("FUXA project contains no devices");
        }

        if (hasText(cabin.getDeviceId())) {
            JsonNode byId = devices.path(cabin.getDeviceId().trim());
            if (byId.isObject()) {
                return ((ObjectNode) byId).deepCopy();
            }
        }

        if (hasText(cabin.getDeviceName())) {
            var fields = devices.fields();
            while (fields.hasNext()) {
                Map.Entry<String, JsonNode> entry = fields.next();
                if (cabin.getDeviceName().trim().equals(entry.getValue().path("name").asText())) {
                    return ((ObjectNode) entry.getValue()).deepCopy();
                }
            }
        }
        throw new IllegalArgumentException("Mapped FUXA MQTT client was not found");
    }

    private void setProjectData(String command, JsonNode data) {
        ObjectNode body = objectMapper.createObjectNode();
        body.put("cmd", command);
        body.set("data", data);
        try {
            fuxaClient.post()
                .uri("/api/projectData")
                .contentType(MediaType.APPLICATION_JSON)
                .body(objectMapper.writeValueAsString(body))
                .retrieve()
                .toBodilessEntity();
        } catch (Exception ex) {
            throw new IllegalStateException("Cannot update the FUXA MQTT client: " + ex.getMessage(), ex);
        }
    }

    private FuxaCabinSessionDto toDto(
        String cabinName,
        ObjectNode device,
        FuxaProperties.CabinClient cabin,
        String sessionId,
        boolean sessionActive,
        int activeSessionCount
    ) {
        String viewName = cabin.getViewName();
        String iframeUrl = hasText(viewName)
            ? UriComponentsBuilder.fromUriString(properties.getBaseUrl())
                // FUXA's dedicated view route renders the HMI only, without
                // the Home page navigation and editor-facing chrome.
                .path("/view")
                .queryParam("name", viewName)
                .build()
                .encode()
                .toUriString()
            : null;
        return FuxaCabinSessionDto.builder()
            .cabinName(cabinName)
            .sessionId(sessionId)
            .fuxaDeviceId(device.path("id").asText())
            .fuxaDeviceName(device.path("name").asText())
            .viewName(viewName)
            .iframeUrl(iframeUrl)
            .enabled(device.path("enabled").asBoolean(false))
            .sessionActive(sessionActive)
            .activeSessionCount(activeSessionCount)
            .heartbeatIntervalSeconds(getHeartbeatIntervalSeconds())
            .build();
    }

    private long getHeartbeatIntervalSeconds() {
        long timeout = properties.getCabinSessionTimeoutSeconds();
        long configured = properties.getCabinHeartbeatIntervalSeconds();
        return Math.max(5, Math.min(configured, Math.max(5, timeout - 5)));
    }

    private Map<String, Instant> getSessions(String cabinName) {
        return sessionsByCabin.computeIfAbsent(normalize(cabinName), ignored -> new ConcurrentHashMap<>());
    }

    private void discardExpiredSessions(String cabinName, Map<String, Instant> sessions, Instant now) {
        long timeoutSeconds = properties.getCabinSessionTimeoutSeconds();
        sessions.entrySet().removeIf(entry -> Duration.between(entry.getValue(), now).toSeconds() > timeoutSeconds);
        if (sessions.isEmpty()) {
            idleSinceByCabin.putIfAbsent(normalize(cabinName), now);
        }
    }

    private <T> T withCabinLock(String cabinName, CabinAction<T> action) {
        String normalizedCabinName = normalize(cabinName);
        Object lock = cabinLocks.computeIfAbsent(normalizedCabinName, ignored -> new Object());
        synchronized (lock) {
            return action.run();
        }
    }

    private void requireSessionId(String sessionId) {
        if (!hasText(sessionId)) {
            throw new IllegalArgumentException("Cabin monitor session id is required");
        }
    }

    @FunctionalInterface
    private interface CabinAction<T> {
        T run();
    }

    private boolean hasText(String value) {
        return value != null && !value.trim().isEmpty();
    }

    private String normalize(String value) {
        return value == null ? "" : value.trim().toLowerCase(Locale.ROOT);
    }
}
