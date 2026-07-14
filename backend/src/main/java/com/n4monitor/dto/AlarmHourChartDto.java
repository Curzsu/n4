package com.n4monitor.dto;

import lombok.Data;
import java.util.List;

@Data
public class AlarmHourChartDto {
    private List<String> hours;
    private List<Integer> counts;
}
