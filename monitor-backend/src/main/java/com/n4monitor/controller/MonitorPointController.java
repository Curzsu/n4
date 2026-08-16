package com.n4monitor.controller;

import com.n4monitor.dto.ApiResponse;
import com.n4monitor.entity.MonitorPoint;
import com.n4monitor.mapper.MonitorPointMapper;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Random;

@RestController
@RequestMapping("/api/devices")
public class MonitorPointController {

    private final MonitorPointMapper monitorPointMapper;
    private final Random random = new Random();

    public MonitorPointController(MonitorPointMapper monitorPointMapper) {
        this.monitorPointMapper = monitorPointMapper;
    }

    @GetMapping("/{deviceId}/points")
    public ApiResponse<List<MonitorPoint>> getPointsByDeviceId(@PathVariable("deviceId") Long deviceId) {
        List<MonitorPoint> points = monitorPointMapper.findByDeviceId(deviceId);
        
        for (MonitorPoint p : points) {
            // Randomize point status: 92% normal (1), 8% alarm (0)
            p.setPointStatus(random.nextDouble() < 0.92 ? 1 : 0);
            
            // Randomize point value according to type and unit
            if (p.getPointType() == null) {
                p.setPointValue("0");
            } else if (p.getPointType() == 1 || p.getPointType() == 2) {
                // DI/DO: 0 or 1
                p.setPointValue(String.valueOf(random.nextInt(2)));
            } else {
                // AI/AO: Analog floating or integer values
                double base = 0.0;
                double range = 100.0;
                String unit = p.getUnit();
                
                if ("℃".equals(unit)) {
                    base = 18.0;
                    range = 25.0; // 18.0 to 43.0 °C
                } else if ("%".equals(unit)) {
                    base = 25.0;
                    range = 65.0; // 25% to 90%
                } else if ("V".equals(unit)) {
                    base = 210.0;
                    range = 20.0; // 210V to 230V
                } else if ("A".equals(unit)) {
                    base = 2.0;
                    range = 48.0; // 2A to 50A
                } else if ("kW".equals(unit)) {
                    base = 15.0;
                    range = 120.0; // 15kW to 135kW
                } else if ("kWh".equals(unit)) {
                    base = 5000.0;
                    range = 15000.0; // 5000 to 20000 kWh
                } else if ("Hz".equals(unit)) {
                    base = 49.5;
                    range = 1.0; // 49.5 to 50.5 Hz
                } else {
                    base = 10.0;
                    range = 90.0;
                }
                
                double val = base + random.nextDouble() * range;
                int precision = p.getDisplayPrecision() != null ? p.getDisplayPrecision() : 1;
                
                if (p.getDataType() != null && p.getDataType() == 1) {
                    p.setPointValue(String.valueOf((int) val));
                } else {
                    p.setPointValue(String.format("%." + precision + "f", val));
                }
            }
        }
        
        return ApiResponse.success(points);
    }
}

