package com.n4monitor.dashboard.service;

import com.n4monitor.dashboard.dto.DeleteImpactDto;
import com.n4monitor.deployment.dto.DeviceDeployListDto;
import com.n4monitor.dashboard.dto.NavTreeNodeDto;
import com.n4monitor.dashboard.entity.NavNode;
import com.n4monitor.dashboard.mapper.NavNodeMapper;
import com.n4monitor.deployment.entity.DeviceDeployment;
import com.n4monitor.deployment.entity.PointDeployment;
import com.n4monitor.deployment.mapper.DeviceDeploymentMapper;
import com.n4monitor.deployment.mapper.PointDeploymentMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

@Service
public class DashboardNavService {

    /** Default max level when root node max_level is not configured */
    private static final int DEFAULT_MAX_LEVEL = 2;

    private final NavNodeMapper navNodeMapper;
    private final DeviceDeploymentMapper deviceDeploymentMapper;
    private final PointDeploymentMapper pointDeploymentMapper;

    public DashboardNavService(
            NavNodeMapper navNodeMapper,
            DeviceDeploymentMapper deviceDeploymentMapper,
            PointDeploymentMapper pointDeploymentMapper) {
        this.navNodeMapper = navNodeMapper;
        this.deviceDeploymentMapper = deviceDeploymentMapper;
        this.pointDeploymentMapper = pointDeploymentMapper;
    }

