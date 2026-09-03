package com.n4monitor.transfer.deployment;

import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class DeploymentImportTemplateServiceTest {

    @Test
    void generatedCsvCanBeReadByTheDeploymentImporter() throws Exception {
        byte[] content = new DeploymentImportTemplateService().generate();

        DeploymentCsvData data = new DeploymentCsvParser().parse(
                "deployment-import-template.csv",
                new ByteArrayInputStream(content));

        assertEquals(1, data.rows().size());
        DeploymentCsvRow row = data.rows().getFirst();
        assertEquals("Example_System_00_Collector_00", row.devicePath());
        assertEquals("Example_Point", row.pointName());
        assertEquals("numeric", row.type());
        assertEquals("Collector", row.n4Name());
        assertNull(row.dataType());
        assertEquals("slot:/Example/System/Example_Device/Example_Point", row.mqttPath());
        assertEquals('\uFEFF', new String(content, StandardCharsets.UTF_8).charAt(0));
    }
}
