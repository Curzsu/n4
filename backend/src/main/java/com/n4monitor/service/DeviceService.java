package com.n4monitor.service;

import com.n4monitor.dto.DeviceTreeNodeDto;
import com.n4monitor.entity.Device;
import com.n4monitor.mapper.DeviceMapper;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

@Service
public class DeviceService {

    private final DeviceMapper deviceMapper;

    public DeviceService(DeviceMapper deviceMapper) {
        this.deviceMapper = deviceMapper;
    }

    public List<DeviceTreeNodeDto> getDeviceTree() {
        List<Device> rootDevices = deviceMapper.findRootDevices();
        List<DeviceTreeNodeDto> tree = new ArrayList<>();
        for (Device root : rootDevices) {
            tree.add(buildTreeNode(root));
        }
        return tree;
    }

    private DeviceTreeNodeDto buildTreeNode(Device device) {
        DeviceTreeNodeDto node = new DeviceTreeNodeDto();
        node.setId(device.getDeviceId());
        node.setLabel(device.getDeviceName());
        node.setType(resolveNodeType(device.getSysLevel()));
        node.setStatus(device.getStatus());

        List<Device> children = deviceMapper.findBySysParentId(device.getDeviceId());
        if (children != null && !children.isEmpty()) {
            List<DeviceTreeNodeDto> childNodes = new ArrayList<>();
            for (Device child : children) {
                childNodes.add(buildTreeNode(child));
            }
            node.setChildren(childNodes);
        } else {
            node.setChildren(new ArrayList<>());
        }
        return node;
    }

    private String resolveNodeType(Integer sysLevel) {
        if (sysLevel == null || sysLevel == 0) return "site";
        if (sysLevel == 1) return "building";
        if (sysLevel == 2) return "system";
        return "device";
    }
}
