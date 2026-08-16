package com.n4monitor.template.service;

import com.n4monitor.template.entity.DeviceTemplate;
import com.n4monitor.template.entity.PointTemplate;
import com.n4monitor.template.mapper.DeviceTemplateMapper;
import com.n4monitor.template.mapper.PointTemplateMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class TemplateService {

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

    /** 模板列表：真实/Mock 标记由 mapper SQL 计算（isReal），附带点位数量 */
    public List<DeviceTemplate> listTemplates() {
        return deviceTemplateMapper.findAllWithPointCount();
    }

    /** 模板点位列表：真实/Mock 标记由 mapper SQL 计算（跟随所属模板） */
    public List<PointTemplate> listPoints(Long deviceTmplId) {
        requireTemplate(deviceTmplId);
        return pointTemplateMapper.findByDeviceTmplId(deviceTmplId);
    }

    /** 单个模板详情（供编辑回显；isReal 由 mapper SQL 计算） */
    public DeviceTemplate getTemplate(Long deviceTmplId) {
        return requireTemplate(deviceTmplId);
    }

    @Transactional
    public DeviceTemplate createTemplate(DeviceTemplate template) {
        if (template.getDeviceTmplName() == null || template.getDeviceTmplName().trim().isEmpty()) {
            throw new IllegalArgumentException("模板名称不能为空");
        }
        if (template.getDeviceType() == null || template.getDeviceType().trim().isEmpty()) {
            throw new IllegalArgumentException("模板类型不能为空");
        }
        deviceTemplateMapper.insert(template);
        template.setIsReal(false);
        template.setPointCount(0);
        return template;
    }

    @Transactional
    public void updateTemplate(Long deviceTmplId, DeviceTemplate template) {
        DeviceTemplate existing = requireTemplate(deviceTmplId);
        if (template.getDeviceTmplName() == null || template.getDeviceTmplName().trim().isEmpty()) {
            throw new IllegalArgumentException("模板名称不能为空");
        }
        existing.setDeviceTmplName(template.getDeviceTmplName());
        existing.setDeviceType(template.getDeviceType());
        existing.setModel(template.getModel());
        existing.setManufacturer(template.getManufacturer());
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
        if (point.getPointName() == null || point.getPointName().trim().isEmpty()) {
            throw new IllegalArgumentException("点位名称不能为空");
        }
        point.setDeviceTmplId(deviceTmplId);
        if (point.getDataType() == null || point.getDataType().trim().isEmpty()) {
            point.setDataType("numeric");
        }
        pointTemplateMapper.insert(point);
        return point;
    }

    @Transactional
    public void updatePoint(Long pointTmplId, PointTemplate point) {
        PointTemplate existing = pointTemplateMapper.findByPointTmplId(pointTmplId);
        if (existing == null) {
            throw new IllegalArgumentException("点位不存在或已删除");
        }
        if (point.getPointName() == null || point.getPointName().trim().isEmpty()) {
            throw new IllegalArgumentException("点位名称不能为空");
        }
        existing.setPointName(point.getPointName());
        existing.setPointType(point.getPointType());
        existing.setDataType(point.getDataType());
        existing.setUnit(point.getUnit());
        existing.setDisplayPrecision(point.getDisplayPrecision());
        pointTemplateMapper.update(existing);
    }

    @Transactional
    public void deletePoint(Long pointTmplId) {
        PointTemplate existing = pointTemplateMapper.findByPointTmplId(pointTmplId);
        if (existing == null) {
            throw new IllegalArgumentException("点位不存在或已删除");
        }
        pointTemplateMapper.logicalDelete(pointTmplId);
    }

    private DeviceTemplate requireTemplate(Long deviceTmplId) {
        DeviceTemplate template = deviceTemplateMapper.findByDeviceTmplId(deviceTmplId);
        if (template == null) {
            throw new IllegalArgumentException("模板不存在或已删除");
        }
        return template;
    }
}
