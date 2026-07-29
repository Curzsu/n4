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
}
