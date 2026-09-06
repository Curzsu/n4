package com.n4monitor.alarm.export;

import com.n4monitor.alarm.dto.AlarmRecordDto;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellStyle;
import org.apache.poi.ss.usermodel.FillPatternType;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.xssf.usermodel.XSSFColor;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tests for the streaming Excel writer: header/data shape, duration parsing,
 * blank handling and the severity fill colors shared with the frontend.
 */
class EventExcelWriterTest {

    @TempDir
    Path tempDir;

    private AlarmRecordDto row(String severity, String duration) {
        AlarmRecordDto dto = new AlarmRecordDto();
        dto.setTime("2026-09-05 10:00:00");
        dto.setValue("42.5");
        dto.setStageMessage("Start");
        dto.setLevel("High");
        dto.setSeverity(severity);
        dto.setSource("CHWS/CH-01");
        dto.setMessage("High pressure");
        dto.setLocation("Building A");
        dto.setStatus("Unack");
        dto.setAckBy("admin");
        dto.setAlarmStartTime("2026-09-05 09:59:00");
        dto.setDuration(duration);
        dto.setPath("A1/MVP/point_01");
        dto.setTs(1_788_000_000_000L);
        return dto;
    }

    @Test
    void writesHeaderAndDataRows() throws Exception {
        Path target = tempDir.resolve("out.xlsx");
        try (EventExcelWriter writer = EventExcelWriter.create(target)) {
            writer.writeRow(row("Critical", "125"));
            writer.finish();
        }

        assertThat(target).exists();
        try (XSSFWorkbook workbook = new XSSFWorkbook(Files.newInputStream(target))) {
            Sheet sheet = workbook.getSheet(EventExcelWriter.SHEET_NAME);
            assertThat(sheet).isNotNull();

            Row header = sheet.getRow(0);
            assertThat(header.getCell(0).getStringCellValue()).isEqualTo("Time");
            assertThat(header.getCell(4).getStringCellValue()).isEqualTo("Severity");
            assertThat(header.getCell(13).getStringCellValue()).isEqualTo("Write Time");

            Row data = sheet.getRow(1);
            assertThat(data.getCell(0).getStringCellValue()).isEqualTo("2026-09-05 10:00:00");
            assertThat(data.getCell(1).getStringCellValue()).isEqualTo("42.5");
            assertThat(data.getCell(2).getStringCellValue()).isEqualTo("Start");
            assertThat(data.getCell(3).getStringCellValue()).isEqualTo("High");
            assertThat(data.getCell(5).getStringCellValue()).isEqualTo("CHWS/CH-01");
            assertThat(data.getCell(12).getStringCellValue()).isEqualTo("A1/MVP/point_01");
        }
    }

    @Test
    void durationIsWrittenAsNumberWhenParseable() throws Exception {
        Path target = tempDir.resolve("out.xlsx");
        try (EventExcelWriter writer = EventExcelWriter.create(target)) {
            writer.writeRow(row("Major", "125"));
            writer.writeRow(row("Major", "not-a-number"));
            writer.finish();
        }

        try (XSSFWorkbook workbook = new XSSFWorkbook(Files.newInputStream(target))) {
            Sheet sheet = workbook.getSheet(EventExcelWriter.SHEET_NAME);
            assertThat(sheet.getRow(1).getCell(11).getNumericCellValue()).isEqualTo(125.0);
            // Unparseable duration stays blank instead of poisoning the file.
            assertThat(sheet.getRow(2).getCell(11).getStringCellValue()).isEmpty();
        }
    }

    @Test
    void missingTimePlaceholderBecomesBlank() throws Exception {
        AlarmRecordDto dto = row("Minor", "10");
        dto.setTime("-");
        dto.setAlarmStartTime("-");
        dto.setTs(null);

        Path target = tempDir.resolve("out.xlsx");
        try (EventExcelWriter writer = EventExcelWriter.create(target)) {
            writer.writeRow(dto);
            writer.finish();
        }

        try (XSSFWorkbook workbook = new XSSFWorkbook(Files.newInputStream(target))) {
            Row data = workbook.getSheet(EventExcelWriter.SHEET_NAME).getRow(1);
            assertThat(data.getCell(0).getStringCellValue()).isEmpty();
            assertThat(data.getCell(10).getStringCellValue()).isEmpty();
            assertThat(data.getCell(13).getStringCellValue()).isEmpty();
        }
    }

    @Test
    void severityCellCarriesTheFrontendPaletteFill() throws Exception {
        Path target = tempDir.resolve("out.xlsx");
        try (EventExcelWriter writer = EventExcelWriter.create(target)) {
            writer.writeRow(row("Critical", "10"));
            writer.writeRow(row("Major", "10"));
            writer.finish();
        }

        try (XSSFWorkbook workbook = new XSSFWorkbook(Files.newInputStream(target))) {
            Sheet sheet = workbook.getSheet(EventExcelWriter.SHEET_NAME);
            CellStyle criticalStyle = sheet.getRow(1).getCell(4).getCellStyle();
            CellStyle majorStyle = sheet.getRow(2).getCell(4).getCellStyle();

            // POI 5 returns the FillPatternType enum from getFillPattern().
            assertThat(criticalStyle.getFillPattern()).isEqualTo(FillPatternType.SOLID_FOREGROUND);
            assertThat(majorStyle.getFillPattern()).isEqualTo(FillPatternType.SOLID_FOREGROUND);
            // Two severities get two distinct styles, both filled #FF0000 / #FFC000.
            assertThat(fillRgb(criticalStyle)).containsExactly((byte) 0xFF, 0x00, 0x00);
            assertThat(fillRgb(majorStyle)).containsExactly((byte) 0xFF, (byte) 0xC0, 0x00);
        }
    }

    /** Extract the RGB triplet of a cell style's foreground fill. */
    private static byte[] fillRgb(CellStyle style) {
        byte[] rgb = ((XSSFColor) style.getFillForegroundColorColor()).getRGB();
        return Arrays.copyOfRange(rgb, rgb.length - 3, rgb.length);
    }
}
