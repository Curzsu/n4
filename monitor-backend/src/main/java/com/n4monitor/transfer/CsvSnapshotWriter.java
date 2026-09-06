package com.n4monitor.transfer;

import java.util.List;

public final class CsvSnapshotWriter {

    private CsvSnapshotWriter() {
    }

    public static String write(List<String> header, List<List<String>> rows) {
        StringBuilder output = new StringBuilder("\uFEFF");
        appendRow(output, header);
        for (List<String> row : rows) {
            appendRow(output, row);
        }
        return output.toString();
    }

    private static void appendRow(StringBuilder output, List<String> row) {
        for (int index = 0; index < row.size(); index++) {
            if (index > 0) {
                output.append(',');
            }
            output.append(escape(row.get(index)));
        }
        output.append("\r\n");
    }

    private static String escape(String value) {
        if (value == null) {
            return "";
        }
        String normalized = value;
        if (normalized.contains(",") || normalized.contains("\"")
                || normalized.contains("\r") || normalized.contains("\n")) {
            return '"' + normalized.replace("\"", "\"\"") + '"';
        }
        return normalized;
    }
}
