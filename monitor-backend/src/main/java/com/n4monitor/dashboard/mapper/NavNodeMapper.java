package com.n4monitor.dashboard.mapper;

import com.n4monitor.dashboard.entity.NavNode;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import java.util.List;
import java.util.Map;

@Mapper
public interface NavNodeMapper {
    /** Find all active nodes ordered by sort_order. */
    List<NavNode> findAll();

    /** Find all nodes, including logically deleted rows. */
    List<NavNode> findAllIncludingDeleted();

    /** Count active devices for each navigation node. */
    List<Map<String, Object>> countDevicesByNode();

    /** Find devices mounted to a navigation node. */
    List<Map<String, Object>> findDevicesByNodeId(@Param("nodeId") Long nodeId);

    /** Find one deployed device by ID. */
    Map<String, Object> findDeviceById(@Param("deviceDeployId") Long deviceDeployId);

    /** Find one navigation node by ID, including deleted rows. */
    NavNode findByNodeId(@Param("nodeId") Long nodeId);

    /** Find active child nodes for a parent. */
    List<NavNode> findChildren(@Param("parentId") Long parentId);

    /** Insert a node and populate its generated ID. */
    void insert(NavNode node);

    /** Update all editable node fields by ID. */
    void update(NavNode node);

    /** Restore a logically deleted node with its target hierarchy fields. */
    void restore(NavNode node);

    /** Logically delete a node. */
    void logicalDelete(@Param("nodeId") Long nodeId);

    /** Find all active devices that are not mounted. */
    List<Map<String, Object>> findUnmountedDevices();

    /** Mount active unmounted devices to a node. */
    int mountDevices(@Param("nodeId") Long nodeId, @Param("deviceIds") List<Long> deviceIds);

    /** Unmount one device. */
    int unmountDevice(@Param("deviceDeployId") Long deviceDeployId);

    /** Unmount all active devices from a node. */
    int unmountAllByNode(@Param("nodeId") Long nodeId);

    /** Count active devices mounted to a node. */
    int countDevicesByNodeId(@Param("nodeId") Long nodeId);
}
