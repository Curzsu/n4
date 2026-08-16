package com.n4monitor.alarm.dto;

import lombok.Data;
import java.util.List;

@Data
public class AlarmSourceChartDto {
    private List<SourceItem> items;

    @Data
    public static class SourceItem {
        private String name;
        private int count;
    }
}
