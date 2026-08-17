package com.n4monitor.flink;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Parsing of point metadata: the Redis point-rule map entry ({"type",
 * "alarm_code"}) and the mqtt path segments (point name, location, source).
 * Extracted verbatim from AlarmProcessFunction.
 */
final class PointMetaParser {

    private static final Logger log = LoggerFactory.getLogger(PointMetaParser.class);

    private PointMetaParser() {
    }

    /** Resolved point metadata: evaluation type + alarm rule code. */
    record PointMeta(String type, String alarmName) {

        static final PointMeta EMPTY = new PointMeta("", "");
    }

    /**
     * Parse the point-rule map hash value. Returns an empty meta (never null)
     * when the entry is missing or malformed; a parse failure is logged once
     * per message as before.
     */
    static PointMeta parse(ObjectMapper objectMapper, String json, String path) {
        if (json == null || json.isEmpty()) return PointMeta.EMPTY;
        String type = "";
        String alarmName = "";
        try {
            var metaNode = objectMapper.readTree(json);
            if (metaNode.has("type") && !metaNode.get("type").isNull()) {
                type = metaNode.get("type").asText().trim();
            }
            if (metaNode.has("alarm_code") && !metaNode.get("alarm_code").isNull()) {
                alarmName = metaNode.get("alarm_code").asText().trim();
            }
        } catch (Exception e) {
            log.warn("Failed to parse point metadata for path {}: {}", path, e.getMessage());
        }
        return new PointMeta(type, alarmName);
    }

    static String extractPointName(String path) {
        if (path == null || path.isEmpty()) return "";
        String normalized = path.startsWith("/") ? path.substring(1) : path;
        String[] parts = normalized.split("/");
        return parts.length > 0 ? parts[parts.length - 1] : "";
    }

    static String extractLocation(String path) {
        if (path == null || path.isEmpty()) return "";
        String normalized = path.startsWith("/") ? path.substring(1) : path;
        String[] parts = normalized.split("/");
        return parts.length > 0 ? parts[0] : "";
    }

    static String extractSource(String path) {
        if (path == null || path.isEmpty()) return "";
        String normalized = path.startsWith("/") ? path.substring(1) : path;
        String[] parts = normalized.split("/");
        if (parts.length >= 2) {
            return parts[parts.length - 2] + "/" + parts[parts.length - 1];
        }
        return parts.length == 1 ? parts[0] : "";
    }
}
