package com.n4monitor.transfer.deployment;

import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.CharBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.Charset;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import org.springframework.stereotype.Component;

@Component
public class DeploymentCsvParser {

    private static final List<String> REQUIRED_HEADERS = List.of("*path", "*name", "*type", "allpath", "mqttpath");

    public DeploymentCsvData parse(String fileName, InputStream inputStream) throws IOException {
        String content = decode(inputStream.readAllBytes());
        List<List<String>> records = parseRecords(content);
        if (records.isEmpty()) {
            throw new IllegalArgumentException("CSV file is empty: " + fileName);
        }

        int headerRecordIndex = 0;
        while (headerRecordIndex < records.size()
                && records.get(headerRecordIndex).stream().allMatch(value -> value == null || value.isBlank())) {
            headerRecordIndex++;
        }
        if (headerRecordIndex >= records.size()) {
            throw new IllegalArgumentException("CSV file has no header: " + fileName);
        }

        Map<String, Integer> headerIndexes = new LinkedHashMap<>();
        List<String> headers = records.get(headerRecordIndex);
        for (int index = 0; index < headers.size(); index++) {
            String normalized = normalizeHeader(headers.get(index));
            if (!normalized.isEmpty()) {
                headerIndexes.put(normalized, index);
            }
        }
        for (String requiredHeader : REQUIRED_HEADERS) {
            if (!headerIndexes.containsKey(requiredHeader)) {
                throw new IllegalArgumentException("Missing CSV column '" + requiredHeader + "' in file '" + fileName + "'");
            }
        }

        List<DeploymentCsvRow> rows = new ArrayList<>();
        for (int rowIndex = headerRecordIndex + 1; rowIndex < records.size(); rowIndex++) {
            List<String> record = records.get(rowIndex);
            if (record.stream().allMatch(value -> value == null || value.isBlank())) {
                continue;
            }
            Map<String, String> values = new LinkedHashMap<>();
            for (Map.Entry<String, Integer> entry : headerIndexes.entrySet()) {
                int columnIndex = entry.getValue();
                values.put(entry.getKey(), columnIndex < record.size() ? blankToNull(record.get(columnIndex)) : null);
            }
            rows.add(new DeploymentCsvRow(fileName, rowIndex + 1, values));
        }
        return new DeploymentCsvData(fileName, rows);
    }

    public static String normalizeHeader(String header) {
        if (header == null) {
            return "";
        }
        String normalized = header.replace("\uFEFF", "").trim().toLowerCase(Locale.ROOT);
        if ("enum".equals(normalized)) {
            return "menu";
        }
        if ("pollfrequeny".equals(normalized)) {
            return "pollfrequency";
        }
        if ("historyintervale".equals(normalized)) {
            return "historyinterval";
        }
        return normalized;
    }

    private String decode(byte[] bytes) {
        if (bytes.length >= 3
                && (bytes[0] & 0xFF) == 0xEF
                && (bytes[1] & 0xFF) == 0xBB
                && (bytes[2] & 0xFF) == 0xBF) {
            return new String(bytes, 3, bytes.length - 3, StandardCharsets.UTF_8);
        }
        try {
            CharBuffer decoded = StandardCharsets.UTF_8.newDecoder()
                    .onMalformedInput(CodingErrorAction.REPORT)
                    .onUnmappableCharacter(CodingErrorAction.REPORT)
                    .decode(ByteBuffer.wrap(bytes));
            return decoded.toString();
        } catch (CharacterCodingException ignored) {
            return Charset.forName("GB18030").decode(ByteBuffer.wrap(bytes)).toString();
        }
    }

    private List<List<String>> parseRecords(String content) {
        List<List<String>> records = new ArrayList<>();
        List<String> record = new ArrayList<>();
        StringBuilder field = new StringBuilder();
        boolean quoted = false;
        for (int index = 0; index < content.length(); index++) {
            char current = content.charAt(index);
            if (quoted) {
                if (current == '"') {
                    if (index + 1 < content.length() && content.charAt(index + 1) == '"') {
                        field.append('"');
                        index++;
                    } else {
                        quoted = false;
                    }
                } else {
                    field.append(current);
                }
                continue;
            }
            if (current == '"' && field.isEmpty()) {
                quoted = true;
            } else if (current == ',') {
                record.add(field.toString());
                field.setLength(0);
            } else if (current == '\n' || current == '\r') {
                record.add(field.toString());
                field.setLength(0);
                if (!record.isEmpty()) {
                    records.add(record);
                }
                record = new ArrayList<>();
                if (current == '\r' && index + 1 < content.length() && content.charAt(index + 1) == '\n') {
                    index++;
                }
            } else {
                field.append(current);
            }
        }
        if (quoted) {
            throw new IllegalArgumentException("CSV contains an unterminated quoted field");
        }
        if (!record.isEmpty() || field.length() > 0) {
            record.add(field.toString());
            records.add(record);
        }
        return records;
    }

    private String blankToNull(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }
}
