package com.n4monitor.transfer.template;

import java.util.List;

public record TemplateSheetData(
        String sheetName,
        String deviceTemplateName,
        String deviceType,
        String manufacturer,
        String model,
        String purpose,
        String n4Name,
        List<TemplatePointData> points) {

    public TemplateSheetData {
        points = List.copyOf(points);
    }
}
