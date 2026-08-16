package com.n4monitor.fuxa.service;

import com.n4monitor.fuxa.FuxaProperties;
import com.n4monitor.fuxa.dto.FuxaCabinSessionDto;
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
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Activates the preconfigured FUXA MQTT client for one cabin.  It deliberately
 * does not create MQTT connections itself: FUXA remains the MQTT client.
 */
@Service
public class FuxaCabinClientService {
    private static final Logger LOGGER = LoggerFactory.getLogger(FuxaCabinClientService.class);
    private final FuxaProperties properties;
    private final RestClient fuxaClient;
    private final Map<String, Object> cabinLocks = new ConcurrentHashMap<>();
    private final Map<String, Set<String>> sessionsByCabin = new ConcurrentHashMap<>();
    private final Map<String, Instant> idleSinceByCabin = new ConcurrentHashMap<>();
    private final Set<String> enabledCabins = ConcurrentHashMap.newKeySet();

    public FuxaCabinClientService(FuxaProperties properties) {
        this.properties = properties;
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
        LOGGER.info("Opening FUXA cabin session: cabin='{}', session='{}'", cabinName, sessionId);
        return withCabinLock(cabinName, () -> {
            FuxaProperties.CabinClient cabin = getCabin(cabinName);
            Set<String> sessions = getSessions(cabinName);
            if (!enabledCabins.contains(normalize(cabinName))) {
                enableDevice(cabin, true);
                enabledCabins.add(normalize(cabinName));
            }
            sessions.add(sessionId);
            idleSinceByCabin.remove(normalize(cabinName));
            LOGGER.info("Opened FUXA cabin session: cabin='{}', activeSessions={}", cabinName, sessions.size());
            return toDto(cabinName, cabin, sessionId, true, sessions.size());
        });
    }

    public FuxaCabinSessionDto releaseSession(String cabinName, String sessionId) {
        requireSessionId(sessionId);
        return withCabinLock(cabinName, () -> {
            FuxaProperties.CabinClient cabin = getCabin(cabinName);
            Set<String> sessions = getSessions(cabinName);
            sessions.remove(sessionId);
            if (sessions.isEmpty()) {
                idleSinceByCabin.putIfAbsent(normalize(cabinName), Instant.now());
            }
            return toDto(cabinName, cabin, sessionId, false, sessions.size());
        });
    }

    public FuxaCabinSessionDto status(String cabinName) {
        return withCabinLock(cabinName, () -> {
            FuxaProperties.CabinClient cabin = getCabin(cabinName);
            Set<String> sessions = getSessions(cabinName);
            return toDto(cabinName, cabin, null, false, sessions.size());
        });
    }

    @Scheduled(fixedDelayString = "${fuxa.cabin-session-reaper-ms:10000}")
    public void disableIdleCabinClients() {
        var cabinKeys = new HashSet<String>();
        cabinKeys.addAll(sessionsByCabin.keySet());
        cabinKeys.addAll(idleSinceByCabin.keySet());
        for (String cabinKey : cabinKeys) {
            try {
                withCabinLock(cabinKey, () -> {
                    FuxaProperties.CabinClient cabin = getCabin(cabinKey);
                    Set<String> sessions = getSessions(cabinKey);
                    Instant now = Instant.now();
                    if (!sessions.isEmpty()) {
                        idleSinceByCabin.remove(normalize(cabinKey));
                        return null;
                    }

                    Instant idleSince = idleSinceByCabin.putIfAbsent(normalize(cabinKey), now);
                    if (idleSince != null && Duration.between(idleSince, now).toSeconds()
                        >= properties.getCabinIdleShutdownDelaySeconds()) {
                        if (enabledCabins.contains(normalize(cabinKey))) {
                            enableDevice(cabin, false);
                            enabledCabins.remove(normalize(cabinKey));
                            LOGGER.info("Disabled idle FUXA cabin MQTT client '{}'", cabin.getDeviceName());
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

    private void enableDevice(FuxaProperties.CabinClient cabin, boolean enable) {
        if (!hasText(cabin.getDeviceName())) {
            throw new IllegalArgumentException("Mapped FUXA MQTT client name is required");
        }
        Map<String, Object> body = Map.of(
            "params", Map.of(
                "functionName", "$enableDevice",
                "parameters", List.of(cabin.getDeviceName().trim(), enable)
            )
        );
        try {
            fuxaClient.post()
                .uri("/api/runSysFunction")
                .contentType(MediaType.APPLICATION_JSON)
                .body(body)
                .retrieve()
                .toBodilessEntity();
        } catch (Exception ex) {
            throw new IllegalStateException("Cannot update the FUXA MQTT client: " + ex.getMessage(), ex);
        }
    }

    private FuxaCabinSessionDto toDto(
        String cabinName,
        FuxaProperties.CabinClient cabin,
        String sessionId,
        boolean sessionActive,
        int activeSessionCount
    ) {
        String hostViewName = cabin.getViewName();
        String templateViewName = hasText(cabin.getTemplateViewName())
            ? cabin.getTemplateViewName().trim()
            : hostViewName;
        String iframeUrl = hasText(hostViewName)
            ? UriComponentsBuilder.fromUriString(properties.getBaseUrl())
                // FUXA's dedicated view route renders the HMI only, without
                // the Home page navigation and editor-facing chrome.
                .path("/home")
                .queryParam("viewName", hostViewName)
                .queryParam("mqttDeviceName", cabin.getDeviceName())
                .queryParam("cabinViewName", templateViewName)
                .build()
                .encode()
                .toUriString()
            : null;
        return FuxaCabinSessionDto.builder()
            .cabinName(cabinName)
            .sessionId(sessionId)
            .fuxaDeviceId(cabin.getDeviceId())
            .fuxaDeviceName(cabin.getDeviceName())
            .viewName(templateViewName)
            .iframeUrl(iframeUrl)
            .viewWidth(cabin.getViewWidth())
            .viewHeight(cabin.getViewHeight())
            .enabled(enabledCabins.contains(normalize(cabinName)))
            .sessionActive(sessionActive)
            .activeSessionCount(activeSessionCount)
            .build();
    }

    private Set<String> getSessions(String cabinName) {
        return sessionsByCabin.computeIfAbsent(
            normalize(cabinName), ignored -> ConcurrentHashMap.newKeySet());
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
