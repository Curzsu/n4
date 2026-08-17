package com.n4monitor.dashboard.dto;

import lombok.Data;

/**
 * 删除导航节点时的影响预览：级联删除的子节点数 + 解绑的设备数。
 */
@Data
public class DeleteImpactDto {
    /** 将被级联删除的二级子节点数（删一级节点时 >0，删二级节点时为0） */
    private int childCount;
    /** 将被解绑（nav_node_id 置空）的设备数 */
    private int deviceCount;
}
