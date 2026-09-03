package com.n4monitor.transfer.template;

import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

class TemplateImportTemplateServiceTest {

    @Test
    void downloadedWorkbookMatchesTeacherTemplateLayout() throws Exception {
        byte[] content = new TemplateImportTemplateService().generate();

        TemplateWorkbookData workbook = new TemplateWorkbookParser().parse(
                new ByteArrayInputStream(content));

        assertEquals(35, workbook.sheets().size());

        TemplateSheetData firstSheet = workbook.sheets().getFirst();
        assertEquals("ZB_EATON_FXD860S_MVincomer", firstSheet.sheetName());
        assertEquals("ZBmvin", firstSheet.n4Name());
        assertEquals(108, firstSheet.points().size());
        assertEquals("Communication_Status", firstSheet.points().getFirst().pointName());

        TemplateSheetData screenshotSheet = workbook.sheets().stream()
                .filter(sheet -> "EM_Pilot_SPM33a".equals(sheet.sheetName()))
                .findFirst()
                .orElse(null);
        assertNotNull(screenshotSheet);
        assertEquals("EM33a", screenshotSheet.n4Name());
        assertEquals(27, screenshotSheet.points().size());
    }
}
