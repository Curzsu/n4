package com.n4monitor.controller;

import com.n4monitor.dto.ApiResponse;
import com.n4monitor.dto.DeleteImpactDto;
import com.n4monitor.dto.DeviceDeployListDto;
import com.n4monitor.dto.NavTreeNodeDto;
import com.n4monitor.entity.NavNode;
import com.n4monitor.service.DashboardNavService;
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

    // ===== System Deployment 页：节点 CRUD =====

    /** 新增节点 */
    @PostMapping("/nodes")
    public ApiResponse<NavNode> createNode(@RequestBody NavNode node) {
        try {
            return ApiResponse.success(navService.createNode(node));
        } catch (IllegalArgumentException e) {
            return ApiResponse.error(400, e.getMessage());
        } catch (Exception e) {
            return ApiResponse.error(500, "新增节点失败：" + e.getMessage());
        }
    }

    /** 编辑节点 */
    @PutMapping("/nodes/{nodeId}")
    public ApiResponse<NavNode> updateNode(@PathVariable Long nodeId, @RequestBody NavNode node) {
        try {
            return ApiResponse.success(navService.updateNode(nodeId, node));
        } catch (IllegalArgumentException e) {
            return ApiResponse.error(400, e.getMessage());
        } catch (Exception e) {
            return ApiResponse.error(500, "更新节点失败：" + e.getMessage());
        }
    }

    /** 删除节点（级联：删一级连带子节点并解绑设备） */
    @DeleteMapping("/nodes/{nodeId}")
    public ApiResponse<DeleteImpactDto> deleteNode(@PathVariable Long nodeId) {
        try {
            return ApiResponse.success(navService.deleteNode(nodeId));
        } catch (IllegalArgumentException e) {
            return ApiResponse.error(400, e.getMessage());
        } catch (Exception e) {
            return ApiResponse.error(500, "删除节点失败：" + e.getMessage());
        }
    }

    /** 删除前预览影响范围（子节点数 + 设备数） */
    @GetMapping("/nodes/{nodeId}/impact")
    public ApiResponse<DeleteImpactDto> getDeleteImpact(@PathVariable Long nodeId) {
        try {
            return ApiResponse.success(navService.getDeleteImpact(nodeId));
        } catch (IllegalArgumentException e) {
            return ApiResponse.error(400, e.getMessage());
        } catch (Exception e) {
            return ApiResponse.error(500, "查询影响范围失败：" + e.getMessage());
        }
    }

    // ===== 设备挂载/卸载 =====

    /** 查所有可挂载设备（nav_node_id 为空） */
    @GetMapping("/devices/mountable")
    public ApiResponse<List<DeviceDeployListDto>> getMountableDevices() {
        return ApiResponse.success(navService.getMountableDevices());
    }

    /** 批量挂载设备到某二级节点 */
    @PostMapping("/nodes/{nodeId}/devices")
    public ApiResponse<Integer> mountDevices(@PathVariable Long nodeId,
                                             @RequestBody List<Long> deviceIds) {
        try {
            return ApiResponse.success(navService.mountDevices(nodeId, deviceIds));
        } catch (IllegalArgumentException e) {
            return ApiResponse.error(400, e.getMessage());
        } catch (Exception e) {
            return ApiResponse.error(500, "挂载设备失败：" + e.getMessage());
        }
    }

    /** 卸载单台设备（nav_node_id 置空） */
    @DeleteMapping("/devices/{deviceDeployId}/mount")
    public ApiResponse<Integer> unmountDevice(@PathVariable Long deviceDeployId) {
        try {
            return ApiResponse.success(navService.unmountDevice(deviceDeployId));
        } catch (Exception e) {
            return ApiResponse.error(500, "卸载设备失败：" + e.getMessage());
        }
    }
}
