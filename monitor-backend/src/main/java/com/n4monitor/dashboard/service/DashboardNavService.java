package com.n4monitor.dashboard.service;

import com.n4monitor.dashboard.dto.DeleteImpactDto;
import com.n4monitor.deployment.dto.DeviceDeployListDto;
import com.n4monitor.dashboard.dto.NavTreeNodeDto;
import com.n4monitor.dashboard.entity.NavNode;
import com.n4monitor.dashboard.mapper.NavNodeMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

@Service
public class DashboardNavService {

    /** 一级节点未配置 max_level 时的默认最大层级（兼容旧的两级导航） */
    private static final int DEFAULT_MAX_LEVEL = 2;

    private final NavNodeMapper navNodeMapper;

    public DashboardNavService(NavNodeMapper navNodeMapper) {
        this.navNodeMapper = navNodeMapper;
    }

    /** 构建任意层级导航树；deviceCount 只填在叶子分组节点（没有子分组的节点）上 */
    public List<NavTreeNodeDto> getNavTree() {
        List<NavNode> all = navNodeMapper.findAll();
        Map<Long, Integer> countMap = new HashMap<>();
        Map<Long, Integer> realCountMap = new HashMap<>();
        for (Map<String, Object> row : navNodeMapper.countDevicesByNode()) {
            Object id = row.get("nodeId");
            if (id != null) {
                long nodeId = ((Number) id).longValue();
                Object cnt = row.get("deviceCount");
                Object real = row.get("realCount");
                countMap.put(nodeId, cnt == null ? 0 : ((Number) cnt).intValue());
                realCountMap.put(nodeId, real == null ? 0 : ((Number) real).intValue());
            }
        }

        Map<Long, NavTreeNodeDto> dtoMap = new HashMap<>();
        for (NavNode n : all) {
            NavTreeNodeDto dto = new NavTreeNodeDto();
            dto.setNodeId(n.getNodeId());
            dto.setNodeName(n.getNodeName());
            dto.setNodeLevel(n.getNodeLevel());
            dto.setIcon(n.getIcon());
            dto.setSortOrder(n.getSortOrder());
            dto.setMaxLevel(n.getMaxLevel());
            dtoMap.put(n.getNodeId(), dto);
        }
        // 组装父子关系（任意层级）
        Set<Long> hasChildren = new HashSet<>();
        for (NavNode n : all) {
            if (n.getParentId() != null && dtoMap.containsKey(n.getParentId())) {
                dtoMap.get(n.getParentId()).getChildren().add(dtoMap.get(n.getNodeId()));
                hasChildren.add(n.getParentId());
            }
        }
        // 二次遍历：子节点继承一级节点的 maxLevel，供前端限制可创建层级
        for (NavNode n : all) {
            NavTreeNodeDto dto = dtoMap.get(n.getNodeId());
            if (dto.getMaxLevel() == null && n.getParentId() != null) {
                NavTreeNodeDto parent = dtoMap.get(n.getParentId());
                if (parent != null && parent.getMaxLevel() != null) {
                    dto.setMaxLevel(parent.getMaxLevel());
                }
            }
        }
        // 叶子分组节点才显示设备数
        for (NavNode n : all) {
            if (!hasChildren.contains(n.getNodeId())) {
                NavTreeNodeDto dto = dtoMap.get(n.getNodeId());
                int real = realCountMap.getOrDefault(n.getNodeId(), 0);
                dto.setDeviceCount(countMap.getOrDefault(n.getNodeId(), 0));
                dto.setRealDeviceCount(real);
                dto.setHasRealData(real > 0);
            }
        }
        List<NavTreeNodeDto> level1 = all.stream()
                .filter(n -> n.getNodeLevel() != null && n.getNodeLevel() == 1)
                .map(n -> dtoMap.get(n.getNodeId()))
                .collect(Collectors.toList());
        // 非叶子节点：hasRealData 由子节点向上传播
        for (NavTreeNodeDto root : level1) {
            propagateHasRealData(root);
        }
        return level1;
    }

