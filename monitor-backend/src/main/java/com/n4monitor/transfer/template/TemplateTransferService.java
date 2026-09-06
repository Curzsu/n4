package com.n4monitor.transfer.template;

import com.n4monitor.template.entity.DeviceTemplate;
import com.n4monitor.template.entity.PointTemplate;
import com.n4monitor.template.mapper.DeviceTemplateMapper;
import com.n4monitor.template.mapper.PointTemplateMapper;
import com.n4monitor.transfer.ImportStats;
import com.n4monitor.transfer.ImportSummary;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Service
@RequiredArgsConstructor
public class TemplateTransferService {

    private static final String KEY_SEPARATOR = "\u0000";

    private final TemplateWorkbookParser workbookParser;
    private final DeviceTemplateMapper deviceTemplateMapper;
    private final PointTemplateMapper pointTemplateMapper;

    @Transactional
    public ImportSummary importWorkbook(MultipartFile file, boolean dryRun) {
        ImportStats stats = new ImportStats(dryRun);
        if (file == null || file.isEmpty()) {
            stats.error("EMPTY_FILE", "Template workbook is empty", null, null);
            return stats.toSummary();
        }

        TemplateWorkbookData workbook;
        try {
            workbook = workbookParser.parse(file.getInputStream());
        } catch (IOException | IllegalArgumentException exception) {
            stats.error("INVALID_WORKBOOK", exception.getMessage(), file.getOriginalFilename(), null);
            return stats.toSummary();
        }
        if (workbook.sheets().isEmpty()) {
            stats.error("EMPTY_WORKBOOK", "Template workbook contains no sheets", file.getOriginalFilename(), null);
            return stats.toSummary();
        }
        stats.sourceFileCount(1);
        stats.sourceRowCount(workbook.sheets().stream().mapToInt(sheet -> sheet.points().size()).sum());

        List<DeviceTemplate> existingTemplates = deviceTemplateMapper.findAllIncludingDeleted();
        List<PointTemplate> existingPoints = pointTemplateMapper.findAllIncludingDeleted();
        Map<String, DeviceTemplate> templatesByName = new HashMap<>();
        Map<String, DeviceTemplate> templatesByN4Name = new HashMap<>();
        for (DeviceTemplate template : existingTemplates) {
            templatesByName.put(template.getDeviceTmplName(), template);
            if (template.getN4Name() != null && !template.getN4Name().isBlank()) {
                templatesByN4Name.putIfAbsent(template.getN4Name(), template);
            }
        }

        Map<String, PointTemplate> pointsByTemplateAndName = new HashMap<>();
        for (PointTemplate point : existingPoints) {
            pointsByTemplateAndName.put(pointKey(point.getDeviceTmplId(), point.getPointName()), point);
        }

        List<TemplatePlan> plans = new ArrayList<>();
        Set<String> plannedN4Names = new HashSet<>();
        for (TemplateSheetData sheet : workbook.sheets()) {
            String n4Name = sheet.n4Name();
            if (n4Name == null || n4Name.isBlank()) {
                stats.error("MISSING_N4_NAME", "Workbook sheet has no N4 variable name", sheet.sheetName(), null);
                continue;
            }
            DeviceTemplate existingByName = templatesByName.get(sheet.deviceTemplateName());
            DeviceTemplate existingByN4Name = templatesByN4Name.get(n4Name);
            if (existingByN4Name != null
                    && (existingByName == null
                    || !existingByN4Name.getDeviceTmplId().equals(existingByName.getDeviceTmplId()))) {
                stats.warning(
                        "DUPLICATE_N4_NAME",
                        "Sheet skipped because n4_name is already owned by template '"
                                + existingByN4Name.getDeviceTmplName() + "'",
                        sheet.sheetName(),
                        null);
                stats.skippedRow();
                stats.addTemplatePreview(new TemplatePreviewItem(
                        sheet.sheetName(),
                        sheet.deviceTemplateName(),
                        n4Name,
                        sheet.deviceType(),
                        sheet.manufacturer(),
                        sheet.model(),
                        sheet.points().size(),
                        "SKIPPED"));
                continue;
            }
            if (!plannedN4Names.add(n4Name)) {
                stats.warning(
                        "DUPLICATE_N4_NAME",
                        "Sheet skipped because n4_name was already accepted earlier in this workbook",
                        sheet.sheetName(),
                        null);
                stats.skippedRow();
                stats.addTemplatePreview(new TemplatePreviewItem(
                        sheet.sheetName(),
                        sheet.deviceTemplateName(),
                        n4Name,
                        sheet.deviceType(),
                        sheet.manufacturer(),
                        sheet.model(),
                        sheet.points().size(),
                        "SKIPPED"));
                continue;
            }

            boolean createTemplate = existingByName == null;
            if (createTemplate) {
                stats.deviceTemplateCreated();
            } else {
                stats.deviceTemplateReused();
            }
            int pointCreateCount = 0;
            int pointReuseCount = 0;
            if (existingByName != null) {
                for (TemplatePointData point : sheet.points()) {
                    PointTemplate existingPoint = pointsByTemplateAndName.get(
                            pointKey(existingByName.getDeviceTmplId(), point.pointName()));
                    if (existingPoint == null) {
                        pointCreateCount++;
                    } else {
                        pointReuseCount++;
                    }
                }
            } else {
                pointCreateCount = sheet.points().size();
            }
            for (int index = 0; index < pointCreateCount; index++) {
                stats.pointTemplateCreated();
            }
            for (int index = 0; index < pointReuseCount; index++) {
                stats.pointTemplateReused();
            }
            String action = createTemplate ? "CREATE" : "REUSE";
            stats.addTemplatePreview(new TemplatePreviewItem(
                    sheet.sheetName(),
                    sheet.deviceTemplateName(),
                    n4Name,
                    sheet.deviceType(),
                    sheet.manufacturer(),
                    sheet.model(),
                    sheet.points().size(),
                    action));
            plans.add(new TemplatePlan(sheet, existingByName));
            if (createTemplate) {
                DeviceTemplate planned = buildTemplate(sheet);
                templatesByName.put(planned.getDeviceTmplName(), planned);
                templatesByN4Name.put(n4Name, planned);
            }
        }

        if (!stats.valid() || dryRun) {
            return stats.toSummary();
        }

        for (TemplatePlan plan : plans) {
            TemplateSheetData sheet = plan.sheet();
            DeviceTemplate template = plan.existingTemplate();
            if (template == null) {
                template = buildTemplate(sheet);
                deviceTemplateMapper.insert(template);
            } else if (Boolean.TRUE.equals(template.getIsDeleted())) {
                deviceTemplateMapper.restore(template.getDeviceTmplId());
            }
            for (TemplatePointData pointData : sheet.points()) {
                String pointKey = pointKey(template.getDeviceTmplId(), pointData.pointName());
                PointTemplate existingPoint = pointsByTemplateAndName.get(pointKey);
                if (existingPoint == null) {
                    PointTemplate point = buildPoint(template.getDeviceTmplId(), pointData);
                    pointTemplateMapper.insert(point);
                    pointsByTemplateAndName.put(pointKey, point);
                } else if (Boolean.TRUE.equals(existingPoint.getIsDeleted())) {
                    pointTemplateMapper.restore(existingPoint.getPointTmplId());
                }
            }
        }
        stats.committed();
        return stats.toSummary();
    }

    private DeviceTemplate buildTemplate(TemplateSheetData sheet) {
        DeviceTemplate template = new DeviceTemplate();
        template.setDeviceTmplName(sheet.deviceTemplateName());
        template.setN4Name(sheet.n4Name());
        template.setDeviceType(sheet.deviceType());
        template.setManufacturer(sheet.manufacturer());
        template.setModel(sheet.model());
        template.setPurpose(sheet.purpose());
        template.setIsDeleted(false);
        return template;
    }

    private PointTemplate buildPoint(Long deviceTemplateId, TemplatePointData data) {
        PointTemplate point = new PointTemplate();
        point.setDeviceTmplId(deviceTemplateId);
        point.setPointName(data.pointName());
        point.setType(data.type());
        point.setUnit(data.unit());
        point.setIsDeleted(false);
        return point;
    }

    private String pointKey(Long deviceTemplateId, String pointName) {
        return String.valueOf(deviceTemplateId) + KEY_SEPARATOR + pointName;
    }

    private record TemplatePlan(TemplateSheetData sheet, DeviceTemplate existingTemplate) {
    }
}
