package com.n4monitor.alarm.dto;

import lombok.Data;
import java.util.List;

@Data
public class AlarmSeverityChartDto {
    private int total;
    private List<SeverityItem> items;

    @Data
    public static class SeverityItem {
        private String name;
        private int value;
        private double percentage;
    }
}
