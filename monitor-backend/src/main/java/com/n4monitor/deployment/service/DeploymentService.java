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
import java.util.Set;
import java.util.regex.Pattern;

@Service
@RequiredArgsConstructor
public class DeploymentService {

    private static final String DEVICE_ALARM_CODE = "Communication_status_00";
    private static final Pattern IDENTIFIER_PATTERN = Pattern.compile("^[a-zA-Z0-9_\\-\\.\\s()]+$");
    private static final Pattern IPV4_PATTERN = Pattern.compile("^(?:(?:25[0-5]|2[0-4]\\d|1\\d\\d|[1-9]?\\d)\\.){3}(?:25[0-5]|2[0-4]\\d|1\\d\\d|[1-9]?\\d)$");
    private static final Pattern IPV6_PATTERN = Pattern.compile("^([0-9a-fA-F]{1,4}:){7}[0-9a-fA-F]{1,4}$");
    private static final Pattern MAC_PATTERN = Pattern.compile("^([0-9A-Fa-f]{2}[:-]){5}([0-9A-Fa-f]{2})$");
    private static final Set<String> VALID_POINT_TYPES = Set.of("DI", "DO", "AI", "AO");
    private static final Set<String> VALID_DATA_TYPES = Set.of("numeric", "enum", "boolean", "string");

    private final DeviceDeploymentMapper deviceDeploymentMapper;
    private final PointDeploymentMapper pointDeploymentMapper;
    private final DeviceTemplateMapper deviceTemplateMapper;
    private final PointTemplateMapper pointTemplateMapper;
    private final NavNodeMapper navNodeMapper;

    /** List all deployed devices */
    public List<DeviceDeployment> listAll() {
        return deviceDeploymentMapper.findAll();
    }

    /** List deployed devices under a navigation node */
    public List<DeviceDeployment> listByNode(Long navNodeId) {
        return deviceDeploymentMapper.findByNavNodeId(navNodeId);
    }

    /** Get deployed device details */
    public DeviceDeployment getDevice(Long deviceDeployId) {
        return requireDevice(deviceDeployId);
    }

    /** List points for a deployed device */
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

