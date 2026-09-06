package com.n4monitor.transfer.deployment;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;

public final class DeploymentCsvRow {

    private final String sourceFile;
    private final int rowNumber;
    private final Map<String, String> values;

    DeploymentCsvRow(String sourceFile, int rowNumber, Map<String, String> values) {
        this.sourceFile = sourceFile;
        this.rowNumber = rowNumber;
        this.values = Collections.unmodifiableMap(new LinkedHashMap<>(values));
    }

    public String sourceFile() {
        return sourceFile;
    }

    public int rowNumber() {
        return rowNumber;
    }

    public Map<String, String> values() {
        return values;
    }

    public String value(String name) {
        return values.get(DeploymentCsvParser.normalizeHeader(name));
    }

    public String devicePath() {
        return value("*path");
    }

    public String pointName() {
        return value("*name");
    }

    public String sourceType() {
        return value("*type");
    }

    public String type() {
        String menuValue = menu();
        if (menuValue != null) {
            return "enum";
        }
        return "numeric".equalsIgnoreCase(trim(sourceType())) ? "numeric" : "enum";
    }

    public String unit() {
        return value("unit");
    }

    public String dataType() {
        return value("datatype");
    }

    public String pollFrequency() {
        return value("pollfrequency");
    }

    public String addressFormat() {
        return value("addressformat");
    }

    public String modbusAddress() {
        return value("modbusaddress");
    }

    public String numberOfBits() {
        return value("numberofbits");
    }

    public String trueText() {
        return value("truetext");
    }

    public String falseText() {
        return value("falsetext");
    }

    public String menu() {
        String menu = value("menu");
        if (menu != null) {
            return menu;
        }
        return value("enum");
    }

    public String allPath() {
        return value("allpath");
    }

    public String mqttPath() {
        return value("mqttpath");
    }

    public String n4Name() {
        String path = require(devicePath(), "*path");
        String[] parts = path.split("_");
        if (parts.length < 2 || parts[parts.length - 2].isBlank()) {
            throw new IllegalArgumentException("Cannot derive n4_name from *path: " + path);
        }
        return parts[parts.length - 2].trim();
    }

    public String deviceAlias() {
        String[] parts = require(devicePath(), "*path").split("_");
        StringBuilder result = new StringBuilder();
        for (String part : parts) {
            if ("00".equals(part)) {
                continue;
            }
            if (result.length() > 0) {
                result.append('_');
            }
            result.append(part);
        }
        return result.toString();
    }

    public String deviceN4Address() {
        return firstSegments(mqttPath(), 3);
    }

    public String systemName() {
        return firstSegments(mqttPath(), 2);
    }

    public String field(String name) {
        return value(name);
    }

    private String firstSegments(String path, int count) {
        String[] parts = require(path, "mqttPath").split("/");
        if (parts.length < count) {
            throw new IllegalArgumentException("Invalid mqttPath: " + path);
        }
        return String.join("/", java.util.Arrays.copyOf(parts, count));
    }

    private String require(String value, String field) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("CSV field is required: " + field);
        }
        return value;
    }

    private String trim(String value) {
        return value == null ? null : value.trim().toLowerCase(Locale.ROOT);
    }
}
