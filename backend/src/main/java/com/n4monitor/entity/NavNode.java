package com.n4monitor.entity;

import lombok.Data;
import java.time.LocalDateTime;

@Data
public class NavNode {
    private Long nodeId;
    private Long parentId;
    private String nodeName;
    private Integer nodeLevel;
    private String nodeType;
    private String icon;
    private Integer sortOrder;
    private Boolean isDeleted;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
}