    /** Deploy new device and generate points from template */
    @Transactional
    public DeviceDeployment createDeployment(DeviceDeployment deployment) {
        if (deployment.getDeviceTmplId() == null) {
            throw new IllegalArgumentException("Device template must be selected");
        }
        if (deployment.getNavNodeId() == null) {
            throw new IllegalArgumentException("Target deployment node must be selected");
        }
        validateDeploymentFields(deployment);

        DeviceTemplate template = requireTemplate(deployment.getDeviceTmplId());
        NavNode node = requireNode(deployment.getNavNodeId());

        deployment.setDeviceDeployName(deployment.getDeviceDeployName().trim());
        if (deployment.getDeviceAlias() == null || deployment.getDeviceAlias().trim().isEmpty()) {
            deployment.setDeviceAlias(deployment.getDeviceDeployName());
        } else {
            deployment.setDeviceAlias(deployment.getDeviceAlias().trim());
        }
        if (deployment.getAlarmCode() == null || deployment.getAlarmCode().trim().isEmpty()) {
            deployment.setAlarmCode(DEVICE_ALARM_CODE);
        }
        if (deployment.getStatus() == null) {
            deployment.setStatus(1);
        }
        deployment.setSystemName(node.getNodeName());
        deployment.setN4Address(buildDeviceN4Address(node.getNodeName(), deployment.getDeviceDeployName()));

        try {
            deviceDeploymentMapper.insert(deployment);
        } catch (org.springframework.dao.DuplicateKeyException e) {
            throw new IllegalArgumentException("Device deployment name already exists: " + deployment.getDeviceDeployName());
        }

        // Copy points from template
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
            String n4Addr = buildPointN4Address(node.getNodeName(), pointName);
            p.setN4Address(n4Addr);
            p.setAllPath(n4Addr);
            p.setPointType(tp.getPointType());
            p.setDataType(tp.getDataType());
            p.setType(tp.getDataType() != null ? tp.getDataType() : "numeric");
            p.setUnit(tp.getUnit());
            p.setDisplayPrecision(tp.getDisplayPrecision());
            newPoints.add(p);
        }
        if (!newPoints.isEmpty()) {
            pointDeploymentMapper.insertBatch(newPoints);
        }
        return deployment;
    }

    /** Update deployed device info */
    @Transactional
    public void updateDevice(Long deviceDeployId, DeviceDeployment deployment) {
        DeviceDeployment existing = requireDevice(deviceDeployId);
        if (deployment.getDeviceTmplId() == null) {
            deployment.setDeviceTmplId(existing.getDeviceTmplId());
        }
        if (deployment.getNavNodeId() == null) {
            deployment.setNavNodeId(existing.getNavNodeId());
        }
        validateDeploymentFields(deployment);

        requireTemplate(deployment.getDeviceTmplId());
        NavNode node = requireNode(deployment.getNavNodeId());

        String newName = deployment.getDeviceDeployName().trim();
        boolean nameChanged = !newName.equals(existing.getDeviceDeployName());
        deployment.setDeviceDeployId(deviceDeployId);
        deployment.setDeviceDeployName(newName);
        if (deployment.getDeviceAlias() == null || deployment.getDeviceAlias().trim().isEmpty()) {
            deployment.setDeviceAlias(newName);
        } else {
            deployment.setDeviceAlias(deployment.getDeviceAlias().trim());
        }
        if (deployment.getAlarmCode() == null || deployment.getAlarmCode().trim().isEmpty()) {
            deployment.setAlarmCode(DEVICE_ALARM_CODE);
        }
        deployment.setSystemName(node.getNodeName());
        deployment.setN4Address(buildDeviceN4Address(node.getNodeName(), newName));

        if (nameChanged) {
            String oldPrefix = existing.getDeviceDeployName() + "_";
            String newPrefix = newName + "_";
            pointDeploymentMapper.updateNamePrefix(deviceDeployId, oldPrefix, newPrefix, node.getNodeName());
        }
        deviceDeploymentMapper.update(deployment);
    }

    /** Move device to another node */
    @Transactional
    public void moveDevice(Long deviceDeployId, Long targetNodeId, String newName) {
        DeviceDeployment existing = requireDevice(deviceDeployId);
        if (targetNodeId == null) {
            throw new IllegalArgumentException("Target node must be selected");
        }
        NavNode node = requireNode(targetNodeId);
        String finalName = newName == null || newName.trim().isEmpty()
                ? existing.getDeviceDeployName() : newName.trim();

        if (finalName.length() < 2 || finalName.length() > 100 || !IDENTIFIER_PATTERN.matcher(finalName).matches()) {
            throw new IllegalArgumentException("Device name must be 2-100 characters containing valid characters");
        }

        String oldPrefix = existing.getDeviceDeployName() + "_";
        String newPrefix = finalName + "_";
        if (!oldPrefix.equals(newPrefix)) {
            pointDeploymentMapper.updateNamePrefix(deviceDeployId, oldPrefix, newPrefix, node.getNodeName());
        }
        deviceDeploymentMapper.updateNode(deviceDeployId, targetNodeId, finalName, finalName,
                buildDeviceN4Address(node.getNodeName(), finalName));
    }

    /** Update point deployment */
    @Transactional
    public void updatePoint(Long pointDeployId, PointDeployment point) {
        PointDeployment existing = pointDeploymentMapper.findByPointDeployId(pointDeployId);
        if (existing == null) {
            throw new IllegalArgumentException("Point not found or deleted");
        }
        validatePointDeploymentFields(point);

        existing.setAlarmCode(point.getAlarmCode());
        existing.setPointName(point.getPointName().trim());
        existing.setN4Address(point.getN4Address());
        existing.setPointCode(point.getPointCode());
        existing.setRegister(point.getRegister());
        existing.setPointType(point.getPointType());
        existing.setDataType(point.getDataType());
        existing.setUnit(point.getUnit() == null ? null : point.getUnit().trim());
        existing.setDisplayPrecision(point.getDisplayPrecision());
        pointDeploymentMapper.update(existing);
    }

    /** Delete device deployment (deletes points and logically deletes device) */
    @Transactional
    public void deleteDeployment(Long deviceDeployId) {
        requireDevice(deviceDeployId);
        pointDeploymentMapper.deleteByDeviceDeployId(deviceDeployId);
        deviceDeploymentMapper.logicalDelete(deviceDeployId);
    }

    private void validateDeploymentFields(DeviceDeployment deployment) {
        if (deployment == null) {
            throw new IllegalArgumentException("Deployment payload cannot be null");
        }
        String name = deployment.getDeviceDeployName();
        if (name == null || name.trim().isEmpty()) {
            throw new IllegalArgumentException("Device deployment name cannot be empty");
        }
        String trimmedName = name.trim();
        if (trimmedName.length() < 2 || trimmedName.length() > 100) {
            throw new IllegalArgumentException("Device deployment name must be between 2 and 100 characters");
        }
        if (!IDENTIFIER_PATTERN.matcher(trimmedName).matches()) {
            throw new IllegalArgumentException("Device deployment name contains invalid characters");
        }

        if (deployment.getDeviceAlias() != null && deployment.getDeviceAlias().trim().length() > 100) {
            throw new IllegalArgumentException("Device alias cannot exceed 100 characters");
        }

        if (deployment.getIpAddress() != null && !deployment.getIpAddress().trim().isEmpty()) {
            String ip = deployment.getIpAddress().trim();
            if (ip.length() > 45 || (!IPV4_PATTERN.matcher(ip).matches() && !IPV6_PATTERN.matcher(ip).matches())) {
                throw new IllegalArgumentException("Invalid IP address format: " + ip);
            }
            deployment.setIpAddress(ip);
        }

        if (deployment.getMacAddress() != null && !deployment.getMacAddress().trim().isEmpty()) {
            String mac = deployment.getMacAddress().trim();
            if (mac.length() > 17 || !MAC_PATTERN.matcher(mac).matches()) {
                throw new IllegalArgumentException("Invalid MAC address format: " + mac);
            }
            deployment.setMacAddress(mac);
        }

        if (deployment.getLocation() != null && deployment.getLocation().trim().length() > 50) {
            throw new IllegalArgumentException("Location cannot exceed 50 characters");
        }
        if (deployment.getDepartment() != null && deployment.getDepartment().trim().length() > 100) {
            throw new IllegalArgumentException("Department cannot exceed 100 characters");
        }
        if (deployment.getResponsiblePerson() != null && deployment.getResponsiblePerson().trim().length() > 50) {
            throw new IllegalArgumentException("Responsible person cannot exceed 50 characters");
        }
        if (deployment.getContactPhone() != null && deployment.getContactPhone().trim().length() > 20) {
            throw new IllegalArgumentException("Contact phone cannot exceed 20 characters");
        }
    }

    private void validatePointDeploymentFields(PointDeployment point) {
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

        if (point.getPointType() != null && !point.getPointType().trim().isEmpty()) {
            String pt = point.getPointType().trim().toUpperCase();
            if (!VALID_POINT_TYPES.contains(pt)) {
                throw new IllegalArgumentException("Invalid point type: " + point.getPointType());
            }
            point.setPointType(pt);
        }

        if (point.getDataType() == null || point.getDataType().trim().isEmpty()) {
            throw new IllegalArgumentException("Point data type cannot be empty");
        } else {
            String dt = point.getDataType().trim().toLowerCase();
            if (!VALID_DATA_TYPES.contains(dt)) {
                throw new IllegalArgumentException("Invalid data type: " + point.getDataType());
            }
            point.setDataType(dt);
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

    private DeviceDeployment requireDevice(Long deviceDeployId) {
        if (deviceDeployId == null) {
            throw new IllegalArgumentException("Device deployment ID cannot be null");
        }
        DeviceDeployment deployment = deviceDeploymentMapper.findByDeviceDeployId(deviceDeployId);
        if (deployment == null) {
            throw new IllegalArgumentException("Device deployment not found or deleted");
        }
        return deployment;
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

    private NavNode requireNode(Long navNodeId) {
        if (navNodeId == null) {
            throw new IllegalArgumentException("Deployment node ID cannot be null");
        }
        NavNode node = navNodeMapper.findByNodeId(navNodeId);
        if (node == null || Boolean.TRUE.equals(node.getIsDeleted())) {
            throw new IllegalArgumentException("Deployment node not found or deleted");
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
