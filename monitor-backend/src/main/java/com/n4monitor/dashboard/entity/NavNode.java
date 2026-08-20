package com.n4monitor.dashboard.entity;

import lombok.Data;
import java.time.LocalDateTime;

@Data
public class NavNode {
    private Long nodeId;
    private Long parentId;
    private String nodeName;
    private Integer nodeLevel;
    private String nodeType;
    private Integer maxLevel;
    private String icon;
    private Integer sortOrder;
    private String mqttServerAddresses;
    private Boolean isDeleted;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
}