    /** Build multi-level navigation tree; deviceCount populated on leaf group nodes */
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
        // Assemble parent-child relations
        Set<Long> hasChildren = new HashSet<>();
        for (NavNode n : all) {
            if (n.getParentId() != null && dtoMap.containsKey(n.getParentId())) {
                dtoMap.get(n.getParentId()).getChildren().add(dtoMap.get(n.getNodeId()));
                hasChildren.add(n.getParentId());
            }
        }
        // Second pass: child nodes inherit maxLevel from root nodes
        for (NavNode n : all) {
            NavTreeNodeDto dto = dtoMap.get(n.getNodeId());
            if (dto.getMaxLevel() == null && n.getParentId() != null) {
                NavTreeNodeDto parent = dtoMap.get(n.getParentId());
                if (parent != null && parent.getMaxLevel() != null) {
                    dto.setMaxLevel(parent.getMaxLevel());
                }
            }
        }
        // Leaf nodes show device counts
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
        // Non-leaf nodes: propagate hasRealData upwards from children
        for (NavTreeNodeDto root : level1) {
            propagateHasRealData(root);
        }
        return level1;
    }

    /** Recursively calculate hasRealData for non-leaf nodes */
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

    /** Query devices under a leaf node */
    public List<DeviceDeployListDto> getDevicesByNode(Long nodeId) {
        List<Map<String, Object>> rows = navNodeMapper.findDevicesByNodeId(nodeId);
        return rows.stream().map(this::toDeviceDto).collect(Collectors.toList());
    }

    /** Get single device detail by deployment ID */
    public DeviceDeployListDto getDeviceById(Long deviceDeployId) {
        Map<String, Object> row = navNodeMapper.findDeviceById(deviceDeployId);
        if (row == null) {
            return null;
        }
        return toDeviceDto(row);
    }

    /** Query mountable devices (nav_node_id is null) */
    public List<DeviceDeployListDto> getMountableDevices() {
        return navNodeMapper.findUnmountedDevices().stream()
                .map(this::toDeviceDto).collect(Collectors.toList());
    }

    // ===== Node CRUD =====

    /** Create navigation node */
    @Transactional
    public NavNode createNode(NavNode node) {
        validateNavNode(node);
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
                throw new IllegalArgumentException("Parent node not found or deleted");
            }
            node.setNodeLevel(parent.getNodeLevel() + 1);
            int maxLevel = resolveMaxLevel(parent);
            if (node.getNodeLevel() > maxLevel) {
                throw new IllegalArgumentException("This group supports a maximum of " + maxLevel + " directory levels");
            }
            // Non-root nodes do not require an icon
            node.setIcon(null);
        }
        navNodeMapper.insert(node);
        return node;
    }

    /** Edit navigation node */
    @Transactional
    public NavNode updateNode(Long nodeId, NavNode node) {
        NavNode existing = navNodeMapper.findByNodeId(nodeId);
        if (existing == null || Boolean.TRUE.equals(existing.getIsDeleted())) {
            throw new IllegalArgumentException("Node not found or deleted");
        }
        validateNavNode(node);

        existing.setNodeName(node.getNodeName().trim());
        existing.setIcon(node.getIcon());
        if (node.getSortOrder() != null) {
            existing.setSortOrder(node.getSortOrder());
        }
        // Reparenting validation
        if (node.getParentId() != null && !node.getParentId().equals(existing.getParentId())) {
            if (node.getParentId().equals(existing.getNodeId())) {
                throw new IllegalArgumentException("Cannot set a node as its own parent");
            }
            if (isDescendant(nodeId, node.getParentId())) {
                throw new IllegalArgumentException("Cannot set a descendant as parent");
            }
            NavNode newParent = navNodeMapper.findByNodeId(node.getParentId());
            if (newParent == null || Boolean.TRUE.equals(newParent.getIsDeleted())) {
                throw new IllegalArgumentException("Target parent node not found or deleted");
            }
            int newLevel = newParent.getNodeLevel() + 1;
            int subtreeDepth = getSubtreeDepth(nodeId);
            int maxAllowed = resolveMaxLevel(newParent);
            if (newLevel + subtreeDepth > maxAllowed) {
                throw new IllegalArgumentException("Target group supports a maximum of " + maxAllowed + " directory levels");
            }
            int levelDelta = newLevel - existing.getNodeLevel();
            existing.setParentId(node.getParentId());
            existing.setNodeLevel(newLevel);
            existing.setIcon(null);
            if (levelDelta != 0) {
                updateDescendantLevels(nodeId, levelDelta);
            }
        }
        navNodeMapper.update(existing);
        return existing;
    }

    /** Batch move navigation nodes to a target parent node */
    @Transactional
    public int batchMoveNodes(List<Long> nodeIds, Long targetParentId) {
        if (nodeIds == null || nodeIds.isEmpty()) {
            return 0;
        }
        if (targetParentId == null) {
            throw new IllegalArgumentException("Target parent node ID cannot be null");
        }
        // Deduplicate nodeIds while keeping order
        Set<Long> uniqueNodeIds = new LinkedHashSet<>(nodeIds);
        if (uniqueNodeIds.contains(targetParentId)) {
            throw new IllegalArgumentException("Cannot move a node into itself");
        }
        NavNode targetParent = requireNode(targetParentId);

        // Anti-cycle validation: Cannot move a node into its own descendant
        for (Long nodeId : uniqueNodeIds) {
            NavNode node = requireNode(nodeId);
            if (isDescendant(nodeId, targetParentId)) {
                throw new IllegalArgumentException("Cannot move node '" + node.getNodeName() + "' into its own descendant");
            }
            // Cabin (level 2) constraint: Cabins can only be moved under a level 1 root node
            if (node.getNodeLevel() != null && node.getNodeLevel() == 2 && targetParent.getNodeLevel() != 1) {
                throw new IllegalArgumentException("Cabins can only be moved to a building or system root node (level 1)");
            }
            // Collector (level 3) constraint: Collectors cannot be moved directly under a level 1 root node
            if (node.getNodeLevel() != null && node.getNodeLevel() == 3 && targetParent.getNodeLevel() == 1) {
                throw new IllegalArgumentException("Collectors can only be moved to a cabin (level 2)");
            }
        }

        int targetParentLevel = targetParent.getNodeLevel();
        int maxAllowedLevel = resolveMaxLevel(targetParent);

        // Check if target is a leaf device container:
        // A node is a leaf device container if:
        // 1) it currently has mounted devices, OR
        // 2) its level is at or above maxAllowedLevel, has no children, and the source contains mounted devices
        boolean isDeviceContainer = navNodeMapper.countDevicesByNodeId(targetParentId) > 0
                || (targetParentLevel >= maxAllowedLevel
                    && navNodeMapper.findChildren(targetParentId).isEmpty()
                    && hasMountedDevicesInSources(uniqueNodeIds));

        if (isDeviceContainer) {
            return mergeNodesIntoLeafTarget(uniqueNodeIds, targetParent);
        } else {
            return moveNodesAsChildren(uniqueNodeIds, targetParent, targetParentLevel, maxAllowedLevel);
        }
    }

    /** Mode A: Merge source group devices into target leaf node and append to the end */
    private int mergeNodesIntoLeafTarget(Set<Long> sourceNodeIds, NavNode targetParent) {
        Long targetParentId = targetParent.getNodeId();
        String targetSystemName = resolveTargetSystemName(targetParent);
        Long maxTargetDeviceId = deviceDeploymentMapper.findMaxDeviceIdByNavNodeId(targetParentId);
        Long globalMaxDeviceId = deviceDeploymentMapper.findMaxDeviceId();
        long nextDeviceId = globalMaxDeviceId == null ? 0L : globalMaxDeviceId;

        int totalMovedCount = 0;
        for (Long nodeId : sourceNodeIds) {
            NavNode sourceNode = requireNode(nodeId);
            List<Long> nodeSubtreeIds = new ArrayList<>();
            collectSubtreeNodeIds(nodeId, nodeSubtreeIds);

            List<DeviceDeployment> allDevices = new ArrayList<>();
            for (Long subId : nodeSubtreeIds) {
                allDevices.addAll(deviceDeploymentMapper.findByNavNodeId(subId));
            }

            for (DeviceDeployment device : allDevices) {
                Long oldDeviceId = device.getDeviceDeployId();
                if (maxTargetDeviceId != null && oldDeviceId != null && oldDeviceId <= maxTargetDeviceId) {
                    nextDeviceId++;
                    Long newDeviceId = nextDeviceId;
                    pointDeploymentMapper.updateDeviceDeployId(oldDeviceId, newDeviceId);
                    deviceDeploymentMapper.updateDeviceId(oldDeviceId, newDeviceId);
                    device.setDeviceDeployId(newDeviceId);
                } else if (oldDeviceId != null && (maxTargetDeviceId == null || oldDeviceId > maxTargetDeviceId)) {
                    maxTargetDeviceId = oldDeviceId;
                }

                device.setNavNodeId(targetParentId);
                if (targetSystemName != null && !targetSystemName.isEmpty()) {
                    device.setSystemName(targetSystemName);
                }

                updateDeviceAndPointAddresses(device, targetSystemName, targetParent.getNodeName());
                deviceDeploymentMapper.update(device);
            }

            // Logically delete source nodes (descendants first, then root)
            for (int i = nodeSubtreeIds.size() - 1; i >= 0; i--) {
                Long subId = nodeSubtreeIds.get(i);
                navNodeMapper.unmountAllByNode(subId);
                navNodeMapper.logicalDelete(subId);
            }
            totalMovedCount++;
        }
        return totalMovedCount;
    }

    private boolean hasMountedDevicesInSources(Set<Long> nodeIds) {
        for (Long nodeId : nodeIds) {
            List<Long> subtreeIds = new ArrayList<>();
            collectSubtreeNodeIds(nodeId, subtreeIds);
            for (Long subId : subtreeIds) {
                if (navNodeMapper.countDevicesByNodeId(subId) > 0) {
                    return true;
                }
            }
        }
        return false;
    }

    /** Mode B: Traditional directory hierarchy reparenting */
    private int moveNodesAsChildren(
            Set<Long> uniqueNodeIds,
            NavNode targetParent,
            int targetParentLevel,
            int maxAllowedLevel) {
        Long targetParentId = targetParent.getNodeId();
        List<NavNode> existingTargetChildren = navNodeMapper.findChildren(targetParentId);
        Set<String> existingNames = existingTargetChildren.stream()
                .map(NavNode::getNodeName)
                .collect(Collectors.toCollection(HashSet::new));

        // Validation pass
        for (Long nodeId : uniqueNodeIds) {
            NavNode node = requireNode(nodeId);
            if (targetParentId.equals(node.getParentId())) {
                continue; // Already under target parent, no-op
            }
            if (isDescendant(nodeId, targetParentId)) {
                throw new IllegalArgumentException("Cannot move node '" + node.getNodeName() + "' into its own descendant");
            }
            int subtreeDepth = getSubtreeDepth(nodeId);
            int newLevel = targetParentLevel + 1;
            if (newLevel + subtreeDepth > maxAllowedLevel) {
                throw new IllegalArgumentException("Moving node '" + node.getNodeName()
                        + "' would exceed maximum directory levels (" + maxAllowedLevel + ")");
            }
            if (existingNames.contains(node.getNodeName())) {
                throw new IllegalArgumentException("Target group already contains a node named '" + node.getNodeName() + "'");
            }
            existingNames.add(node.getNodeName());
        }

        // Execution pass
        int movedCount = 0;
        for (Long nodeId : uniqueNodeIds) {
            NavNode node = requireNode(nodeId);
            if (targetParentId.equals(node.getParentId())) {
                continue;
            }
            int oldLevel = node.getNodeLevel();
            int newLevel = targetParentLevel + 1;
            int levelDelta = newLevel - oldLevel;

            node.setParentId(targetParentId);
            node.setNodeLevel(newLevel);
            node.setIcon(null);
            navNodeMapper.update(node);

            if (levelDelta != 0) {
                updateDescendantLevels(nodeId, levelDelta);
            }
            // Keep device system_name / N4 addresses in sync with the new hierarchy
            refreshSubtreeDevicePaths(nodeId);
            movedCount++;
        }
        return movedCount;
    }

    private String resolveTargetSystemName(NavNode targetParent) {
        List<DeviceDeployment> existing = deviceDeploymentMapper.findByNavNodeId(targetParent.getNodeId());
        if (!existing.isEmpty() && existing.get(0).getSystemName() != null && !existing.get(0).getSystemName().isBlank()) {
            return existing.get(0).getSystemName();
        }
        return resolveNodeSystemName(targetParent);
    }

    /** Resolve a node's system path from its ancestor chain (last two name segments) */
    private String resolveNodeSystemName(NavNode node) {
        List<String> parts = new ArrayList<>();
        NavNode cur = node;
        while (cur != null) {
            parts.add(0, cur.getNodeName());
            cur = cur.getParentId() == null ? null : navNodeMapper.findByNodeId(cur.getParentId());
        }
        if (parts.size() >= 2) {
            return parts.get(parts.size() - 2) + "/" + parts.get(parts.size() - 1);
        }
        return node.getNodeName();
    }

    private void collectSubtreeNodeIds(Long nodeId, List<Long> result) {
        result.add(nodeId);
        List<NavNode> children = navNodeMapper.findChildren(nodeId);
        for (NavNode child : children) {
            collectSubtreeNodeIds(child.getNodeId(), result);
        }
    }

    private void updateDeviceAndPointAddresses(
            DeviceDeployment device,
            String targetSystemName,
            String targetNodeName) {
        if (targetNodeName == null) {
            return;
        }
        String oldN4 = device.getN4Address();
        if (oldN4 != null) {
            if (oldN4.startsWith("slot:/")) {
                device.setN4Address("slot:/Drivers/ModbusTcpNetwork/" + targetNodeName + "/points/MqttNetwork/" + device.getDeviceDeployName());
            } else if (targetSystemName != null && !targetSystemName.isEmpty()) {
                device.setN4Address(targetSystemName + "/" + device.getDeviceDeployName());
            }
        }
        List<PointDeployment> points = pointDeploymentMapper.findByDeviceDeployId(device.getDeviceDeployId());
        for (PointDeployment point : points) {
            boolean changed = false;
            String pointN4 = point.getN4Address();
            if (pointN4 != null) {
                if (pointN4.startsWith("slot:/")) {
                    point.setN4Address("slot:/Drivers/ModbusTcpNetwork/" + targetNodeName + "/points/MqttNetwork/" + point.getPointName());
                    changed = true;
                } else if (targetSystemName != null && !targetSystemName.isEmpty()) {
                    point.setN4Address(targetSystemName + "/" + device.getDeviceDeployName() + "/" + point.getPointName());
                    changed = true;
                }
            }
            if (changed) {
                pointDeploymentMapper.update(point);
            }
        }
    }

    /** Recompute system_name and N4 addresses for all devices mounted in a moved subtree */
    private void refreshSubtreeDevicePaths(Long rootNodeId) {
        List<Long> subtreeIds = new ArrayList<>();
        collectSubtreeNodeIds(rootNodeId, subtreeIds);
        for (Long nodeId : subtreeIds) {
            NavNode node = navNodeMapper.findByNodeId(nodeId);
            if (node == null) {
                continue;
            }
            String systemName = resolveNodeSystemName(node);
            for (DeviceDeployment device : deviceDeploymentMapper.findByNavNodeId(nodeId)) {
                if (systemName != null && !systemName.isEmpty()) {
                    device.setSystemName(systemName);
                }
                updateDeviceAndPointAddresses(device, systemName, node.getNodeName());
                deviceDeploymentMapper.update(device);
            }
        }
    }

    /** Delete node and cascade unmount devices */
    @Transactional
    public DeleteImpactDto deleteNode(Long nodeId) {
        NavNode existing = requireNode(nodeId);
        DeleteImpactDto impact = new DeleteImpactDto();
        impact.setChildCount(0);
        impact.setDeviceCount(deleteRecursive(existing.getNodeId(), impact));
        return impact;
    }

    /** Preview delete impact */
    public DeleteImpactDto getDeleteImpact(Long nodeId) {
        NavNode existing = requireNode(nodeId);
        DeleteImpactDto impact = new DeleteImpactDto();
        impact.setChildCount(0);
        impact.setDeviceCount(impactRecursive(existing.getNodeId(), impact));
        return impact;
    }

    // ===== Device Mount / Unmount =====

    /** Mount devices to leaf node */
    @Transactional
    public int mountDevices(Long nodeId, List<Long> deviceIds) {
        if (deviceIds == null || deviceIds.isEmpty()) {
            return 0;
        }
        NavNode node = requireNode(nodeId);
        if (!navNodeMapper.findChildren(nodeId).isEmpty()) {
            throw new IllegalArgumentException("Devices can only be mounted to leaf group nodes");
        }
        return navNodeMapper.mountDevices(nodeId, deviceIds);
    }

    /** Unmount device */
    @Transactional
    public int unmountDevice(Long deviceDeployId) {
        return navNodeMapper.unmountDevice(deviceDeployId);
    }

    // ===== Internal Utilities =====

    private void validateNavNode(NavNode node) {
        if (node == null) {
            throw new IllegalArgumentException("Node payload cannot be null");
        }
        String name = node.getNodeName();
        if (name == null || name.trim().isEmpty()) {
            throw new IllegalArgumentException("Node name cannot be empty");
        }
        String trimmed = name.trim();
        if (trimmed.length() > 50) {
            throw new IllegalArgumentException("Node name cannot exceed 50 characters");
        }
        if (trimmed.contains("/") || trimmed.contains("\\") || trimmed.contains("'") || trimmed.contains("\"")) {
            throw new IllegalArgumentException("Node name cannot contain slashes or quotation marks");
        }
        if (node.getSortOrder() != null && (node.getSortOrder() < 0 || node.getSortOrder() > 99999)) {
            throw new IllegalArgumentException("Sort order must be between 0 and 99999");
        }
    }

    /** Recursive delete sub-tree */
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
        if (nodeId == null) {
            throw new IllegalArgumentException("Node ID cannot be null");
        }
        NavNode node = navNodeMapper.findByNodeId(nodeId);
        if (node == null || Boolean.TRUE.equals(node.getIsDeleted())) {
            throw new IllegalArgumentException("Node not found or deleted");
        }
        return node;
    }

    /** Resolve root max level */
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

    private boolean isDescendant(Long ancestorId, Long targetId) {
        List<NavNode> children = navNodeMapper.findChildren(ancestorId);
        for (NavNode child : children) {
            if (child.getNodeId().equals(targetId)) {
                return true;
            }
            if (isDescendant(child.getNodeId(), targetId)) {
                return true;
            }
        }
        return false;
    }

    private int getSubtreeDepth(Long nodeId) {
        List<NavNode> children = navNodeMapper.findChildren(nodeId);
        if (children.isEmpty()) {
            return 0;
        }
        int maxChildDepth = 0;
        for (NavNode child : children) {
            maxChildDepth = Math.max(maxChildDepth, getSubtreeDepth(child.getNodeId()));
        }
        return 1 + maxChildDepth;
    }

    private void updateDescendantLevels(Long parentId, int levelDelta) {
        List<NavNode> children = navNodeMapper.findChildren(parentId);
        for (NavNode child : children) {
            child.setNodeLevel(child.getNodeLevel() + levelDelta);
            navNodeMapper.update(child);
            updateDescendantLevels(child.getNodeId(), levelDelta);
        }
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
