package com.n4monitor.deployment.service;

import com.n4monitor.template.service.TemplateService;
import com.n4monitor.deployment.entity.DeviceDeployment;
import com.n4monitor.template.entity.DeviceTemplate;
import com.n4monitor.dashboard.entity.NavNode;
import com.n4monitor.deployment.entity.PointDeployment;
import com.n4monitor.template.entity.PointTemplate;
import com.n4monitor.deployment.mapper.DeviceDeploymentMapper;
import com.n4monitor.template.mapper.DeviceTemplateMapper;
import com.n4monitor.dashboard.mapper.NavNodeMapper;
import com.n4monitor.deployment.mapper.PointDeploymentMapper;
import com.n4monitor.template.mapper.PointTemplateMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;

@Service
@RequiredArgsConstructor
public class DeploymentService {

    private static final String DEVICE_ALARM_CODE = "Communication_status_00";

    private final DeviceDeploymentMapper deviceDeploymentMapper;
    private final PointDeploymentMapper pointDeploymentMapper;
    private final DeviceTemplateMapper deviceTemplateMapper;
    private final PointTemplateMapper pointTemplateMapper;
    private final NavNodeMapper navNodeMapper;

    /** 全部设备部署列表 */
    public List<DeviceDeployment> listAll() {
        return deviceDeploymentMapper.findAll();
    }

    /** 某导航节点下的设备部署列表 */
    public List<DeviceDeployment> listByNode(Long navNodeId) {
        return deviceDeploymentMapper.findByNavNodeId(navNodeId);
    }

    /** 单个设备部署详情 */
    public DeviceDeployment getDevice(Long deviceDeployId) {
        DeviceDeployment deployment = requireDevice(deviceDeployId);
        return deployment;
    }

    /** 设备部署点位列表（isReal 按模板名是否为真实模板判断） */
    public List<PointDeployment> listPoints(Long deviceDeployId) {
        DeviceDeployment deployment = requireDevice(deviceDeployId);
        DeviceTemplate template = deviceTemplateMapper.findByDeviceTmplId(deployment.getDeviceTmplId());
        boolean real = template != null
                && TemplateService.isRealTemplate(template.getDeviceTmplName());
        List<PointDeployment> points = pointDeploymentMapper.findByDeviceDeployId(deviceDeployId);
        for (PointDeployment p : points) {
            p.setIsReal(real);
        }
        return points;
    }

    /** 部署新设备：插入设备记录并从模板复制生成全部点位 */
    @Transactional
    public DeviceDeployment createDeployment(DeviceDeployment deployment) {
        if (deployment.getDeviceTmplId() == null) {
            throw new IllegalArgumentException("必须选择设备模板");
        }
        if (deployment.getNavNodeId() == null) {
            throw new IllegalArgumentException("必须选择部署节点");
        }
        if (deployment.getDeviceDeployName() == null || deployment.getDeviceDeployName().trim().isEmpty()) {
            throw new IllegalArgumentException("设备部署名称不能为空");
        }
        DeviceTemplate template = requireTemplate(deployment.getDeviceTmplId());
        NavNode node = requireNode(deployment.getNavNodeId());

        deployment.setDeviceDeployName(deployment.getDeviceDeployName().trim());
        if (deployment.getDeviceAlias() == null || deployment.getDeviceAlias().trim().isEmpty()) {
            deployment.setDeviceAlias(deployment.getDeviceDeployName());
        }
        if (deployment.getAlarmCode() == null || deployment.getAlarmCode().trim().isEmpty()) {
            deployment.setAlarmCode(DEVICE_ALARM_CODE);
        }
        if (deployment.getStatus() == null) {
            deployment.setStatus(1);
        }
        deployment.setN4Address(buildDeviceN4Address(node.getNodeName(), deployment.getDeviceDeployName()));

        try {
            deviceDeploymentMapper.insert(deployment);
        } catch (org.springframework.dao.DuplicateKeyException e) {
            throw new IllegalArgumentException("设备部署名已存在：" + deployment.getDeviceDeployName());
        }

        // 从模板复制点位
        List<PointTemplate> templatePoints = pointTemplateMapper.findByDeviceTmplId(deployment.getDeviceTmplId());
        List<PointDeployment> newPoints = new ArrayList<>();
        String prefix = template.getDeviceTmplName() + "_";
        for (PointTemplate tp : templatePoints) {
            PointDeployment p = new PointDeployment();
            p.setPointTmplId(tp.getPointTmplId());
            p.setDeviceDeployId(deployment.getDeviceDeployId());
            p.setAlarmCode(tp.getAlarmCode());
            String suffix = stripPrefix(tp.getPointName(), prefix);
            String pointName = deployment.getDeviceDeployName() + "_" + suffix;
            p.setPointName(pointName);
            p.setN4Address(buildPointN4Address(node.getNodeName(), pointName));
            p.setPointType(tp.getPointType());
            p.setDataType(tp.getDataType());
            p.setUnit(tp.getUnit());
            p.setDisplayPrecision(tp.getDisplayPrecision());
            newPoints.add(p);
        }
        if (!newPoints.isEmpty()) {
            pointDeploymentMapper.insertBatch(newPoints);
        }
        return deployment;
    }

