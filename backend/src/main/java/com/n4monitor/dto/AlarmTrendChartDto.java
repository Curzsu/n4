package com.n4monitor.dto;

import lombok.Data;
import java.util.List;

@Data
public class AlarmTrendChartDto {
    private List<String> timeLabels;
    private List<TrendSeries> series;

    @Data
    public static class TrendSeries {
        private String name;
        private List<Integer> data;
    }
}
