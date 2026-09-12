package com.n4monitor.template.service;

import com.n4monitor.template.entity.DeviceTemplate;
import com.n4monitor.template.entity.PointTemplate;
import com.n4monitor.template.mapper.DeviceTemplateMapper;
import com.n4monitor.template.mapper.PointTemplateMapper;
import com.n4monitor.common.UnitUtils;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Arrays;
import java.util.List;
import java.util.Set;
import java.util.regex.Pattern;

@Service
public class TemplateService {

    private static final Pattern IDENTIFIER_PATTERN = Pattern.compile("^[a-zA-Z0-9_\\-\\.\\s()]+$");
    private static final Set<String> VALID_DATA_TYPES = Set.of("numeric", "enum", "boolean", "string");

    /**
     * Data-driven check for real (non-mock) templates: any template whose name
     * does not carry the MOCK_ prefix is real data imported from the remote DB.
     * Kept in sync with the isReal/is_mock expressions in the mapper XMLs.
     */
    public static boolean isRealTemplate(String deviceTmplName) {
        return deviceTmplName != null && !deviceTmplName.toUpperCase().startsWith("MOCK_");
    }

    private final DeviceTemplateMapper deviceTemplateMapper;
    private final PointTemplateMapper pointTemplateMapper;

    public TemplateService(DeviceTemplateMapper deviceTemplateMapper, PointTemplateMapper pointTemplateMapper) {
        this.deviceTemplateMapper = deviceTemplateMapper;
        this.pointTemplateMapper = pointTemplateMapper;
    }

    /** List templates with point counts and real/mock tags */
    public List<DeviceTemplate> listTemplates() {
        return deviceTemplateMapper.findAllWithPointCount();
    }

    /** List points under a template */
    public List<PointTemplate> listPoints(Long deviceTmplId) {
        requireTemplate(deviceTmplId);
        return pointTemplateMapper.findByDeviceTmplId(deviceTmplId);
    }

    /** Get template detail */
    public DeviceTemplate getTemplate(Long deviceTmplId) {
        return requireTemplate(deviceTmplId);
    }

    @Transactional
    public DeviceTemplate createTemplate(DeviceTemplate template) {
        validateTemplateFields(template);
        if (template.getN4Name() == null || template.getN4Name().trim().isEmpty()) {
            template.setN4Name(template.getDeviceTmplName().trim());
        }
        deviceTemplateMapper.insert(template);
        template.setIsReal(false);
        template.setPointCount(0);
        return template;
    }

    @Transactional
    public void updateTemplate(Long deviceTmplId, DeviceTemplate template) {
        DeviceTemplate existing = requireTemplate(deviceTmplId);
        validateTemplateFields(template);
        existing.setDeviceTmplName(template.getDeviceTmplName().trim());
        existing.setDeviceType(template.getDeviceType().trim());
        existing.setModel(template.getModel() == null ? null : template.getModel().trim());
        existing.setManufacturer(template.getManufacturer() == null ? null : template.getManufacturer().trim());
        existing.setManuDate(template.getManuDate());
        deviceTemplateMapper.update(existing);
    }

    @Transactional
    public void deleteTemplate(Long deviceTmplId) {
        requireTemplate(deviceTmplId);
        deviceTemplateMapper.logicalDelete(deviceTmplId);
    }

    @Transactional
    public PointTemplate createPoint(Long deviceTmplId, PointTemplate point) {
        requireTemplate(deviceTmplId);
        validatePointFields(point);
        point.setDeviceTmplId(deviceTmplId);
        point.setUnit(UnitUtils.normalizeUnit(point.getUnit()));

        // Check duplicate point name under this template
        List<PointTemplate> existingPoints = pointTemplateMapper.findByDeviceTmplId(deviceTmplId);
        String newName = point.getPointName().trim().toLowerCase();
        boolean exists = existingPoints.stream().anyMatch(p -> p.getPointName() != null && p.getPointName().trim().toLowerCase().equals(newName));
        if (exists) {
            throw new IllegalArgumentException("Point name already exists under this template: " + point.getPointName());
        }

        pointTemplateMapper.insert(point);
        return point;
    }

