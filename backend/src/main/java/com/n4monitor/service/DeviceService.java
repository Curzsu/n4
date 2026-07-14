package com.n4monitor.service;

import com.n4monitor.dto.DeviceTreeNodeDto;
import com.n4monitor.entity.Device;
import com.n4monitor.mapper.DeviceMapper;
import org.springframework.stereotype.Service;

import java.util.*;

@Service
public class DeviceService {

    private final DeviceMapper deviceMapper;

    public DeviceService(DeviceMapper deviceMapper) {
        this.deviceMapper = deviceMapper;
    }

    public List<DeviceTreeNodeDto> getDeviceTree() {
        List<Device> allDevices = deviceMapper.findAll();
        
        List<DeviceTreeNodeDto> rootNodes = new ArrayList<>();
        Map<String, DeviceTreeNodeDto> pathNodeMap = new HashMap<>();

        for (Device device : allDevices) {
            String sysPath = device.getSysPath();
            if (sysPath == null || sysPath.trim().isEmpty()) {
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
                }

                parentNode = currentNode;
            }
        }

        return rootNodes;
    }

    private String resolveNodeTypeByLevel(int level) {
        if (level == 0) return "site";
        if (level == 1) return "building";
        return "system";
    }
}