    /** 递归计算非叶子节点的 hasRealData（叶子节点已在构建时赋值） */
    private boolean propagateHasRealData(NavTreeNodeDto node) {
        if (node.getHasRealData() != null) {
            return node.getHasRealData();
        }
        boolean any = false;
        for (NavTreeNodeDto child : node.getChildren()) {
            if (propagateHasRealData(child)) {
                any = true;
            }
        }
        node.setHasRealData(any);
        return any;
    }

    /** 查某叶子分组节点下设备列表 */
    public List<DeviceDeployListDto> getDevicesByNode(Long nodeId) {
        List<Map<String, Object>> rows = navNodeMapper.findDevicesByNodeId(nodeId);
        return rows.stream().map(this::toDeviceDto).collect(Collectors.toList());
    }

    /** 按设备部署ID查单个设备 */
    public DeviceDeployListDto getDeviceById(Long deviceDeployId) {
        Map<String, Object> row = navNodeMapper.findDeviceById(deviceDeployId);
        if (row == null) {
            return null;
        }
        return toDeviceDto(row);
    }

    /** 查所有可挂载设备（nav_node_id 为空） */
    public List<DeviceDeployListDto> getMountableDevices() {
        return navNodeMapper.findUnmountedDevices().stream()
                .map(this::toDeviceDto).collect(Collectors.toList());
    }

    // ===== 节点 CRUD =====

    /** 新增节点。parentId 为空则一级，非空则挂在父节点下一级（支持任意层级，受一级节点 max_level 限制） */
    @Transactional
    public NavNode createNode(NavNode node) {
        if (node.getNodeName() == null || node.getNodeName().trim().isEmpty()) {
            throw new IllegalArgumentException("节点名称不能为空");
        }
        if (node.getSortOrder() == null) {
            node.setSortOrder(0);
        }
        if (node.getNodeType() == null || node.getNodeType().isEmpty()) {
            node.setNodeType("group");
        }
        if (node.getParentId() == null) {
            node.setNodeLevel(1);
        } else {
            NavNode parent = navNodeMapper.findByNodeId(node.getParentId());
            if (parent == null || Boolean.TRUE.equals(parent.getIsDeleted())) {
                throw new IllegalArgumentException("父节点不存在");
            }
            node.setNodeLevel(parent.getNodeLevel() + 1);
            int maxLevel = resolveMaxLevel(parent);
            if (node.getNodeLevel() > maxLevel) {
                throw new IllegalArgumentException("该分组最多支持 " + maxLevel + " 级目录");
            }
            // 非一级节点不需要图标
            node.setIcon(null);
        }
        navNodeMapper.insert(node);
        return node;
    }

    /** 编辑节点：更新名称/图标/排序/父级。有子节点的节点不允许改父级 */
    @Transactional
    public NavNode updateNode(Long nodeId, NavNode node) {
        NavNode existing = navNodeMapper.findByNodeId(nodeId);
        if (existing == null || Boolean.TRUE.equals(existing.getIsDeleted())) {
            throw new IllegalArgumentException("节点不存在或已删除");
        }
        if (node.getNodeName() == null || node.getNodeName().trim().isEmpty()) {
            throw new IllegalArgumentException("节点名称不能为空");
        }
        existing.setNodeName(node.getNodeName());
        existing.setIcon(node.getIcon());
        if (node.getSortOrder() != null) {
            existing.setSortOrder(node.getSortOrder());
        }
        // 父级可改：挂到任意层级的节点下（深度受一级节点 max_level 限制）
        if (node.getParentId() != null && !node.getParentId().equals(existing.getParentId())) {
            if (node.getParentId().equals(existing.getNodeId())) {
                throw new IllegalArgumentException("不能把自己挂到自己下面");
            }
            List<NavNode> children = navNodeMapper.findChildren(nodeId);
            if (!children.isEmpty()) {
                throw new IllegalArgumentException("该节点下还有子节点，不能调整父级");
            }
            NavNode newParent = navNodeMapper.findByNodeId(node.getParentId());
            if (newParent == null || Boolean.TRUE.equals(newParent.getIsDeleted())) {
                throw new IllegalArgumentException("目标父节点不存在或已删除");
            }
            int newLevel = newParent.getNodeLevel() + 1;
            if (newLevel > resolveMaxLevel(newParent)) {
                throw new IllegalArgumentException("目标分组最多支持 " + resolveMaxLevel(newParent) + " 级目录");
            }
            existing.setParentId(node.getParentId());
            existing.setNodeLevel(newLevel);
            existing.setIcon(null);
        }
        navNodeMapper.update(existing);
        return existing;
    }