    /** 更新设备部署信息（设备名变化时同步 n4_address 与点位前缀） */
    @Transactional
    public void updateDevice(Long deviceDeployId, DeviceDeployment deployment) {
        DeviceDeployment existing = requireDevice(deviceDeployId);
        if (deployment.getDeviceDeployName() == null || deployment.getDeviceDeployName().trim().isEmpty()) {
            throw new IllegalArgumentException("设备部署名称不能为空");
        }
        if (deployment.getDeviceTmplId() == null) {
            throw new IllegalArgumentException("必须选择设备模板");
        }
        requireTemplate(deployment.getDeviceTmplId());
        NavNode node = requireNode(deployment.getNavNodeId());

        String newName = deployment.getDeviceDeployName().trim();
        boolean nameChanged = !newName.equals(existing.getDeviceDeployName());
        deployment.setDeviceDeployId(deviceDeployId);
        deployment.setDeviceDeployName(newName);
        if (deployment.getDeviceAlias() == null || deployment.getDeviceAlias().trim().isEmpty()) {
            deployment.setDeviceAlias(newName);
        }
        if (deployment.getAlarmCode() == null || deployment.getAlarmCode().trim().isEmpty()) {
            deployment.setAlarmCode(DEVICE_ALARM_CODE);
        }
        deployment.setN4Address(buildDeviceN4Address(node.getNodeName(), newName));

        if (nameChanged) {
            String oldPrefix = existing.getDeviceDeployName() + "_";
            String newPrefix = newName + "_";
            pointDeploymentMapper.updateNamePrefix(deviceDeployId, oldPrefix, newPrefix, node.getNodeName());
        }
        deviceDeploymentMapper.update(deployment);
    }

    /** 移动设备到其他节点（可选重命名，点位前缀与 n4_address 同步） */
    @Transactional
    public void moveDevice(Long deviceDeployId, Long targetNodeId, String newName) {
        DeviceDeployment existing = requireDevice(deviceDeployId);
        if (targetNodeId == null) {
            throw new IllegalArgumentException("必须选择目标节点");
        }
        NavNode node = requireNode(targetNodeId);
        String finalName = newName == null || newName.trim().isEmpty()
                ? existing.getDeviceDeployName() : newName.trim();

        String oldPrefix = existing.getDeviceDeployName() + "_";
        String newPrefix = finalName + "_";
        if (!oldPrefix.equals(newPrefix)) {
            pointDeploymentMapper.updateNamePrefix(deviceDeployId, oldPrefix, newPrefix, node.getNodeName());
        }
        deviceDeploymentMapper.updateNode(deviceDeployId, targetNodeId, finalName, finalName,
                buildDeviceN4Address(node.getNodeName(), finalName));
    }

    /** 更新部署点位（寄存器地址等） */
    @Transactional
    public void updatePoint(Long pointDeployId, PointDeployment point) {
        PointDeployment existing = pointDeploymentMapper.findByPointDeployId(pointDeployId);
        if (existing == null) {
            throw new IllegalArgumentException("点位不存在或已删除");
        }
        if (point.getPointName() == null || point.getPointName().trim().isEmpty()) {
            throw new IllegalArgumentException("点位名称不能为空");
        }
        if (point.getDataType() == null || point.getDataType().trim().isEmpty()) {
            throw new IllegalArgumentException("点位数据类型不能为空");
        }
        existing.setAlarmCode(point.getAlarmCode());
        existing.setPointName(point.getPointName());
        existing.setN4Address(point.getN4Address());
        existing.setPointCode(point.getPointCode());
        existing.setRegister(point.getRegister());
        existing.setPointType(point.getPointType());
        existing.setDataType(point.getDataType());
        existing.setUnit(point.getUnit());
        existing.setDisplayPrecision(point.getDisplayPrecision());
        pointDeploymentMapper.update(existing);
    }

    /** 删除设备部署（物理删除点位 + 逻辑删除设备） */
    @Transactional
    public void deleteDeployment(Long deviceDeployId) {
        requireDevice(deviceDeployId);
        pointDeploymentMapper.deleteByDeviceDeployId(deviceDeployId);
        deviceDeploymentMapper.logicalDelete(deviceDeployId);
    }

    private DeviceDeployment requireDevice(Long deviceDeployId) {
        DeviceDeployment deployment = deviceDeploymentMapper.findByDeviceDeployId(deviceDeployId);
        if (deployment == null) {
            throw new IllegalArgumentException("设备部署不存在或已删除");
        }
        return deployment;
    }

    private DeviceTemplate requireTemplate(Long deviceTmplId) {
        DeviceTemplate template = deviceTemplateMapper.findByDeviceTmplId(deviceTmplId);
        if (template == null) {
            throw new IllegalArgumentException("设备模板不存在或已删除");
        }
        return template;
    }

    private NavNode requireNode(Long navNodeId) {
        NavNode node = navNodeMapper.findByNodeId(navNodeId);
        if (node == null || Boolean.TRUE.equals(node.getIsDeleted())) {
            throw new IllegalArgumentException("部署节点不存在或已删除");
        }
        return node;
    }

    private String stripPrefix(String pointName, String prefix) {
        if (pointName.startsWith(prefix)) {
            return pointName.substring(prefix.length());
        }
        return pointName;
    }

    private String buildDeviceN4Address(String nodeName, String deviceName) {
        return "slot:/Drivers/ModbusTcpNetwork/" + nodeName + "/points/MqttNetwork/" + deviceName;
    }

    private String buildPointN4Address(String nodeName, String pointName) {
        return "slot:/Drivers/ModbusTcpNetwork/" + nodeName + "/points/MqttNetwork/" + pointName;
    }
}
