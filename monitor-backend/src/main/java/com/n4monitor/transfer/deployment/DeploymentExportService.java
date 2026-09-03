package com.n4monitor.transfer.deployment;

import com.n4monitor.alarm.mapper.AlarmRuleEnumMapper;
import com.n4monitor.alarm.mapper.AlarmRuleNumericMapper;
import com.n4monitor.deployment.entity.DeviceDeployment;
import com.n4monitor.deployment.entity.PointDeployment;
import com.n4monitor.deployment.mapper.DeviceDeploymentMapper;
import com.n4monitor.deployment.mapper.PointDeploymentMapper;
import com.n4monitor.entity.AlarmRuleEnumEntity;
import com.n4monitor.entity.AlarmRuleNumericEntity;
import com.n4monitor.transfer.CsvSnapshotWriter;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

@Service
@RequiredArgsConstructor
public class DeploymentExportService {

    private final DeviceDeploymentMapper deviceDeploymentMapper;
    private final PointDeploymentMapper pointDeploymentMapper;
    private final AlarmRuleEnumMapper alarmRuleEnumMapper;
    private final AlarmRuleNumericMapper alarmRuleNumericMapper;

    public byte[] exportSnapshot() {
        try (ByteArrayOutputStream output = new ByteArrayOutputStream();
             ZipOutputStream zip = new ZipOutputStream(output, StandardCharsets.UTF_8)) {
            writeEntry(zip, "device_deployment.csv", deviceCsv());
            writeEntry(zip, "point_deployment.csv", pointCsv());
            writeEntry(zip, "alarm_rule_enum.csv", enumRuleCsv());
            writeEntry(zip, "alarm_rule_numeric.csv", numericRuleCsv());
            zip.finish();
            return output.toByteArray();
        } catch (IOException exception) {
            throw new IllegalStateException("Failed to export deployment snapshot", exception);
        }
    }

    private String deviceCsv() {
        List<List<String>> rows = new ArrayList<>();
        for (DeviceDeployment deployment : deviceDeploymentMapper.findAllIncludingDeleted()) {
            if (Boolean.TRUE.equals(deployment.getIsDeleted())) {
                continue;
            }
            rows.add(row(
                    value(deployment.getDeviceDeployId()), value(deployment.getDeviceTmplId()),
                    value(deployment.getNavNodeId()), deployment.getFacePlateId(), deployment.getAlarmCode(),
                    deployment.getSystemName(), deployment.getDeviceDeployName(), deployment.getDeviceAlias(),
                    deployment.getN4Address(), deployment.getIpAddress(), deployment.getMacAddress(),
                    value(deployment.getStatus()), deployment.getInstallDate(), deployment.getWarrantyExpire(),
                    deployment.getLocation(), deployment.getDepartment(), deployment.getResponsiblePerson(),
                    deployment.getContactPhone(), deployment.getExtraAttrs()));
        }
        return CsvSnapshotWriter.write(List.of(
                "device_deploy_id", "device_tmpl_id", "nav_node_id", "face_plate_id", "alarm_code",
                "system_name", "device_deploy_name", "device_alias", "n4_address", "ip_address",
                "mac_address", "status", "install_date", "warranty_expire", "location", "department",
                "responsible_person", "contact_phone", "extra_attrs"), rows);
    }

    private String pointCsv() {
        List<List<String>> rows = new ArrayList<>();
        for (PointDeployment point : pointDeploymentMapper.findAllIncludingDeleted()) {
            if (Boolean.TRUE.equals(point.getIsDeleted())) {
                continue;
            }
            rows.add(row(
                    value(point.getPointDeployId()), value(point.getPointTmplId()), value(point.getDeviceDeployId()),
                    point.getAlarmCode(), point.getPointName(), point.getN4Address(), point.getPointCode(),
                    "", point.getUnit(), value(point.getDisplayPrecision()), point.getAllPath(), point.getType(),
                    value(point.getConversionScale()), value(point.getConversionOffset()), point.getPollFrequency(),
                    point.getAddressFormat(), point.getModbusAddress(), value(point.getNumberOfBits())));
        }
        return CsvSnapshotWriter.write(List.of(
                "point_deploy_id", "point_tmpl_id", "device_deploy_id", "alarm_code", "point_name",
                "n4_address", "point_code", "data_type", "unit", "display_precision", "all_path", "type",
                "conversion_scale", "conversion_offset", "poll_frequency", "address_format",
                "modbus_address", "number_of_bits"), rows);
    }

    private String enumRuleCsv() {
        List<List<String>> rows = new ArrayList<>();
        for (AlarmRuleEnumEntity rule : alarmRuleEnumMapper.findAll()) {
            rows.add(row(
                    value(rule.getId()), rule.getAlarmName(), rule.getPointType(), value(rule.getTimeDelay()),
                    rule.getEnumValue(), rule.getEnumMeaning(), rule.getSeverity(), rule.getMessage(),
                    value(rule.getIsDeleted())));
        }
        return CsvSnapshotWriter.write(List.of(
                "id", "alarm_name", "point_type", "time_delay", "enum_value", "enum_meaning", "severity",
                "message", "is_deleted"), rows);
    }

    private String numericRuleCsv() {
        List<List<String>> rows = new ArrayList<>();
        for (AlarmRuleNumericEntity rule : alarmRuleNumericMapper.findAll()) {
            rows.add(row(
                    value(rule.getId()), rule.getAlarmName(), rule.getPointType(), value(rule.getTimeDelay()),
                    rule.getDeadband(), rule.getHighHighValue(), rule.getHighHighSeverity(), rule.getHighHighMessage(),
                    rule.getHighValue(), rule.getHighSeverity(), rule.getHighMessage(), rule.getLowValue(),
                    rule.getLowSeverity(), rule.getLowMessage(), rule.getLowLowValue(), rule.getLowLowSeverity(),
                    rule.getLowLowMessage(), value(rule.getIsDeleted())));
        }
        return CsvSnapshotWriter.write(List.of(
                "id", "alarm_name", "point_type", "time_delay", "deadband", "high_high_value",
                "high_high_severity", "high_high_message", "high_value", "high_severity", "high_message",
                "low_value", "low_severity", "low_message", "low_low_value", "low_low_severity",
                "low_low_message", "is_deleted"), rows);
    }

    private void writeEntry(ZipOutputStream zip, String name, String content) throws IOException {
        zip.putNextEntry(new ZipEntry(name));
        zip.write(content.getBytes(StandardCharsets.UTF_8));
        zip.closeEntry();
    }

    private String value(Object value) {
        return value == null ? null : String.valueOf(value);
    }

    private List<String> row(Object... values) {
        return java.util.Arrays.stream(values).map(this::value).toList();
    }
}