    /** 删除节点：递归级联删所有子孙节点并解绑设备 */
    @Transactional
    public DeleteImpactDto deleteNode(Long nodeId) {
        NavNode existing = requireNode(nodeId);
        DeleteImpactDto impact = new DeleteImpactDto();
        impact.setChildCount(0);
        impact.setDeviceCount(deleteRecursive(existing.getNodeId(), impact));
        return impact;
    }

    /** 删除前预览影响范围（不执行删除）：返回子孙节点总数 + 直接挂载设备总数 */
    public DeleteImpactDto getDeleteImpact(Long nodeId) {
        NavNode existing = requireNode(nodeId);
        DeleteImpactDto impact = new DeleteImpactDto();
        impact.setChildCount(0);
        impact.setDeviceCount(impactRecursive(existing.getNodeId(), impact));
        return impact;
    }

    // ===== 设备挂载/卸载 =====

    /** 批量挂载设备到某叶子分组节点（任意层级，只要没有子分组即可挂载） */
    @Transactional
    public int mountDevices(Long nodeId, List<Long> deviceIds) {
        if (deviceIds == null || deviceIds.isEmpty()) {
            return 0;
        }
        NavNode node = requireNode(nodeId);
        if (!navNodeMapper.findChildren(nodeId).isEmpty()) {
            throw new IllegalArgumentException("设备只能挂载到叶子分组节点（该节点下还有子分组）");
        }
        return navNodeMapper.mountDevices(nodeId, deviceIds);
    }

    /** 卸载单台设备（nav_node_id 置空） */
    @Transactional
    public int unmountDevice(Long deviceDeployId) {
        return navNodeMapper.unmountDevice(deviceDeployId);
    }

    // ===== 内部工具 =====

    /** 递归删除子树并统计直接挂载设备数；impact.childCount 累加子孙节点数 */
    private int deleteRecursive(Long nodeId, DeleteImpactDto impact) {
        List<NavNode> children = navNodeMapper.findChildren(nodeId);
        int devices = 0;
        for (NavNode child : children) {
            devices += deleteRecursive(child.getNodeId(), impact);
            impact.setChildCount(impact.getChildCount() + 1);
        }
        devices += navNodeMapper.countDevicesByNodeId(nodeId);
        navNodeMapper.unmountAllByNode(nodeId);
        navNodeMapper.logicalDelete(nodeId);
        return devices;
    }

    private int impactRecursive(Long nodeId, DeleteImpactDto impact) {
        List<NavNode> children = navNodeMapper.findChildren(nodeId);
        int devices = 0;
        for (NavNode child : children) {
            devices += impactRecursive(child.getNodeId(), impact);
            impact.setChildCount(impact.getChildCount() + 1);
        }
        return devices + navNodeMapper.countDevicesByNodeId(nodeId);
    }

    private NavNode requireNode(Long nodeId) {
        NavNode node = navNodeMapper.findByNodeId(nodeId);
        if (node == null || Boolean.TRUE.equals(node.getIsDeleted())) {
            throw new IllegalArgumentException("节点不存在或已删除");
        }
        return node;
    }

    /** 向上找到一级根节点，取其 max_level；未配置按 2 级处理 */
    private int resolveMaxLevel(NavNode node) {
        NavNode cur = node;
        while (cur != null && cur.getParentId() != null) {
            cur = navNodeMapper.findByNodeId(cur.getParentId());
        }
        if (cur != null && cur.getMaxLevel() != null) {
            return cur.getMaxLevel();
        }
        return DEFAULT_MAX_LEVEL;
    }

    private DeviceDeployListDto toDeviceDto(Map<String, Object> row) {
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
    }

    private static Long toLong(Object v) {
        return v == null ? null : ((Number) v).longValue();
    }

    private static Integer toInt(Object v) {
        return v == null ? null : ((Number) v).intValue();
    }
}
