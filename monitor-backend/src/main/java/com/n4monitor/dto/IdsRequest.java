package com.n4monitor.dto;

import lombok.Data;

import java.util.List;

@Data
public class IdsRequest {
    private List<Long> ids;
}
