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
    /** 二级节点的设备数；一级节点为 null */
    private Integer deviceCount;
    private List<NavTreeNodeDto> children = new ArrayList<>();
}
