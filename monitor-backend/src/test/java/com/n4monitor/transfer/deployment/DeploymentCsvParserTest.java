package com.n4monitor.transfer.deployment;

import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.nio.charset.Charset;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class DeploymentCsvParserTest {

    @Test
    void parsesQuotedFieldsAndDerivesCanonicalTypeWithoutUsingDataType() throws Exception {
        String csv = String.join("\n",
                "*path,*name,*type,unit,dataType,pollFrequeny,addressFormat,modbusAddress,numberOfBits,menu,allPath,mqttPath",
                "MVP_A1_00_Collector_00,Voltage, numeric,kV,,normal,modbus,20,,\"1:Low,2:High\",slot:/points/Voltage,A1_MVP_A1/MVP_A1/MVP_A1_00_Collector_00/Voltage"
        );

        DeploymentCsvData result = new DeploymentCsvParser().parse(
                "MVP_A1.csv", new ByteArrayInputStream(csv.getBytes(Charset.forName("UTF-8"))));

        assertEquals(1, result.rows().size());
        DeploymentCsvRow row = result.rows().getFirst();
        assertEquals("MVP_A1_00_Collector_00", row.devicePath());
        assertEquals("Collector", row.n4Name());
        assertEquals("Voltage", row.pointName());
        assertEquals("enum", row.type());
        assertEquals("kV", row.unit());
        assertEquals("20", row.modbusAddress());
        assertEquals("1:Low,2:High", row.menu());
        assertNull(row.dataType());
    }

    @Test
    void fallsBackToGbkWhenUtf8DecodingFails() throws Exception {
        String csv = String.join("\r\n",
                "*path,*name,*type,unit,dataType,allPath,mqttPath",
                "Device,Temperature,numeric,\u2103,,\u8def\u5f84,Root/Device/Temperature"
        );

        DeploymentCsvData result = new DeploymentCsvParser().parse(
                "legacy.csv", new ByteArrayInputStream(csv.getBytes(Charset.forName("GBK"))));

        assertEquals("\u2103", result.rows().getFirst().unit());
        assertEquals("\u8def\u5f84", result.rows().getFirst().allPath());
    }
}
