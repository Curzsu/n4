package com.n4monitor.transfer.template;

import java.util.List;

public record TemplateWorkbookData(List<TemplateSheetData> sheets) {

    public TemplateWorkbookData {
        sheets = List.copyOf(sheets);
    }
}
