package com.n4monitor.controller;

import com.n4monitor.dto.ApiResponse;
import com.n4monitor.dto.DeviceTreeNodeDto;
import com.n4monitor.service.DeviceService;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/devices")
public class DeviceController {

    private final DeviceService deviceService;
    private final com.n4monitor.mapper.MonitorPointMapper monitorPointMapper;

    public DeviceController(DeviceService deviceService, com.n4monitor.mapper.MonitorPointMapper monitorPointMapper) {
        this.deviceService = deviceService;
        this.monitorPointMapper = monitorPointMapper;
    }

    @GetMapping("/tree")
    public ApiResponse<List<DeviceTreeNodeDto>> getDeviceTree() {
        return ApiResponse.success(deviceService.getDeviceTree());
    }

    @GetMapping
    public ApiResponse<List<com.n4monitor.entity.Device>> getAllDevices() {
        return ApiResponse.success(deviceService.getAllDevices());
    }

    @org.springframework.web.bind.annotation.PostMapping
    public ApiResponse<String> createDevice(@org.springframework.web.bind.annotation.RequestBody com.n4monitor.entity.Device device) {
        try {
            deviceService.createDevice(device);
            return ApiResponse.success("部署设备成功");
        } catch (IllegalArgumentException e) {
            return ApiResponse.error(400, e.getMessage());
        } catch (Exception e) {
            return ApiResponse.error(500, "服务器错误: " + e.getMessage());
        }
    }

    @org.springframework.web.bind.annotation.PutMapping("/{id}")
    public ApiResponse<String> updateDevice(@PathVariable("id") Long id, @org.springframework.web.bind.annotation.RequestBody com.n4monitor.entity.Device device) {
        try {
            device.setDeviceId(id);
            deviceService.updateDevice(device);
            return ApiResponse.success("更新设备成功");
        } catch (IllegalArgumentException e) {
            return ApiResponse.error(400, e.getMessage());
        } catch (Exception e) {
            return ApiResponse.error(500, "服务器错误: " + e.getMessage());
        }
    }

    @org.springframework.web.bind.annotation.DeleteMapping("/{id}")
    public ApiResponse<String> deleteDevice(@PathVariable("id") Long id) {
        try {
            deviceService.deleteDevice(id);
            return ApiResponse.success("删除设备成功");
        } catch (IllegalArgumentException e) {
            return ApiResponse.error(400, e.getMessage());
        } catch (Exception e) {
            return ApiResponse.error(500, "服务器错误: " + e.getMessage());
        }
    }

    @org.springframework.web.bind.annotation.PutMapping("/{id}/move")
    public ApiResponse<String> moveDevice(
            @PathVariable("id") Long id,
            @org.springframework.web.bind.annotation.RequestParam("targetPath") String targetPath,
            @org.springframework.web.bind.annotation.RequestParam(value = "newName", required = false) String newName) {
        try {
            deviceService.moveDevice(id, targetPath, newName);
            return ApiResponse.success("移动设备成功");
        } catch (IllegalArgumentException e) {
            return ApiResponse.error(400, e.getMessage());
        } catch (Exception e) {
            return ApiResponse.error(500, "服务器错误: " + e.getMessage());
        }
    }

    @org.springframework.web.bind.annotation.PostMapping("/{id}/points")
    public ApiResponse<String> addPoint(@PathVariable("id") Long deviceId, @org.springframework.web.bind.annotation.RequestBody com.n4monitor.entity.MonitorPoint point) {
        try {
            point.setDeviceId(deviceId);
            if (point.getPointCode() == null || point.getPointCode().trim().isEmpty()) {
                point.setPointCode(deviceId + "_" + point.getRegisterId());
            }
            if (point.getPointStatus() == null) {
                point.setPointStatus(1);
            }
            if (point.getPointValue() == null) {
                point.setPointValue("0");
            }
            if (point.getDataType() == null) {
                point.setDataType((point.getDisplayPrecision() != null && point.getDisplayPrecision() > 0) ? 2 : 1);
            }

            // Check if point with the same point_code already exists for this device to prevent duplicate keys
            List<com.n4monitor.entity.MonitorPoint> existingPoints = monitorPointMapper.findByDeviceId(deviceId);
            com.n4monitor.entity.MonitorPoint existingPoint = existingPoints.stream()
                .filter(p -> p.getPointCode() != null && p.getPointCode().equals(point.getPointCode()))
                .findFirst()
                .orElse(null);

            if (existingPoint != null) {
                point.setPointId(existingPoint.getPointId());
                if (point.getPointValue() == null || "0".equals(point.getPointValue())) {
                    point.setPointValue(existingPoint.getPointValue());
                }
                if (point.getPointStatus() == null) {
                    point.setPointStatus(existingPoint.getPointStatus());
                }
                monitorPointMapper.update(point);
                return ApiResponse.success("更新点位成功");
            } else {
                monitorPointMapper.insert(point);
                return ApiResponse.success("添加点位成功");
            }
        } catch (Exception e) {
            return ApiResponse.error(500, "服务器错误: " + e.getMessage());
        }
    }

    @org.springframework.web.bind.annotation.PutMapping("/points/{pointId}")
    public ApiResponse<String> updatePoint(@PathVariable("pointId") Long pointId, @org.springframework.web.bind.annotation.RequestBody com.n4monitor.entity.MonitorPoint point) {
        try {
            // Retrieve existing point from database
            List<com.n4monitor.entity.MonitorPoint> allPoints = monitorPointMapper.findAll();
            com.n4monitor.entity.MonitorPoint existing = allPoints.stream()
                .filter(p -> p.getPointId().equals(pointId))
                .findFirst()
                .orElse(null);

            if (existing == null) {
                return ApiResponse.error(404, "点位不存在");
            }

            // Merge updated fields
            if (point.getRegisterId() != null) {
                existing.setRegisterId(point.getRegisterId());
            }
            if (point.getPointName() != null) {
                existing.setPointName(point.getPointName());
            }
            if (point.getPointType() != null) {
                existing.setPointType(point.getPointType());
            }
            if (point.getUnit() != null) {
                existing.setUnit(point.getUnit());
            }
            if (point.getDisplayPrecision() != null) {
                existing.setDisplayPrecision(point.getDisplayPrecision());
                existing.setDataType((point.getDisplayPrecision() > 0) ? 2 : 1);
            }
            if (point.getPointValue() != null) {
                existing.setPointValue(point.getPointValue());
            }
            if (point.getPointStatus() != null) {
                existing.setPointStatus(point.getPointStatus());
            }

            // Re-calculate pointCode in case deviceId or registerId changed
            existing.setPointCode(existing.getDeviceId() + "_" + existing.getRegisterId());

            monitorPointMapper.update(existing);
            return ApiResponse.success("修改点位成功");
        } catch (Exception e) {
            return ApiResponse.error(500, "服务器错误: " + e.getMessage());
        }
    }

    @org.springframework.web.bind.annotation.DeleteMapping("/points/{pointId}")
    public ApiResponse<String> deletePoint(@PathVariable("pointId") Long pointId) {
        try {
            monitorPointMapper.deleteByPointId(pointId);
            return ApiResponse.success("删除点位成功");
        } catch (Exception e) {
            return ApiResponse.error(500, "服务器错误: " + e.getMessage());
        }
    }
}
