package com.n4monitor.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.n4monitor.config.FuxaProperties;
import com.n4monitor.dto.FuxaBindingDto;
import com.n4monitor.dto.FuxaDiscoveryRequest;
import com.n4monitor.entity.Device;
import com.n4monitor.entity.FuxaDeviceBinding;
import com.n4monitor.mapper.DeviceMapper;
import com.n4monitor.repository.FuxaBindingRepository;
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
import java.util.Map;

@Service
public class FuxaBindingService {
    private static final String SCRIPT_ID = "s_n4_dynamic_device_selector";

    private final FuxaProperties properties;
    private final FuxaBindingRepository repository;
    private final DeviceMapper deviceMapper;
    private final ObjectMapper objectMapper;
    private final RestClient fuxaClient;

    public FuxaBindingService(
        FuxaProperties properties,
        FuxaBindingRepository repository,
        DeviceMapper deviceMapper,
        ObjectMapper objectMapper
    ) {
        this.properties = properties;
        this.repository = repository;
        this.deviceMapper = deviceMapper;
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

        Device device = resolveDevice(request);
        String modelCode = hasText(request.getModelCode()) ? request.getModelCode().trim() : "SPM32";
        String mqttDeviceId = request.getMqttDeviceId().trim();
        String fuxaDeviceId = stableId("d_n4_", modelCode + ":" + mqttDeviceId);
        String fuxaDeviceName = "N4__" + safeName(modelCode) + "__" + safeName(mqttDeviceId);

        FuxaDeviceBinding binding = repository.findByMqttDeviceId(mqttDeviceId)
            .orElseGet(FuxaDeviceBinding::new);
        binding.setMqttDeviceId(mqttDeviceId);
        binding.setDeviceId(device != null ? device.getDeviceId() : request.getDeviceId());
        binding.setDeviceCode(device != null ? device.getDeviceCode() : request.getDeviceCode());
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

        ObjectNode adapter = objectMapper.createObjectNode();
        adapter.put("id", properties.getAdapterId());
        adapter.put("name", properties.getAdapterName());
        adapter.put("type", "internal");
        adapter.put("enabled", true);
        adapter.put("polling", 1000);
        adapter.set("property", objectMapper.createObjectNode());

        ObjectNode adapterTags = objectMapper.createObjectNode();
        Map<String, String> replacements = new HashMap<>();
        tags.fields().forEachRemaining(entry -> {
            ObjectNode sourceTag = (ObjectNode) entry.getValue();
            String adapterTagId = stableId("t_n4a_", sourceTag.path("name").asText());
            ObjectNode adapterTag = sourceTag.deepCopy();
            adapterTag.put("id", adapterTagId);
            adapterTag.remove(java.util.List.of("value", "timestamp", "address", "options"));
            adapterTags.set(adapterTagId, adapterTag);
            replacements.put(entry.getKey(), adapterTagId);
        });
        adapter.set("tags", adapterTags);
        setProjectData("set-device", adapter);

        ObjectNode view = findView(project);
        int replacementCount = replaceTextValues(view, replacements);
        setProjectData("set-view", view);

        ObjectNode script = objectMapper.createObjectNode();
        script.put("id", SCRIPT_ID);
        script.put("name", "n4SelectFuxaDevice");
        script.put("mode", "CLIENT");
        script.put("sync", false);
        script.put("permission", 0);
        script.set("parameters", objectMapper.createArrayNode());
        script.set("permissionRoles", objectMapper.createObjectNode().set("enabled", objectMapper.createArrayNode()));
        script.set("scheduling", objectMapper.createObjectNode()
            .put("mode", "interval")
            .put("interval", 1)
            .set("schedules", objectMapper.createArrayNode()));
        script.put("code", """
            const selectedDevice = new URLSearchParams(window.location.search).get('deviceName');
            if (selectedDevice && window.__n4FuxaDevice !== selectedDevice) {
              await $setAdapterToDevice('%s', selectedDevice);
              window.__n4FuxaDevice = selectedDevice;
              $setView('%s', true);
            }
            """.formatted(
                properties.getAdapterName().replace("'", "\\'"),
                properties.getTemplateViewName().replace("'", "\\'")
            ));
        setClientScript(script);

        ObjectNode clientAccess = objectMapper.createObjectNode();
        ArrayNode functions = objectMapper.createArrayNode();
        functions.add("$setAdapterToDevice");
        functions.add("$setView");
        clientAccess.set("scriptSystemFunctions", functions);
        setProjectData("client-access", clientAccess);

        return Map.of(
            "adapterName", properties.getAdapterName(),
            "viewName", properties.getTemplateViewName(),
            "tagCount", adapterTags.size(),
            "bindingReplacements", replacementCount,
            "scriptId", SCRIPT_ID
        );
    }

    private Device resolveDevice(FuxaDiscoveryRequest request) {
        if (request.getDeviceId() != null) {
            return deviceMapper.findByDeviceId(request.getDeviceId());
        }
        if (hasText(request.getDeviceCode())) {
            Device byCode = deviceMapper.findByDeviceCode(request.getDeviceCode().trim());
            if (byCode != null) {
                return byCode;
            }
        }
        if (hasText(request.getMqttDeviceId())) {
            return deviceMapper.findByDeviceName(request.getMqttDeviceId().trim());
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
            .queryParam("deviceName", binding.getFuxaDeviceName())
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
