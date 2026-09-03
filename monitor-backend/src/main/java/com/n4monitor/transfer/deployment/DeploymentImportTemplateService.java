package com.n4monitor.transfer.deployment;

import com.n4monitor.transfer.CsvSnapshotWriter;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
public class DeploymentImportTemplateService {

    private static final List<String> HEADERS = List.of(
            "*path", "*name", "*type", "unit", "min", "max", "trueText", "falseText",
            "conversionScale", "conversionOffset", "dataType", "pollFrequeny", "addressFormat",
            "modbusAddress", "numberOfBits", "alarmType", "alarmName", "alarmTimeDelay",
            "alarmToOffnormalText", "outOfRangeFaultAlgorithmHighLimit",
            "outOfRangeFaultAlgorithmLowLimit", "outOfRangeFaultAlgorithmDeadband",
            "outOfRangeFaultAlgorithmHighLimitText", "outOfRangeFaultAlgorithmLowLimitText",
            "outOfRangeFaultAlgorithmLimitEnableHighLimitEnable",
            "outOfRangeFaultAlgorithmLimitEnableLowLimitEnable", "outOfRangeAlgorithmHighLimit",
            "outOfRangeAlgorithmLowLimit", "outOfRangeAlgorithmDeadband",
            "outOfRangeAlgorithmHighLimitText", "outOfRangeAlgorithmLowLimitText",
            "outOfRangeAlgorithmLimitEnableHighLimitEnable",
            "outOfRangeAlgorithmLimitEnableLowLimitEnable",
            "booleanChangeOfStateAlgorithmAlarmValue", "alarmClass", "historyType", "historyName",
            "historyEnabled", "historyRecordCount", "historyIntervale", "menu", "allPath", "mqttPath");

    public byte[] generate() {
        Map<String, String> example = new LinkedHashMap<>();
        example.put("*path", "Example_System_00_Collector_00");
        example.put("*name", "Example_Point");
        example.put("*type", "numeric");
        example.put("unit", "C");
        example.put("min", "0");
        example.put("max", "100");
        example.put("conversionScale", "1");
        example.put("conversionOffset", "0");
        example.put("pollFrequeny", "normal");
        example.put("addressFormat", "hex");
        example.put("modbusAddress", "BB4");
        example.put("numberOfBits", "13");
        example.put("allPath", "slot:/Example/System/Example_Device/Example_Point");
        example.put("mqttPath", "slot:/Example/System/Example_Device/Example_Point");
        return CsvSnapshotWriter.write(
                        HEADERS,
                        List.of(HEADERS.stream().map(example::get).toList()))
                .getBytes(StandardCharsets.UTF_8);
    }
}
