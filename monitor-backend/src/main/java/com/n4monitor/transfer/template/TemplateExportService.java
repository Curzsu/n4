package com.n4monitor.transfer.template;

import com.n4monitor.template.entity.DeviceTemplate;
import com.n4monitor.template.entity.PointTemplate;
import com.n4monitor.template.mapper.DeviceTemplateMapper;
import com.n4monitor.template.mapper.PointTemplateMapper;
import lombok.RequiredArgsConstructor;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.stereotype.Service;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.List;

@Service
@RequiredArgsConstructor
public class TemplateExportService {

    private final DeviceTemplateMapper deviceTemplateMapper;
    private final PointTemplateMapper pointTemplateMapper;

    public byte[] exportWorkbook() {
        List<DeviceTemplate> templates = deviceTemplateMapper.findAllIncludingDeleted().stream()
                .filter(template -> !Boolean.TRUE.equals(template.getIsDeleted()))
                .toList();
        List<PointTemplate> points = pointTemplateMapper.findAllIncludingDeleted().stream()
                .filter(point -> !Boolean.TRUE.equals(point.getIsDeleted()))
                .toList();
        try (XSSFWorkbook workbook = new XSSFWorkbook();
             ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            writeDeviceTemplates(workbook.createSheet("device_template"), templates);
            writePointTemplates(workbook.createSheet("point_template"), points);
            workbook.write(output);
            return output.toByteArray();
        } catch (IOException exception) {
            throw new IllegalStateException("Failed to export template workbook", exception);
        }
    }

    private void writeDeviceTemplates(Sheet sheet, List<DeviceTemplate> templates) {
        writeRow(sheet.createRow(0), List.of(
                "device_tmpl_id", "device_tmpl_name", "n4_name", "device_type",
                "manufacturer", "model", "purpose", "alarm_code"));
        int rowIndex = 1;
        for (DeviceTemplate template : templates) {
            writeRow(sheet.createRow(rowIndex++), row(
                    value(template.getDeviceTmplId()), template.getDeviceTmplName(), template.getN4Name(),
                    template.getDeviceType(), template.getManufacturer(), template.getModel(),
                    template.getPurpose(), template.getAlarmCode()));
        }
    }

    private void writePointTemplates(Sheet sheet, List<PointTemplate> points) {
        writeRow(sheet.createRow(0), List.of(
                "point_tmpl_id", "device_tmpl_id", "alarm_code", "point_name", "type", "unit",
                "display_precision"));
        int rowIndex = 1;
        for (PointTemplate point : points) {
            writeRow(sheet.createRow(rowIndex++), row(
                    value(point.getPointTmplId()), value(point.getDeviceTmplId()), point.getAlarmCode(),
                    point.getPointName(), point.getType(), point.getUnit(), value(point.getDisplayPrecision())));
        }
    }

    private void writeRow(Row row, List<String> values) {
        for (int index = 0; index < values.size(); index++) {
            row.createCell(index).setCellValue(values.get(index) == null ? "" : values.get(index));
        }
    }

    private String value(Object value) {
        return value == null ? null : String.valueOf(value);
    }

    private List<String> row(Object... values) {
        return java.util.Arrays.stream(values).map(this::value).toList();
    }
}
