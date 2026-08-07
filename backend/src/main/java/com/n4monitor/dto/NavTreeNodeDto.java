package com.n4monitor.dto;

import lombok.Data;
import java.util.ArrayList;
import java.util.List;

@Data
public class NavTreeNodeDto {
    private Long nodeId;
    private String nodeName;
    private Integer nodeLevel;
    private String icon;
    private Integer sortOrder;
    /** 一级节点允许的最大层级（2/3/...）；仅一级节点有值 */
    private Integer maxLevel;
    /** 叶子分组节点的设备数；非叶子节点为 null */
    private Integer deviceCount;
    /** 叶子分组节点下的真实设备数（非 MOCK_ 前缀）；非叶子节点为 null */
    private Integer realDeviceCount;
    /** 该节点子树内是否含真实设备（叶子直接判断，非叶子由子节点传播） */
    private Boolean hasRealData;
    private List<NavTreeNodeDto> children = new ArrayList<>();
}
