package com.n4monitor.dashboard.controller;

import com.n4monitor.common.ApiResponse;
import com.n4monitor.dashboard.dto.DeleteImpactDto;
import com.n4monitor.deployment.dto.DeviceDeployListDto;
import com.n4monitor.dashboard.dto.NavTreeNodeDto;
import com.n4monitor.dashboard.entity.NavNode;
import com.n4monitor.dashboard.service.DashboardNavService;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
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

    /** Get navigation tree */
    @GetMapping("/tree")
    public ApiResponse<List<NavTreeNodeDto>> getNavTree() {
        return ApiResponse.success(navService.getNavTree());
    }

    /** Query devices under a node */
    @GetMapping("/nodes/{nodeId}/devices")
    public ApiResponse<List<DeviceDeployListDto>> getDevicesByNode(@PathVariable Long nodeId) {
        return ApiResponse.success(navService.getDevicesByNode(nodeId));
    }

    /** Get single device detail by deployment ID */
    @GetMapping("/devices/{deviceDeployId}")
    public ApiResponse<DeviceDeployListDto> getDeviceById(@PathVariable Long deviceDeployId) {
        return ApiResponse.success(navService.getDeviceById(deviceDeployId));
    }

    // ===== Navigation Node CRUD =====

    /** Create navigation node */
    @PostMapping("/nodes")
    @PreAuthorize("hasAuthority('device:deploy:group:add')")
    public ApiResponse<NavNode> createNode(@RequestBody NavNode node) {
        return ApiResponse.success(navService.createNode(node));
    }

    /** Update navigation node */
    @PutMapping("/nodes/{nodeId}")
    @PreAuthorize("hasAuthority('device:deploy:group:edit')")
    public ApiResponse<NavNode> updateNode(@PathVariable Long nodeId, @RequestBody NavNode node) {
        return ApiResponse.success(navService.updateNode(nodeId, node));
    }

    /** Delete node (cascade delete children and unmount devices) */
    @DeleteMapping("/nodes/{nodeId}")
    @PreAuthorize("hasAuthority('device:deploy:group:delete')")
    public ApiResponse<DeleteImpactDto> deleteNode(@PathVariable Long nodeId) {
        return ApiResponse.success(navService.deleteNode(nodeId));
    }

    /** Preview delete impact (child node count and mounted device count) */
    @GetMapping("/nodes/{nodeId}/impact")
    public ApiResponse<DeleteImpactDto> getDeleteImpact(@PathVariable Long nodeId) {
        return ApiResponse.success(navService.getDeleteImpact(nodeId));
    }

    // ===== Device Mount / Unmount =====

    /** Query mountable devices (unmounted) */
    @GetMapping("/devices/mountable")
    public ApiResponse<List<DeviceDeployListDto>> getMountableDevices() {
        return ApiResponse.success(navService.getMountableDevices());
    }

    /** Mount devices to a leaf node */
    @PostMapping("/nodes/{nodeId}/devices")
    @PreAuthorize("hasAuthority('device:deploy:mount')")
    public ApiResponse<Integer> mountDevices(@PathVariable Long nodeId,
                                             @RequestBody List<Long> deviceIds) {
        return ApiResponse.success(navService.mountDevices(nodeId, deviceIds));
    }

    /** Unmount device */
    @DeleteMapping("/devices/{deviceDeployId}/mount")
    @PreAuthorize("hasAuthority('device:deploy:unmount')")
    public ApiResponse<Integer> unmountDevice(@PathVariable Long deviceDeployId) {
        return ApiResponse.success(navService.unmountDevice(deviceDeployId));
    }
}
