package com.n4monitor.device.controller;

import com.n4monitor.common.ApiResponse;
import com.n4monitor.device.dto.DeviceTreeNodeDto;
import com.n4monitor.device.entity.Device;
import com.n4monitor.device.entity.MonitorPoint;
import com.n4monitor.device.service.DeviceService;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/devices")
public class DeviceController {

    private final DeviceService deviceService;

    public DeviceController(DeviceService deviceService) {
        this.deviceService = deviceService;
    }

    @GetMapping("/tree")
    public ApiResponse<List<DeviceTreeNodeDto>> getDeviceTree() {
        return ApiResponse.success(deviceService.getDeviceTree());
    }

    @GetMapping
    public ApiResponse<List<Device>> getAllDevices() {
        return ApiResponse.success(deviceService.getAllDevices());
    }

    @PostMapping
    public ApiResponse<String> createDevice(@RequestBody Device device) {
        deviceService.createDevice(device);
        return ApiResponse.success("部署设备成功");
    }

    @PutMapping("/{id}")
    public ApiResponse<String> updateDevice(@PathVariable("id") Long id, @RequestBody Device device) {
        device.setDeviceId(id);
        deviceService.updateDevice(device);
        return ApiResponse.success("更新设备成功");
    }

    @DeleteMapping("/{id}")
    public ApiResponse<String> deleteDevice(@PathVariable("id") Long id) {
        deviceService.deleteDevice(id);
        return ApiResponse.success("删除设备成功");
    }

    @PutMapping("/{id}/move")
    public ApiResponse<String> moveDevice(
            @PathVariable("id") Long id,
            @RequestParam("targetPath") String targetPath,
            @RequestParam(value = "newName", required = false) String newName) {
        deviceService.moveDevice(id, targetPath, newName);
        return ApiResponse.success("移动设备成功");
    }

    @PostMapping("/{id}/points")
    public ApiResponse<String> addPoint(@PathVariable("id") Long deviceId, @RequestBody MonitorPoint point) {
        boolean updatedExisting = deviceService.addOrUpdatePoint(deviceId, point);
        return updatedExisting ? ApiResponse.success("更新点位成功") : ApiResponse.success("添加点位成功");
    }

    @PutMapping("/points/{pointId}")
    public ApiResponse<String> updatePoint(@PathVariable("pointId") Long pointId, @RequestBody MonitorPoint point) {
        MonitorPoint existing = deviceService.updatePoint(pointId, point);
        if (existing == null) {
            return ApiResponse.error(404, "点位不存在");
        }
        return ApiResponse.success("修改点位成功");
    }

    @DeleteMapping("/points/{pointId}")
    public ApiResponse<String> deletePoint(@PathVariable("pointId") Long pointId) {
        deviceService.deletePoint(pointId);
        return ApiResponse.success("删除点位成功");
    }
}
