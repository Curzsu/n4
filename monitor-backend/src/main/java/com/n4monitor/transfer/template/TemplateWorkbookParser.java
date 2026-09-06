package com.n4monitor.transfer.template;

import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.DataFormatter;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

@Component
public class TemplateWorkbookParser {

    private static final String POINT_NAME_HEADER = "\u8bbe\u5907\u6d4b\u70b9";
    private static final String N4_NAME_HEADER = "N4\u53d8\u91cf\u5185\u5b57\u7b26";
    private static final String LOCATION_HEADER = "\u8bbe\u5907\u4f4d\u7f6e";
    private static final String PANEL_NAME_HEADER = "\u9762\u677f\u547d\u540d";
    private static final String PURPOSE_HEADER = "\u753b\u9762\u7528\u5904";

    private final DataFormatter dataFormatter = new DataFormatter(Locale.ROOT);

    public TemplateWorkbookData parse(InputStream inputStream) throws IOException {
        try (Workbook workbook = new XSSFWorkbook(inputStream)) {
            List<TemplateSheetData> sheets = new ArrayList<>();
            for (Sheet sheet : workbook) {
                sheets.add(parseSheet(sheet));
            }
            return new TemplateWorkbookData(sheets);
        }
    }

    private TemplateSheetData parseSheet(Sheet sheet) {
        Row headerRow = findHeaderRow(sheet);
        Map<String, Integer> columns = buildColumnMap(headerRow);
        requireColumn(columns, POINT_NAME_HEADER, sheet.getSheetName());
        requireColumn(columns, N4_NAME_HEADER, sheet.getSheetName());

        List<TemplatePointData> points = new ArrayList<>();
        Set<String> seenPointNames = new LinkedHashSet<>();
        String n4Name = null;
        for (int rowIndex = headerRow.getRowNum() + 1; rowIndex <= sheet.getLastRowNum(); rowIndex++) {
            Row row = sheet.getRow(rowIndex);
            if (row == null) {
                continue;
            }
            String pointName = value(row, columns.get(POINT_NAME_HEADER));
            String rowN4Name = value(row, columns.get(N4_NAME_HEADER));
            if (n4Name == null && rowN4Name != null) {
                n4Name = rowN4Name;
            }
            if (pointName == null || !seenPointNames.add(pointName)) {
                continue;
            }
            points.add(new TemplatePointData(
                    pointName,
                    rowN4Name,
                    value(row, columns.get(LOCATION_HEADER)),
                    value(row, columns.get(PANEL_NAME_HEADER)),
                    value(row, columns.get(PURPOSE_HEADER)),
                    null,
                    null));
        }

        String[] metadata = splitSheetName(sheet.getSheetName());
        return new TemplateSheetData(
                sheet.getSheetName(),
                sheet.getSheetName(),
                metadata[0],
                metadata[1],
                metadata[2],
                metadata[3],
                n4Name,
                points);
    }

    private Row findHeaderRow(Sheet sheet) {
        for (Row row : sheet) {
            Set<String> headers = new HashSet<>();
            for (Cell cell : row) {
                String header = text(cell);
                if (header != null) {
                    headers.add(header);
                }
            }
            if (headers.contains(POINT_NAME_HEADER) && headers.contains(N4_NAME_HEADER)) {
                return row;
            }
        }
        throw new IllegalArgumentException("Workbook sheet has no header: " + sheet.getSheetName());
    }

    private Map<String, Integer> buildColumnMap(Row headerRow) {
        Map<String, Integer> columns = new LinkedHashMap<>();
        for (Cell cell : headerRow) {
            String header = text(cell);
            if (header != null) {
                columns.put(header, cell.getColumnIndex());
            }
        }
        return columns;
    }

    private void requireColumn(Map<String, Integer> columns, String header, String sheetName) {
        if (!columns.containsKey(header)) {
            throw new IllegalArgumentException("Missing workbook column '" + header + "' in sheet '" + sheetName + "'");
        }
    }

    private String value(Row row, Integer columnIndex) {
        if (columnIndex == null) {
            return null;
        }
        return text(row.getCell(columnIndex));
    }

    private String text(Cell cell) {
        if (cell == null) {
            return null;
        }
        String value = dataFormatter.formatCellValue(cell).trim();
        return value.isEmpty() ? null : value;
    }

    private String[] splitSheetName(String sheetName) {
        String[] parts = sheetName.split("_", -1);
        return new String[]{
                part(parts, 0),
                part(parts, 1),
                part(parts, 2),
                part(parts, 3)
        };
    }

    private String part(String[] parts, int index) {
        return index < parts.length && !parts[index].isBlank() ? parts[index].trim() : null;
    }
}
