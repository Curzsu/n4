package com.n4monitor.transfer.template;

import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class TemplateWorkbookParserTest {

    @Test
    void parsesSheetMetadataAndPointRowsFromWorkbook() throws Exception {
        byte[] workbookBytes = createWorkbook();

        TemplateWorkbookData workbook = new TemplateWorkbookParser().parse(
                new ByteArrayInputStream(workbookBytes));

        assertEquals(1, workbook.sheets().size());
        TemplateSheetData sheet = workbook.sheets().getFirst();
        assertEquals("AHU_Acme_X1_HVAC", sheet.deviceTemplateName());
        assertEquals("AHU", sheet.deviceType());
        assertEquals("Acme", sheet.manufacturer());
        assertEquals("X1", sheet.model());
        assertEquals("HVAC", sheet.purpose());
        assertEquals("AHU_Temperature", sheet.n4Name());
        assertEquals(2, sheet.points().size());
        assertEquals("Temperature", sheet.points().getFirst().pointName());
        assertEquals("AHU_Temperature", sheet.points().getFirst().n4Name());
        assertNull(sheet.points().getFirst().type());
    }

    private byte[] createWorkbook() throws Exception {
        try (XSSFWorkbook workbook = new XSSFWorkbook();
             ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            var sheet = workbook.createSheet("AHU_Acme_X1_HVAC");
            sheet.createRow(0).createCell(0).setCellValue("Template information");
            var header = sheet.createRow(1);
            header.createCell(0).setCellValue("\u8bbe\u5907\u6d4b\u70b9");
            header.createCell(1).setCellValue("N4\u53d8\u91cf\u5185\u5b57\u7b26");
            header.createCell(2).setCellValue("\u8bbe\u5907\u4f4d\u7f6e");
            header.createCell(3).setCellValue("\u9762\u677f\u547d\u540d");
            header.createCell(4).setCellValue("\u753b\u9762\u7528\u5904");

            var firstPoint = sheet.createRow(2);
            firstPoint.createCell(0).setCellValue("Temperature");
            firstPoint.createCell(1).setCellValue("AHU_Temperature");
            firstPoint.createCell(2).setCellValue("Cabin");

            var secondPoint = sheet.createRow(3);
            secondPoint.createCell(0).setCellValue("Humidity");
            secondPoint.createCell(1).setCellValue("AHU_Humidity");

            workbook.write(output);
            return output.toByteArray();
        }
    }
}
