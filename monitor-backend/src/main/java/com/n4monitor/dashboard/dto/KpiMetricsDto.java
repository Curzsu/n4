package com.n4monitor.dashboard.dto;

import lombok.Data;

@Data
public class KpiMetricsDto {
    private double pue;                // 1.32
    private String pueTrend;           // "+0.03 vs yesterday"
    private double itLoadPercent;      // 68
    private double itLoadKw;           // 1876
    private double itLoadCapacityKw;   // 2780
    private double coolingLoadPercent; // 42
    private double coolingLoadKw;      // 1153
    private double coolingLoadCapacityKw; // 2780
    private double facilityLoadPercent; // 57
    private double facilityLoadKw;     // 1584
    private double facilityLoadCapacityKw; // 2780
    private double totalEnergyKwh;     // 32680
    private double capacityUsagePercent; // 78
    private int activeDevices;         // 1426
    private int totalDevices;          // 1820
}
