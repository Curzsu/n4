package com.n4monitor.dashboard.dto;

import lombok.Data;
import java.util.List;

@Data
public class TrendDataDto {
    private List<String> timeLabels;   // ["00:00", "01:00", ..., "23:00"]
    private List<Double> values;       // corresponding data values
    private List<Double> values2;      // optional second series (e.g. total load)
    private String seriesName;         // e.g. "IT Load"
    private String seriesName2;        // e.g. "Total Load"
    private String unit;               // e.g. "kW", "kWh", "°C"
}
