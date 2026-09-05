package com.n4monitor.alarm.export;

import com.n4monitor.alarm.dto.AlarmRecordDto;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellStyle;
import org.apache.poi.ss.usermodel.FillPatternType;
import org.apache.poi.ss.usermodel.Font;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.xssf.streaming.SXSSFSheet;
import org.apache.poi.xssf.streaming.SXSSFWorkbook;
import org.apache.poi.xssf.usermodel.XSSFCellStyle;
import org.apache.poi.xssf.usermodel.XSSFColor;
import org.apache.poi.xssf.usermodel.XSSFFont;

import java.io.IOException;
import java.io.OutputStream;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.Map;

/**
 * Streams {@link AlarmRecordDto} rows into a single-sheet xlsx workbook with a
 * constant memory footprint: {@link SXSSFWorkbook} keeps only a sliding window
 * of rows in memory and flushes older rows to disk as the file grows, so the
 * export size is bounded by disk, not heap.
 * <p>
 * Column widths are tracked manually (max cell character length per column)
 * instead of POI's auto-size, which depends on AWT font metrics and behaves
 * differently in headless containers. Widths are clamped so long messages
 * cannot stretch columns absurdly.
 * <p>
 * Lifecycle: {@link #create(Path)}, one {@link #writeRow} per data row, then
 * {@link #finish()} serializes the workbook. {@link #close()} releases
 * resources and is safe to call after {@link #finish()}; on a failure path
 * (close without finish) the caller owns deleting the partial file.
 */
public final class EventExcelWriter implements AutoCloseable {

    public static final String XLSX_CONTENT_TYPE =
            "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet";

    static final String SHEET_NAME = "Events";

    private static final String[] HEADERS = {
            "Time", "Value", "Stage", "Level", "Severity", "Source", "Message",
            "Location", "Status", "Ack. By", "Alarm Start Time", "Duration (s)", "Path", "Write Time"};

    private static final int SEVERITY_COLUMN = 4;
    private static final int DURATION_COLUMN = 11;

    private static final int MIN_COLUMN_WIDTH_CHARS = 8;
    private static final int MAX_COLUMN_WIDTH_CHARS = 40;

    private static final DateTimeFormatter TIME_FORMAT =
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    /** The placeholder the history queries write when a timestamp is absent. */
    private static final String MISSING_TIME = "-";
    /** Dark text color used on top of the severity fills (matches the UI). */
    private static final byte[] DARK_TEXT = new byte[] {(byte) 0x11, (byte) 0x18, (byte) 0x27};

    /**
     * Severity fill colors, mirroring frontend/src/config/severityColors.ts so
     * the exported file reads like the page.
     */
    private static final Map<String, byte[]> SEVERITY_RGB = Map.of(
            "Critical", new byte[] {(byte) 0xFF, 0x00, 0x00},
            "Major", new byte[] {(byte) 0xFF, (byte) 0xC0, 0x00},
            "Minor", new byte[] {(byte) 0xFF, (byte) 0xFF, 0x00},
            "Warning", new byte[] {(byte) 0x3B, (byte) 0x82, (byte) 0xF6});

    private final SXSSFWorkbook workbook;
    private final SXSSFSheet sheet;
    private final OutputStream out;

    private final CellStyle headerStyle;
    private final Map<String, CellStyle> severityStyles;
    /** Max display length seen per column, used to size columns on finish. */
    private final int[] columnCharWidths = new int[HEADERS.length];

    /** Index of the next data row; row 0 is the header. */
    private int nextRowIndex = 1;

    private boolean finished;

    /**
     * Create a writer that serializes to {@code target} on {@link #finish()}.
     * The file is opened immediately so a bad path fails before any rows are
     * read from TDengine.
     */
    public static EventExcelWriter create(java.nio.file.Path target) throws IOException {
        SXSSFWorkbook workbook = new SXSSFWorkbook(200);
        OutputStream out = java.nio.file.Files.newOutputStream(target);
        return new EventExcelWriter(workbook, out);
    }

    private EventExcelWriter(SXSSFWorkbook workbook, OutputStream out) {
        this.workbook = workbook;
        this.out = out;
        this.sheet = workbook.createSheet(SHEET_NAME);
        this.sheet.createFreezePane(0, 1);
        this.headerStyle = buildHeaderStyle();
        this.severityStyles = buildSeverityStyles();
        writeHeaderRow();
    }

    private void writeHeaderRow() {
        Row header = sheet.createRow(0);
        for (int i = 0; i < HEADERS.length; i++) {
            Cell cell = header.createCell(i);
            cell.setCellValue(HEADERS[i]);
            cell.setCellStyle(headerStyle);
            trackWidth(i, HEADERS[i].length());
        }
    }

