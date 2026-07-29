package com.n4monitor.controller;

import com.n4monitor.dto.ApiResponse;
import com.n4monitor.dto.DeviceDeployListDto;
import com.n4monitor.dto.NavTreeNodeDto;
import com.n4monitor.service.DashboardNavService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/dashboard/nav")
public class DashboardNavController {

    private final DashboardNavService navService;

    public DashboardNavController(DashboardNavService navService) {
        this.navService = navService;
    }

    /** 获取两级导航树 */
    @GetMapping("/tree")
    public ApiResponse<List<NavTreeNodeDto>> getNavTree() {
        return ApiResponse.success(navService.getNavTree());
    }

    /** 查某二级节点下设备列表 */
    @GetMapping("/nodes/{nodeId}/devices")
    public ApiResponse<List<DeviceDeployListDto>> getDevicesByNode(@PathVariable Long nodeId) {
        return ApiResponse.success(navService.getDevicesByNode(nodeId));
    }

    /** 按设备部署ID查单个设备详情 */
    @GetMapping("/devices/{deviceDeployId}")
    public ApiResponse<DeviceDeployListDto> getDeviceById(@PathVariable Long deviceDeployId) {
        return ApiResponse.success(navService.getDeviceById(deviceDeployId));
    }
}
