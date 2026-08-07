package com.n4monitor.mapper;

import com.n4monitor.entity.NavNode;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import java.util.List;
import java.util.Map;

@Mapper
public interface NavNodeMapper {
    /** 查全部未删除节点，按 sort_order 排序 */
    List<NavNode> findAll();

    /** 统计各二级节点下的设备数（is_deleted=0），返回 nodeId -> count */
    List<Map<String, Object>> countDevicesByNode();

    /** 查某二级节点下的设备列表（JOIN device_template） */
    List<Map<String, Object>> findDevicesByNodeId(@Param("nodeId") Long nodeId);

    /** 按设备部署ID查单个设备（JOIN device_template） */
    Map<String, Object> findDeviceById(@Param("deviceDeployId") Long deviceDeployId);

    /** 按节点ID查单个节点（含已删除，用于校验存在性） */
    NavNode findByNodeId(@Param("nodeId") Long nodeId);

    /** 查某一级节点下未删除的二级子节点 */
    List<NavNode> findChildren(@Param("parentId") Long parentId);

    /** 新增节点，useGeneratedKeys 回填 nodeId */
    void insert(NavNode node);

    /** 按 nodeId 全字段更新 */
    void update(NavNode node);

    /** 逻辑删除节点：SET is_deleted=1 */
    void logicalDelete(@Param("nodeId") Long nodeId);

    /** 查所有可挂载设备（nav_node_id 为空的未删除设备） */
    List<Map<String, Object>> findUnmountedDevices();

    /** 批量把设备挂到某二级节点 */
    int mountDevices(@Param("nodeId") Long nodeId, @Param("deviceIds") List<Long> deviceIds);

    /** 单台卸载（nav_node_id 置空） */
    int unmountDevice(@Param("deviceDeployId") Long deviceDeployId);

    /** 批量卸载某节点下所有设备（级联删除节点时用） */
    int unmountAllByNode(@Param("nodeId") Long nodeId);

    /** 统计某节点下未删除设备数（删前确认影响） */
    int countDevicesByNodeId(@Param("nodeId") Long nodeId);
}
