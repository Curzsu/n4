package com.n4monitor.dto;

import lombok.Data;
import java.util.List;

@Data
public class AlarmSystemChartDto {
    private List<SystemItem> items;

    @Data
    public static class SystemItem {
        private String name;
        private int value;
        private double percentage;
    }
}
