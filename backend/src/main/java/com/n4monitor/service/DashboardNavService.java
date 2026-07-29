package com.n4monitor.service;

import com.n4monitor.dto.DeviceDeployListDto;
import com.n4monitor.dto.NavTreeNodeDto;
import com.n4monitor.entity.NavNode;
import com.n4monitor.mapper.NavNodeMapper;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
public class DashboardNavService {

    private final NavNodeMapper navNodeMapper;

    public DashboardNavService(NavNodeMapper navNodeMapper) {
        this.navNodeMapper = navNodeMapper;
    }

    /** 构建两级导航树：一级节点 -> children(二级节点，带 deviceCount) */
    public List<NavTreeNodeDto> getNavTree() {
        List<NavNode> all = navNodeMapper.findAll();
        Map<Long, Integer> countMap = new HashMap<>();
        for (Map<String, Object> row : navNodeMapper.countDevicesByNode()) {
            Object id = row.get("nodeId");
            Object cnt = row.get("deviceCount");
            if (id != null) {
                countMap.put(((Number) id).longValue(), ((Number) cnt).intValue());
            }
        }

        Map<Long, NavTreeNodeDto> dtoMap = new HashMap<>();
        List<NavTreeNodeDto> level1 = new ArrayList<>();
        for (NavNode n : all) {
            NavTreeNodeDto dto = new NavTreeNodeDto();
            dto.setNodeId(n.getNodeId());
            dto.setNodeName(n.getNodeName());
            dto.setNodeLevel(n.getNodeLevel());
            dto.setIcon(n.getIcon());
            dto.setSortOrder(n.getSortOrder());
            if (n.getNodeLevel() == 2) {
                dto.setDeviceCount(countMap.getOrDefault(n.getNodeId(), 0));
            }
            dtoMap.put(n.getNodeId(), dto);
        }
        // 组装父子关系
        for (NavNode n : all) {
            NavTreeNodeDto dto = dtoMap.get(n.getNodeId());
            if (n.getNodeLevel() == 1) {
                level1.add(dto);
            } else if (n.getParentId() != null && dtoMap.containsKey(n.getParentId())) {
                dtoMap.get(n.getParentId()).getChildren().add(dto);
            }
        }
        return level1;
    }

    /** 查某二级节点下设备列表 */
    public List<DeviceDeployListDto> getDevicesByNode(Long nodeId) {
        List<Map<String, Object>> rows = navNodeMapper.findDevicesByNodeId(nodeId);
        return rows.stream().map(row -> {
            DeviceDeployListDto dto = new DeviceDeployListDto();
            dto.setDeviceDeployId(toLong(row.get("deviceDeployId")));
            dto.setDeviceDeployName((String) row.get("deviceDeployName"));
            dto.setDeviceAlias((String) row.get("deviceAlias"));
            dto.setStatus(toInt(row.get("status")));
            dto.setN4Address((String) row.get("n4Address"));
            dto.setDeviceType((String) row.get("deviceType"));
            dto.setManufacturer((String) row.get("manufacturer"));
            dto.setModel((String) row.get("model"));
            Object isMock = row.get("isMock");
            dto.setIsMock(isMock != null && ((Number) isMock).intValue() == 1);
            return dto;
        }).collect(Collectors.toList());
    }

    private static Long toLong(Object v) {
        return v == null ? null : ((Number) v).longValue();
    }

    private static Integer toInt(Object v) {
        return v == null ? null : ((Number) v).intValue();
    }
}
