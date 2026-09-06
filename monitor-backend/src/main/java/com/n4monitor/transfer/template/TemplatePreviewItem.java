package com.n4monitor.transfer.template;

/**
 * Preview summary for a single template sheet during workbook import.
 */
public record TemplatePreviewItem(
        String sheetName,
        String templateName,
        String n4Name,
        String deviceType,
        String manufacturer,
        String model,
        int pointCount,
        String action) {
}
