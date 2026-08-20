package com.n4monitor.fuxa.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.n4monitor.fuxa.FuxaProperties;
import com.n4monitor.fuxa.dto.FuxaBindingDto;
import com.n4monitor.fuxa.dto.FuxaDiscoveryRequest;
import com.n4monitor.deployment.entity.DeviceDeployment;
import com.n4monitor.deployment.mapper.DeviceDeploymentMapper;
import com.n4monitor.fuxa.entity.FuxaDeviceBinding;
import com.n4monitor.fuxa.repository.FuxaBindingRepository;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;
import org.springframework.web.util.UriComponentsBuilder;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Duration;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;
import java.util.List;

@Service
public class FuxaBindingService {
    private static final String SCRIPT_ID = "s_n4_dynamic_device_selector";
    private static final String SOURCE_DEVICE_STYLE_START = "/* N4 source-device page begin */";
    private static final String SOURCE_DEVICE_STYLE_END = "/* N4 source-device page end */";

    private final FuxaProperties properties;
    private final FuxaBindingRepository repository;
    private final DeviceDeploymentMapper deviceDeploymentMapper;
    private final ObjectMapper objectMapper;
    private final RestClient fuxaClient;

    public FuxaBindingService(
        FuxaProperties properties,
        FuxaBindingRepository repository,
        DeviceDeploymentMapper deviceDeploymentMapper,
        ObjectMapper objectMapper
    ) {
        this.properties = properties;
        this.repository = repository;
        this.deviceDeploymentMapper = deviceDeploymentMapper;
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

    public FuxaBindingDto discover(FuxaDiscoveryRequest request) {
        requireText(request.getMqttDeviceId(), "mqttDeviceId is required");
        if (!properties.isEnabled()) {
            throw new IllegalStateException("FUXA provisioning is disabled");
        }

        DeviceDeployment device = resolveDevice(request);
        String modelCode = hasText(request.getModelCode()) ? request.getModelCode().trim() : "SPM32";
        String mqttDeviceId = request.getMqttDeviceId().trim();
        String fuxaDeviceId = stableId("d_n4_", modelCode + ":" + mqttDeviceId);
        String fuxaDeviceName = "N4__" + safeName(modelCode) + "__" + safeName(mqttDeviceId);

        FuxaDeviceBinding binding = repository.findByMqttDeviceId(mqttDeviceId)
            .orElseGet(FuxaDeviceBinding::new);
        binding.setMqttDeviceId(mqttDeviceId);
        binding.setDeviceId(device != null ? device.getDeviceDeployId() : request.getDeviceId());
        binding.setDeviceCode(device != null ? (device.getDeviceAlias() != null ? device.getDeviceAlias() : device.getDeviceDeployName()) : request.getDeviceCode());
        binding.setModelCode(modelCode);
        binding.setFuxaDeviceId(fuxaDeviceId);
        binding.setFuxaDeviceName(fuxaDeviceName);
        binding.setViewName(properties.getTemplateViewName());

        try {
            ObjectNode project = getProject();
            JsonNode existing = project.path("devices").path(fuxaDeviceId);
            String mqttTopicPrefix = normalizeTopicPrefix(request.getMqttTopicPrefix());
            if (existing.isMissingNode()
                || !deviceUsesTopicPrefix(existing, mqttTopicPrefix, mqttDeviceId)) {
                ObjectNode concreteDevice = cloneTemplateDevice(
                    project, fuxaDeviceId, fuxaDeviceName, mqttDeviceId, mqttTopicPrefix);
                setProjectData("set-device", concreteDevice);
            }
            binding.setProvisionStatus("READY");
            binding.setLastError(null);
            repository.save(binding);
            return toDto(binding);
        } catch (RuntimeException ex) {
            binding.setProvisionStatus("ERROR");
            binding.setLastError(ex.getMessage());
            repository.save(binding);
            throw ex;
        }
    }

    public FuxaBindingDto getByDeviceId(Long deviceId) {
        return repository.findByDeviceId(deviceId)
            .map(this::toDto)
            .orElseThrow(() -> new IllegalArgumentException(
                "This device has not been discovered by Node-RED yet: " + deviceId));
    }

    public Map<String, Object> prepareTemplate() {
        ObjectNode project = getProject();
        ObjectNode templateDevice = requireObject(
            project.path("devices").path(properties.getTemplateDeviceId()),
            "FUXA template device not found: " + properties.getTemplateDeviceId()
        );
        ObjectNode tags = requireObject(templateDevice.path("tags"), "Template device has no tags");

        Map<String, String> replacements = new HashMap<>();
        tags.fields().forEachRemaining(entry -> {
            String tagName = entry.getValue().path("name").asText();
            replacements.put(entry.getKey(), "@" + tagName);
        });

        ObjectNode view = findView(project);
        int replacementCount = replaceTextValues(view, replacements);
        setProjectData("set-view", view);

        ObjectNode script = objectMapper.createObjectNode();
        script.put("id", SCRIPT_ID);
        script.put("name", "n4OpenSourceDeviceView");
        script.put("mode", "CLIENT");
        script.put("sync", false);
        script.put("permission", 0);
        script.set("parameters", objectMapper.createArrayNode());
        script.set("permissionRoles", objectMapper.createObjectNode().set("enabled", objectMapper.createArrayNode()));
        script.set("scheduling", objectMapper.createObjectNode()
            .put("mode", "interval")
            .put("interval", 0.1)
            .set("schedules", objectMapper.createArrayNode()));
        script.put("code", loadResource("fuxa/source-device-view.js").formatted(
                properties.getTemplateViewName().replace("'", "\\'"))
        );
        setClientScript(script);

        ObjectNode layout = requireObject(project.path("hmi").path("layout"), "FUXA layout not found");
        String sourceDeviceStyles = SOURCE_DEVICE_STYLE_START + "\n"
                + loadResource("fuxa/source-device-styles.css").stripTrailing() + "\n"
                + SOURCE_DEVICE_STYLE_END;
        layout.put("customStyles", upsertMarkedBlock(
            layout.path("customStyles").asText(""),
            SOURCE_DEVICE_STYLE_START,
            SOURCE_DEVICE_STYLE_END,
            sourceDeviceStyles
        ));
        setProjectData("layout", layout);

        return Map.of(
            "mode", "placeholder-source-device",
            "viewName", properties.getTemplateViewName(),
            "tagCount", tags.size(),
            "bindingReplacements", replacementCount,
            "scriptId", SCRIPT_ID,
            "queryParameter", "sourceDeviceId"
        );
    }

    private DeviceDeployment resolveDevice(FuxaDiscoveryRequest request) {
        if (request.getDeviceId() != null) {
            return deviceDeploymentMapper.findByDeviceDeployId(request.getDeviceId());
        }
        if (hasText(request.getMqttDeviceId())) {
            String target = request.getMqttDeviceId().trim();
            List<DeviceDeployment> all = deviceDeploymentMapper.findAll();
            return all.stream()
                .filter(d -> target.equalsIgnoreCase(d.getDeviceDeployName())
                          || (d.getDeviceAlias() != null && target.equalsIgnoreCase(d.getDeviceAlias()))
                          || (d.getN4Address() != null && target.equalsIgnoreCase(d.getN4Address())))
                .findFirst()
                .orElse(null);
        }
        return null;
    }

    private ObjectNode cloneTemplateDevice(
        ObjectNode project,
        String deviceId,
        String deviceName,
        String mqttDeviceId,
        String mqttTopicPrefix
    ) {
        ObjectNode source = requireObject(
            project.path("devices").path(properties.getTemplateDeviceId()),
            "FUXA template device not found: " + properties.getTemplateDeviceId()
        );
        ObjectNode clone = source.deepCopy();
        clone.put("id", deviceId);
        clone.put("name", deviceName);

        ObjectNode clonedTags = objectMapper.createObjectNode();
        requireObject(source.path("tags"), "Template device has no tags").fields().forEachRemaining(entry -> {
            ObjectNode tag = ((ObjectNode) entry.getValue()).deepCopy();
            String tagId = stableId("t_n4_", deviceId + ":" + tag.path("name").asText());
            tag.put("id", tagId);
            if (tag.has("address")) {
                String sourceAddress = tag.path("address").asText();
                if (hasText(mqttTopicPrefix)) {
                    int pointSeparator = sourceAddress.lastIndexOf('/');
                    String pointName = pointSeparator >= 0
                        ? sourceAddress.substring(pointSeparator + 1)
                        : sourceAddress;
                    tag.put("address", mqttTopicPrefix + "/" + mqttDeviceId + "/" + pointName);
                } else {
                    tag.put("address", sourceAddress
                        .replace(properties.getTemplateTopicDeviceId(), mqttDeviceId));
                }
            }
            tag.remove(java.util.List.of("value", "timestamp"));
            clonedTags.set(tagId, tag);
        });
        clone.set("tags", clonedTags);
        return clone;
    }

    private boolean deviceUsesTopicPrefix(
        JsonNode device,
        String mqttTopicPrefix,
        String mqttDeviceId
    ) {
        if (!hasText(mqttTopicPrefix)) {
            return !device.isMissingNode();
        }
        String expectedPrefix = mqttTopicPrefix + "/" + mqttDeviceId + "/";
        JsonNode tags = device.path("tags");
        if (!tags.isObject()) {
            return false;
        }
        Iterator<JsonNode> values = tags.elements();
        while (values.hasNext()) {
            String address = values.next().path("address").asText();
            if (address.startsWith(expectedPrefix)) {
                return true;
            }
        }
        return false;
    }

    private static String normalizeTopicPrefix(String topicPrefix) {
        if (!hasText(topicPrefix)) {
            return null;
        }
        return topicPrefix.trim().replaceAll("/+$", "");
    }

    private ObjectNode findView(ObjectNode project) {
        JsonNode views = project.path("hmi").path("views");
        if (!views.isArray()) {
            throw new IllegalStateException("FUXA project contains no views");
        }
        for (JsonNode candidate : views) {
            if (properties.getTemplateViewId().equals(candidate.path("id").asText())) {
                return ((ObjectNode) candidate).deepCopy();
            }
        }
        throw new IllegalStateException("FUXA template view not found: " + properties.getTemplateViewId());
    }

    private int replaceTextValues(JsonNode node, Map<String, String> replacements) {
        int count = 0;
        if (node.isObject()) {
            Iterator<Map.Entry<String, JsonNode>> fields = node.fields();
            while (fields.hasNext()) {
                Map.Entry<String, JsonNode> field = fields.next();
                JsonNode child = field.getValue();
                if (child.isTextual() && replacements.containsKey(child.asText())) {
                    ((ObjectNode) node).put(field.getKey(), replacements.get(child.asText()));
                    count++;
                } else {
                    count += replaceTextValues(child, replacements);
                }
            }
        } else if (node.isArray()) {
            for (JsonNode child : node) {
                count += replaceTextValues(child, replacements);
            }
        }
        return count;
    }

    private static String upsertMarkedBlock(
        String existing,
        String startMarker,
        String endMarker,
        String replacement
    ) {
        String current = existing == null ? "" : existing;
        int start = current.indexOf(startMarker);
        int end = start >= 0 ? current.indexOf(endMarker, start) : -1;
        if (start >= 0 && end >= 0) {
            current = current.substring(0, start)
                + current.substring(end + endMarker.length());
        }
        current = current.trim();
        return current.isEmpty() ? replacement.trim() : current + "\n\n" + replacement.trim();
    }

    private ObjectNode getProject() {
        String result = fuxaClient.get().uri("/api/project").retrieve().body(String.class);
        try {
            return requireObject(
                objectMapper.readTree(result),
                "FUXA returned an invalid project document"
            );
        } catch (Exception ex) {
            throw new IllegalStateException(
                "Cannot parse the FUXA project document: " + ex.getMessage(), ex);
        }
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
        ObjectNode project = getProject();
        ArrayNode scripts = objectMapper.createArrayNode();
        JsonNode existingScripts = project.get("scripts");

        if (existingScripts != null && existingScripts.isArray()) {
            existingScripts.forEach(existing -> {
                if (!SCRIPT_ID.equals(existing.path("id").asText())) {
                    scripts.add(existing.deepCopy());
                }
            });
        } else if (existingScripts != null
            && existingScripts.isObject()
            && !SCRIPT_ID.equals(existingScripts.path("id").asText())) {
            scripts.add(existingScripts.deepCopy());
        }
        scripts.add(script);
        project.set("scripts", scripts);

        ObjectNode clientAccess = project.path("client-access").isObject()
            ? ((ObjectNode) project.path("client-access")).deepCopy()
            : objectMapper.createObjectNode();
        Set<String> allowedFunctions = new LinkedHashSet<>();
        JsonNode existingFunctions = clientAccess.path("scriptSystemFunctions");
        if (existingFunctions.isArray()) {
            existingFunctions.forEach(function -> allowedFunctions.add(function.asText()));
        }
        allowedFunctions.add("$openCard");
        ArrayNode functions = objectMapper.createArrayNode();
        allowedFunctions.forEach(functions::add);
        clientAccess.set("scriptSystemFunctions", functions);
        project.set("client-access", clientAccess);

        try {
            fuxaClient.post()
                .uri("/api/project")
                .contentType(MediaType.APPLICATION_JSON)
                .body(objectMapper.writeValueAsString(project))
                .retrieve()
                .toBodilessEntity();
        } catch (Exception ex) {
            throw new IllegalStateException("Cannot update FUXA client scripts: " + ex.getMessage(), ex);
        }
    }

    private FuxaBindingDto toDto(FuxaDeviceBinding binding) {
        String iframeUrl = UriComponentsBuilder.fromUriString(properties.getBaseUrl())
            .path("/home")
            .queryParam("viewName", binding.getViewName())
            .queryParam("sourceDeviceId", binding.getFuxaDeviceId())
            .build().encode().toUriString();
        return FuxaBindingDto.builder()
            .deviceId(binding.getDeviceId())
            .deviceCode(binding.getDeviceCode())
            .mqttDeviceId(binding.getMqttDeviceId())
            .modelCode(binding.getModelCode())
            .fuxaDeviceId(binding.getFuxaDeviceId())
            .fuxaDeviceName(binding.getFuxaDeviceName())
            .viewName(binding.getViewName())
            .iframeUrl(iframeUrl)
            .status(binding.getProvisionStatus())
            .build();
    }

    private static ObjectNode requireObject(JsonNode node, String message) {
        if (node == null || !node.isObject()) {
            throw new IllegalStateException(message);
        }
        return (ObjectNode) node;
    }

    private static String stableId(String prefix, String value) {
        try {
            byte[] hash = MessageDigest.getInstance("SHA-256")
                .digest(value.getBytes(StandardCharsets.UTF_8));
            StringBuilder hex = new StringBuilder();
            for (int i = 0; i < 10; i++) {
                hex.append(String.format("%02x", hash[i]));
            }
            return prefix + hex;
        } catch (Exception ex) {
            throw new IllegalStateException("Cannot create stable FUXA id", ex);
        }
    }

    private static String safeName(String value) {
        return value.trim().replaceAll("[^A-Za-z0-9_.-]", "_");
    }

    private static boolean hasText(String value) {
        return value != null && !value.trim().isEmpty();
    }

    private static void requireText(String value, String message) {
        if (!hasText(value)) {
            throw new IllegalArgumentException(message);
        }
    }
}
