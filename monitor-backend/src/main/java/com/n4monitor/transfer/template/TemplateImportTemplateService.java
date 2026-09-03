package com.n4monitor.transfer.template;

import org.springframework.stereotype.Service;

import java.io.IOException;
import java.io.InputStream;

@Service
public class TemplateImportTemplateService {

    private static final String TEMPLATE_RESOURCE =
            "/template-import/kvtp-a-single-panel-plan.xlsx";

    public byte[] generate() {
        try (InputStream inputStream = TemplateImportTemplateService.class
                .getResourceAsStream(TEMPLATE_RESOURCE)) {
            if (inputStream == null) {
                throw new IllegalStateException("Template import resource is missing: " + TEMPLATE_RESOURCE);
            }
            return inputStream.readAllBytes();
        } catch (IOException exception) {
            throw new IllegalStateException("Failed to read template import workbook", exception);
        }
    }
}