    @Transactional
    public void updatePoint(Long pointTmplId, PointTemplate point) {
        PointTemplate existing = pointTemplateMapper.findByPointTmplId(pointTmplId);
        if (existing == null) {
            throw new IllegalArgumentException("Point not found or deleted");
        }
        validatePointFields(point);

        // Check duplicate point name excluding current point
        List<PointTemplate> siblingPoints = pointTemplateMapper.findByDeviceTmplId(existing.getDeviceTmplId());
        String newName = point.getPointName().trim().toLowerCase();
        boolean exists = siblingPoints.stream().anyMatch(p -> !p.getPointTmplId().equals(pointTmplId) && p.getPointName() != null && p.getPointName().trim().toLowerCase().equals(newName));
        if (exists) {
            throw new IllegalArgumentException("Point name already exists under this template: " + point.getPointName());
        }

        existing.setAlarmCode(point.getAlarmCode());
        existing.setPointName(point.getPointName().trim());
        existing.setType(point.getType());
        existing.setUnit(UnitUtils.normalizeUnit(point.getUnit()));
        existing.setDisplayPrecision(point.getDisplayPrecision());
        pointTemplateMapper.update(existing);
    }

    @Transactional
    public void deletePoint(Long pointTmplId) {
        PointTemplate existing = pointTemplateMapper.findByPointTmplId(pointTmplId);
        if (existing == null) {
            throw new IllegalArgumentException("Point not found or deleted");
        }
        pointTemplateMapper.logicalDelete(pointTmplId);
    }

    private void validateTemplateFields(DeviceTemplate template) {
        if (template == null) {
            throw new IllegalArgumentException("Template payload cannot be null");
        }
        String name = template.getDeviceTmplName();
        if (name == null || name.trim().isEmpty()) {
            throw new IllegalArgumentException("Template name cannot be empty");
        }
        String trimmedName = name.trim();
        if (trimmedName.length() < 2 || trimmedName.length() > 100) {
            throw new IllegalArgumentException("Template name must be between 2 and 100 characters");
        }
        if (!IDENTIFIER_PATTERN.matcher(trimmedName).matches()) {
            throw new IllegalArgumentException("Template name contains invalid characters");
        }

        String type = template.getDeviceType();
        if (type == null || type.trim().isEmpty()) {
            throw new IllegalArgumentException("Template type cannot be empty");
        }
        String trimmedType = type.trim();
        if (trimmedType.length() > 50) {
            throw new IllegalArgumentException("Template type cannot exceed 50 characters");
        }

        if (template.getModel() != null && template.getModel().trim().length() > 100) {
            throw new IllegalArgumentException("Model specification cannot exceed 100 characters");
        }
        if (template.getManufacturer() != null && template.getManufacturer().trim().length() > 100) {
            throw new IllegalArgumentException("Manufacturer cannot exceed 100 characters");
        }
    }

    private void validatePointFields(PointTemplate point) {
        if (point == null) {
            throw new IllegalArgumentException("Point payload cannot be null");
        }
        String name = point.getPointName();
        if (name == null || name.trim().isEmpty()) {
            throw new IllegalArgumentException("Point name cannot be empty");
        }
        String trimmedName = name.trim();
        if (trimmedName.length() > 150) {
            throw new IllegalArgumentException("Point name cannot exceed 150 characters");
        }
        if (!IDENTIFIER_PATTERN.matcher(trimmedName).matches()) {
            throw new IllegalArgumentException("Point name contains invalid characters");
        }

        if ((point.getType() == null || point.getType().trim().isEmpty())
                && point.getDataType() != null && !point.getDataType().trim().isEmpty()) {
            point.setType(point.getDataType());
        }
        if (point.getType() == null || point.getType().trim().isEmpty()) {
            point.setType("numeric");
        } else {
            String dt = point.getType().trim().toLowerCase();
            if (!VALID_DATA_TYPES.contains(dt)) {
                throw new IllegalArgumentException("Invalid point type: " + point.getType() + ". Must be one of " + VALID_DATA_TYPES);
            }
            point.setType(dt);
        }

        if (point.getUnit() != null && point.getUnit().trim().length() > 50) {
            throw new IllegalArgumentException("Unit cannot exceed 50 characters");
        }

        if (point.getDisplayPrecision() != null) {
            int prec = point.getDisplayPrecision();
            if (prec < 0 || prec > 4) {
                throw new IllegalArgumentException("Display precision must be between 0 and 4");
            }
        }
    }

    private DeviceTemplate requireTemplate(Long deviceTmplId) {
        if (deviceTmplId == null) {
            throw new IllegalArgumentException("Device template ID cannot be null");
        }
        DeviceTemplate template = deviceTemplateMapper.findByDeviceTmplId(deviceTmplId);
        if (template == null) {
            throw new IllegalArgumentException("Device template not found or deleted");
        }
        return template;
    }
}
