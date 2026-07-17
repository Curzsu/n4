package com.n4monitor.service;

import com.n4monitor.dto.DeviceTreeNodeDto;
import com.n4monitor.entity.Device;
import com.n4monitor.mapper.DeviceMapper;
import org.springframework.stereotype.Service;

import java.util.*;

@Service
public class DeviceService {

    private final DeviceMapper deviceMapper;
    private final com.n4monitor.mapper.MonitorPointMapper monitorPointMapper;

    public DeviceService(DeviceMapper deviceMapper, com.n4monitor.mapper.MonitorPointMapper monitorPointMapper) {
        this.deviceMapper = deviceMapper;
        this.monitorPointMapper = monitorPointMapper;
    }

    public List<DeviceTreeNodeDto> getDeviceTree() {
        List<Device> allDevices = deviceMapper.findAll();
        
        List<DeviceTreeNodeDto> rootNodes = new ArrayList<>();
        Map<String, DeviceTreeNodeDto> pathNodeMap = new HashMap<>();

        for (Device device : allDevices) {
            String sysPath = device.getSysPath();
            if (sysPath == null || sysPath.trim().isEmpty() || sysPath.startsWith("/TEMPLATES")) {
                continue;
            }

            // Split path by '/' and filter out empty segments
            String[] segments = Arrays.stream(sysPath.split("/"))
                                      .filter(s -> !s.trim().isEmpty())
                                      .toArray(String[]::new);

            if (segments.length == 0) {
                continue;
            }

            String currentPath = "";
            DeviceTreeNodeDto parentNode = null;

            for (int i = 0; i < segments.length; i++) {
                String segment = segments[i];
                currentPath = currentPath + "/" + segment;
                boolean isLeaf = (i == segments.length - 1);

                DeviceTreeNodeDto currentNode = pathNodeMap.get(currentPath);
                if (currentNode == null) {
                    currentNode = new DeviceTreeNodeDto();
                    currentNode.setChildren(new ArrayList<>());
                    currentNode.setPath(currentPath);

                    if (isLeaf) {
                        // Leaf node corresponds to the actual device
                        currentNode.setId(device.getDeviceId());
                        currentNode.setDeviceCode(device.getDeviceCode());
                        currentNode.setLabel(device.getDeviceName());
                        currentNode.setType("device");
                        currentNode.setStatus(device.getStatus());
                    } else {
                        // Intermediate node
                        // Generate a unique ID based on path hash to avoid clashes with DB IDs
                        currentNode.setId((long) currentPath.hashCode());
                        currentNode.setLabel(segment);
                        currentNode.setType(resolveNodeTypeByLevel(i));
                        currentNode.setStatus(1); // Default online for grouping nodes
                    }

                    pathNodeMap.put(currentPath, currentNode);

                    if (parentNode == null) {
                        if (!rootNodes.contains(currentNode)) {
                            rootNodes.add(currentNode);
                        }
                    } else {
                        parentNode.getChildren().add(currentNode);
                    }
                } else if (isLeaf) {
                    // Update in case duplicate path mappings occur
                    currentNode.setId(device.getDeviceId());
                    currentNode.setDeviceCode(device.getDeviceCode());
                    currentNode.setLabel(device.getDeviceName());
                    currentNode.setType("device");
                    currentNode.setStatus(device.getStatus());
                    currentNode.setPath(currentPath);
                }

                parentNode = currentNode;
            }
        }

        return rootNodes;
    }

    public List<Device> getAllDevices() {
        return deviceMapper.findAll();
    }

    private String resolveNodeTypeByLevel(int level) {
        if (level == 0) return "system-group";
        if (level == 1) return "system-sub";
        return "system-sub";
    }