    /** Append one data row to the sheet. */
    public void writeRow(AlarmRecordDto row) {
        Row sheetRow = sheet.createRow(nextRowIndex++);
        writeText(sheetRow, 0, blankIfMissing(row.getTime()));
        writeText(sheetRow, 1, row.getValue());
        writeText(sheetRow, 2, row.getStageMessage());
        writeText(sheetRow, 3, row.getLevel());
        writeSeverity(sheetRow, row.getSeverity());
        writeText(sheetRow, 5, row.getSource());
        writeText(sheetRow, 6, row.getMessage());
        writeText(sheetRow, 7, row.getLocation());
        writeText(sheetRow, 8, row.getStatus());
        writeText(sheetRow, 9, row.getAckBy());
        writeText(sheetRow, 10, blankIfMissing(row.getAlarmStartTime()));
        writeDuration(sheetRow, row.getDuration());
        writeText(sheetRow, 12, row.getPath());
        writeText(sheetRow, 13, formatMillis(row.getTs()));
    }

    private void writeText(Row row, int column, String value) {
        Cell cell = row.createCell(column);
        cell.setCellValue(value == null ? "" : value);
        trackWidth(column, value == null ? 0 : value.length());
    }

    private void writeSeverity(Row row, String severity) {
        Cell cell = row.createCell(SEVERITY_COLUMN);
        cell.setCellValue(severity == null ? "" : severity);
        trackWidth(SEVERITY_COLUMN, severity == null ? 0 : severity.length());
        if (severity != null) {
            CellStyle style = severityStyles.get(severity);
            if (style != null) {
                cell.setCellStyle(style);
            }
        }
    }

    private void writeDuration(Row row, String seconds) {
        Cell cell = row.createCell(DURATION_COLUMN);
        Long parsed = parseSeconds(seconds);
        if (parsed != null) {
            cell.setCellValue(parsed);
            trackWidth(DURATION_COLUMN, seconds.trim().length());
        }
    }

    /**
     * Serialize the workbook to the target file. Idempotent; after this the
     * file is complete and {@link #close()} only releases resources.
     */
    public void finish() throws IOException {
        if (finished) {
            return;
        }
        for (int i = 0; i < HEADERS.length; i++) {
            int chars = Math.max(MIN_COLUMN_WIDTH_CHARS, Math.min(MAX_COLUMN_WIDTH_CHARS,
                    columnCharWidths[i] + 2));
            sheet.setColumnWidth(i, chars * 256);
        }
        workbook.write(out);
        workbook.dispose();
        out.close();
        finished = true;
    }

    @Override
    public void close() {
        if (finished) {
            return;
        }
        // Failure path: the partial file is the caller's to delete; release
        // the workbook's temp files and the stream quietly.
        try {
            workbook.dispose();
        } catch (RuntimeException ignored) {
            // best effort cleanup
        }
        try {
            out.close();
        } catch (IOException ignored) {
            // best effort cleanup
        }
        finished = true;
    }

    private void trackWidth(int column, int length) {
        if (length > columnCharWidths[column]) {
            columnCharWidths[column] = length;
        }
    }

    private CellStyle buildHeaderStyle() {
        CellStyle style = workbook.createCellStyle();
        Font font = workbook.createFont();
        font.setBold(true);
        style.setFont(font);
        style.setFillForegroundColor(new XSSFColor(new byte[] {(byte) 0xE8, (byte) 0xEA, (byte) 0xED}, null));
        style.setFillPattern(FillPatternType.SOLID_FOREGROUND);
        return style;
    }

    private Map<String, CellStyle> buildSeverityStyles() {
        Map<String, CellStyle> styles = new HashMap<>();
        for (Map.Entry<String, byte[]> entry : SEVERITY_RGB.entrySet()) {
            XSSFCellStyle style = (XSSFCellStyle) workbook.createCellStyle();
            style.setFillForegroundColor(new XSSFColor(entry.getValue(), null));
            style.setFillPattern(FillPatternType.SOLID_FOREGROUND);
            XSSFFont font = (XSSFFont) workbook.createFont();
            font.setBold(true);
            font.setColor(new XSSFColor(DARK_TEXT, null));
            style.setFont(font);
            styles.put(entry.getKey(), style);
        }
        return styles;
    }

    private static String blankIfMissing(String time) {
        return MISSING_TIME.equals(time) ? "" : time;
    }

    private static Long parseSeconds(String raw) {
        if (raw == null || raw.isBlank() || MISSING_TIME.equals(raw.trim())) {
            return null;
        }
        try {
            return Long.parseLong(raw.trim());
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private static String formatMillis(Long epochMillis) {
        if (epochMillis == null) {
            return "";
        }
        return LocalDateTime.ofInstant(Instant.ofEpochMilli(epochMillis), ZoneId.systemDefault())
                .format(TIME_FORMAT);
    }
}
