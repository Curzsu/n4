package com.n4monitor.fuxa.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.n4monitor.fuxa.FuxaProperties;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

import java.time.Duration;

@Service
public class FuxaBindingService {
    private static final String CABIN_PANEL_BRIDGE_SCRIPT_ID = "s_n4_cabin_device_panel_bridge";
    private static final String CABIN_RUNTIME_BRIDGE_SCRIPT_ID = "s_n4_cabin_runtime_mapper";
    private volatile boolean cabinPanelBridgeInstalled;
    private volatile boolean cabinRuntimeBridgeInstalled;

    private final FuxaProperties properties;
    private final ObjectMapper objectMapper;
    private final RestClient fuxaClient;

    public FuxaBindingService(
        FuxaProperties properties,
        ObjectMapper objectMapper
    ) {
        this.properties = properties;
        this.objectMapper = objectMapper;
        SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
        requestFactory.setConnectTimeout(Duration.ofSeconds(5));
        requestFactory.setReadTimeout(Duration.ofSeconds(15));
        this.fuxaClient = RestClient.builder()
            .baseUrl(properties.getBaseUrl())
            .requestFactory(requestFactory)
            .defaultHeader(HttpHeaders.CONNECTION, "close")
            .build();
    }

    /**
     * Installs a small client-side bridge used only by N4-embedded cabin
     * views. It observes existing slot-pool openers and sends their real
     * from-to mappings to the outer Vue page with postMessage.
     */
    public synchronized void ensureCabinPanelBridgeInstalled() {
        if (cabinPanelBridgeInstalled) {
            return;
        }
        ObjectNode script = objectMapper.createObjectNode();
        script.put("id", CABIN_PANEL_BRIDGE_SCRIPT_ID);
        script.put("name", "n4CabinDevicePanelBridge");
        script.put("mode", "CLIENT");
        script.put("sync", false);
        script.put("permission", 0);
        script.set("parameters", objectMapper.createArrayNode());
        script.set("permissionRoles", objectMapper.createObjectNode().set("enabled", objectMapper.createArrayNode()));
        script.set("scheduling", objectMapper.createObjectNode()
            .put("mode", "interval")
            .put("interval", 0.2)
            .set("schedules", objectMapper.createArrayNode()));
        script.put("code", loadResource("fuxa/cabin-device-panel-bridge.js"));
        setClientScript(script);
        cabinPanelBridgeInstalled = true;
    }

    /**
     * Keeps the embedded FUXA client alive while N4 switches between cabin
     * views. The first iframe load still uses URL parameters; later changes
     * arrive through postMessage and do not reload the Angular application.
     */
    public synchronized void ensureCabinRuntimeBridgeInstalled() {
        if (cabinRuntimeBridgeInstalled) {
            return;
        }
        ObjectNode script = objectMapper.createObjectNode();
        script.put("id", CABIN_RUNTIME_BRIDGE_SCRIPT_ID);
        script.put("name", "n4OpenReusableCabinView");
        script.put("mode", "CLIENT");
        script.put("sync", false);
        script.put("permission", 0);
        script.set("parameters", objectMapper.createArrayNode());
        script.set("permissionRoles", objectMapper.createObjectNode().set("enabled", objectMapper.createArrayNode()));
        script.set("scheduling", objectMapper.createObjectNode()
            .put("mode", "start")
            .put("interval", 0)
            .set("schedules", objectMapper.createArrayNode()));
        script.put("code", loadResource("fuxa/cabin-runtime-bridge.js"));
        setClientScript(script);
        cabinRuntimeBridgeInstalled = true;
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
            throw new IllegalStateException("Cannot update FUXA project data: " + ex.getMessage(), ex);
        }
    }

    /** Load a bundled FUXA snippet (client script / styles) from the classpath. */
    private String loadResource(String path) {
        try (var in = new org.springframework.core.io.ClassPathResource(path).getInputStream()) {
            return new String(in.readAllBytes(), java.nio.charset.StandardCharsets.UTF_8);
        } catch (java.io.IOException e) {
            throw new IllegalStateException("Missing bundled FUXA resource: " + path, e);
        }
    }

    private void setClientScript(ObjectNode script) {
        // FUXA projects with high-resolution cabin layouts easily exceed the
        // /api/project request limit. set-script updates exactly one script
        // and preserves every view, image, device and existing script.
        setProjectData("set-script", script);
    }

}