    @org.springframework.transaction.annotation.Transactional
    public void createDevice(Device device) {
        if (device.getDeviceName() == null || device.getDeviceName().trim().isEmpty()) {
            throw new IllegalArgumentException("设备名称不能为空");
        }
        if (device.getDeviceCode() == null || device.getDeviceCode().trim().isEmpty()) {
            throw new IllegalArgumentException("设备编码不能为空");
        }
        if (device.getProductId() == null) {
            throw new IllegalArgumentException("必须选择一个设备模板");
        }

        // Set default values
        device.setStatus(1); // Default online
        device.setIsDeleted(false);
        device.setCreateTime(java.time.LocalDateTime.now());
        device.setUpdateTime(java.time.LocalDateTime.now());

        // We can parse sys_parent_id, sys_level etc. from sys_path
        String sysPath = device.getSysPath();
        if (sysPath != null && !sysPath.trim().isEmpty()) {
            String[] segments = Arrays.stream(sysPath.split("/"))
                                      .filter(s -> !s.trim().isEmpty())
                                      .toArray(String[]::new);
            device.setSysLevel(segments.length);
        } else {
            device.setSysLevel(1);
            device.setSysPath("/" + device.getDeviceName());
        }

        // Keep loc_path in sync with sys_path if not specified
        if (device.getLocPath() == null || device.getLocPath().trim().isEmpty()) {
            device.setLocPath(device.getSysPath());
        }
        
        // Always compute loc_level based on loc_path
        if (device.getLocPath() != null && !device.getLocPath().trim().isEmpty()) {
            String[] segments = Arrays.stream(device.getLocPath().split("/"))
                                      .filter(s -> !s.trim().isEmpty())
                                      .toArray(String[]::new);
            device.setLocLevel(segments.length);
        } else {
            device.setLocLevel(device.getSysLevel());
        }

        // Set default sorting orders if null
        if (device.getSysSortOrder() == null) {
            device.setSysSortOrder(1);
        }
        if (device.getLocSortOrder() == null) {
            device.setLocSortOrder(1);
        }

        // 1. Insert device to generate deviceId
        deviceMapper.insert(device);

        // 2. Try to find the template device or another device of the same product to copy points from
        List<Device> allDevices = deviceMapper.findAll();
        Device templateDevice = allDevices.stream()
                                          .filter(d -> d.getProductId().equals(device.getProductId()) && ("TEMPLATE_" + device.getProductId()).equals(d.getDeviceCode()))
                                          .findFirst()
                                          .orElse(null);
        if (templateDevice == null) {
            templateDevice = allDevices.stream()
                                              .filter(d -> d.getProductId().equals(device.getProductId()) && !d.getDeviceId().equals(device.getDeviceId()))
                                              .findFirst()
                                              .orElse(null);
        }

        List<com.n4monitor.entity.MonitorPoint> newPoints = new ArrayList<>();
        if (templateDevice != null) {
            List<com.n4monitor.entity.MonitorPoint> templatePoints = monitorPointMapper.findByDeviceId(templateDevice.getDeviceId());
            for (com.n4monitor.entity.MonitorPoint tp : templatePoints) {
                com.n4monitor.entity.MonitorPoint np = new com.n4monitor.entity.MonitorPoint();
                np.setRegisterId(tp.getRegisterId());
                np.setPointName(tp.getPointName());
                // point_code is unique: format to: [newDeviceId]_[registerId/originalSuffix]
                String suffix = tp.getPointCode();
                if (suffix.contains("_")) {
                    suffix = suffix.substring(suffix.indexOf("_") + 1);
                }
                np.setPointCode(device.getDeviceId() + "_" + suffix);
                np.setPointType(tp.getPointType());
                np.setDataType(tp.getDataType());
                np.setUnit(tp.getUnit());
                np.setDisplayPrecision(tp.getDisplayPrecision());
                np.setDeviceId(device.getDeviceId());
                np.setPointValue("0"); // default value
                np.setPointStatus(1);  // normal
                newPoints.add(np);
            }
        } else {
            // Generate some basic default mock points so the device isn't completely empty
            // e.g. communication status
            com.n4monitor.entity.MonitorPoint commPoint = new com.n4monitor.entity.MonitorPoint();
            commPoint.setRegisterId("191001");
            commPoint.setPointName("通信状态");
            commPoint.setPointCode(device.getDeviceId() + "_191001");
            commPoint.setPointType(1); // DI
            commPoint.setDataType(1);  // int
            commPoint.setUnit("无");
            commPoint.setDisplayPrecision(0);
            commPoint.setDeviceId(device.getDeviceId());
            commPoint.setPointValue("1");
            commPoint.setPointStatus(1);
            newPoints.add(commPoint);
        }

        // Batch insert the generated points
        if (!newPoints.isEmpty()) {
            monitorPointMapper.batchInsert(newPoints);
        }
    }

    @org.springframework.transaction.annotation.Transactional
    public void updateDevice(Device device) {
        if (device.getDeviceId() == null) {
            throw new IllegalArgumentException("设备ID不能为空");
        }
        Device existing = deviceMapper.findByDeviceId(device.getDeviceId());
        if (existing == null) {
            throw new IllegalArgumentException("设备不存在");
        }
        existing.setDeviceName(device.getDeviceName());
        existing.setDeviceCode(device.getDeviceCode());
        existing.setIpAddress(device.getIpAddress());
        existing.setMacAddress(device.getMacAddress());
        existing.setInstallDate(device.getInstallDate());
        existing.setUpdateTime(java.time.LocalDateTime.now());
        
        if (device.getSysPath() != null && !device.getSysPath().trim().isEmpty()) {
            existing.setSysPath(device.getSysPath());
            String[] segments = Arrays.stream(device.getSysPath().split("/"))
                                      .filter(s -> !s.trim().isEmpty())
                                      .toArray(String[]::new);
            existing.setSysLevel(segments.length);
        }
        if (device.getLocPath() != null && !device.getLocPath().trim().isEmpty()) {
            existing.setLocPath(device.getLocPath());
            existing.setLocLevel(existing.getSysLevel());
        }
        deviceMapper.update(existing);
    }

    @org.springframework.transaction.annotation.Transactional
    public void deleteDevice(Long id) {
        if (id == null) {
            throw new IllegalArgumentException("设备ID不能为空");
        }
        deviceMapper.logicalDelete(id);
        monitorPointMapper.deleteByDeviceId(id);
    }

    @org.springframework.transaction.annotation.Transactional
    public void moveDevice(Long deviceId, String targetPath, String newName) {
        if (deviceId == null) {
            throw new IllegalArgumentException("设备ID不能为空");
        }
        Device device = deviceMapper.findByDeviceId(deviceId);
        if (device == null) {
            throw new IllegalArgumentException("设备不存在");
        }
        
        if (targetPath == null || targetPath.trim().isEmpty()) {
            throw new IllegalArgumentException("目标路径不能为空");
        }
        
        String cleanTargetPath = targetPath.trim();
        if (cleanTargetPath.endsWith("/")) {
            cleanTargetPath = cleanTargetPath.substring(0, cleanTargetPath.length() - 1);
        }
        
        String cleanNewName = (newName != null && !newName.trim().isEmpty()) ? newName.trim() : device.getDeviceName();
        
        String newSysPath = cleanTargetPath + "/" + cleanNewName;
        device.setDeviceName(cleanNewName);
        device.setSysPath(newSysPath);
        device.setLocPath(newSysPath);
        
        String[] segments = Arrays.stream(newSysPath.split("/"))
                                  .filter(s -> !s.trim().isEmpty())
                                  .toArray(String[]::new);
        device.setSysLevel(segments.length);
        device.setLocLevel(segments.length);
        device.setUpdateTime(java.time.LocalDateTime.now());
        
        deviceMapper.update(device);
    }
}

