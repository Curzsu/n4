package com.n4monitor.dashboard.dto;

import lombok.Data;
import java.util.List;

@Data
public class BatchMoveNavNodesDto {
    private List<Long> nodeIds;
    private Long targetParentId;
}
