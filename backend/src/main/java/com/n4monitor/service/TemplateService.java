package com.n4monitor.service;

import com.n4monitor.entity.DeviceTemplate;
import com.n4monitor.entity.PointTemplate;
import com.n4monitor.mapper.DeviceTemplateMapper;
import com.n4monitor.mapper.PointTemplateMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Set;

@Service
public class TemplateService {

    /** 远程库 device_template 中真实存在的 14 个模板名（用于标记真实数据 vs Mock 占位） */
    public static final Set<String> REAL_TEMPLATE_NAMES = Set.of(
            "Collector_Honeywell", "DHG_Honeywell", "ZB_Honeywell", "EM_Honeywell",
            "IRC_Honeywell", "DEHU_Honeywell", "DCP_Honeywell", "LD_Honeywell",
            "TH_Honeywell", "GAS_Honeywell", "Battery_Honeywell", "TR_Honeywell",
            "APF_Honeywell", "UPS_Honeywell");

    private final DeviceTemplateMapper deviceTemplateMapper;
    private final PointTemplateMapper pointTemplateMapper;

    public TemplateService(DeviceTemplateMapper deviceTemplateMapper, PointTemplateMapper pointTemplateMapper) {
        this.deviceTemplateMapper = deviceTemplateMapper;
        this.pointTemplateMapper = pointTemplateMapper;
    }

    /** 模板列表：标记真实/Mock，附带点位数量 */
    public List<DeviceTemplate> listTemplates() {
        List<DeviceTemplate> templates = deviceTemplateMapper.findAllWithPointCount();
        for (DeviceTemplate t : templates) {
            t.setIsReal(REAL_TEMPLATE_NAMES.contains(t.getDeviceTmplName()));
        }
        return templates;
    }

    /** 模板点位列表：真实模板的点位标记为真实，Mock 模板的点位标记为 Mock */
    public List<PointTemplate> listPoints(Long deviceTmplId) {
        DeviceTemplate template = requireTemplate(deviceTmplId);
        boolean real = REAL_TEMPLATE_NAMES.contains(template.getDeviceTmplName());
        List<PointTemplate> points = pointTemplateMapper.findByDeviceTmplId(deviceTmplId);
        for (PointTemplate p : points) {
            p.setIsReal(real);
        }
        return points;
    }

    /** 单个模板详情（供编辑回显） */
    public DeviceTemplate getTemplate(Long deviceTmplId) {
        DeviceTemplate template = requireTemplate(deviceTmplId);
        template.setIsReal(REAL_TEMPLATE_NAMES.contains(template.getDeviceTmplName()));
        return template;
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
