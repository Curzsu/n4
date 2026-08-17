package com.n4monitor.deployment.controller;

import com.n4monitor.common.ApiResponse;
import com.n4monitor.deployment.entity.DeviceDeployment;
import com.n4monitor.deployment.entity.PointDeployment;
import com.n4monitor.deployment.service.DeploymentService;
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
import java.util.Map;

@RestController
@RequestMapping("/api/deployments")
public class DeploymentController {

    private final DeploymentService deploymentService;

    public DeploymentController(DeploymentService deploymentService) {
        this.deploymentService = deploymentService;
    }

    /** 全部设备部署列表 */
    @GetMapping
    public ApiResponse<List<DeviceDeployment>> listAll() {
        return ApiResponse.success(deploymentService.listAll());
    }

    /** 某导航节点下的设备部署列表 */
    @GetMapping("/nodes/{nodeId}")
    public ApiResponse<List<DeviceDeployment>> listByNode(@PathVariable Long nodeId) {
        return ApiResponse.success(deploymentService.listByNode(nodeId));
    }

    /** 单个设备部署详情 */
    @GetMapping("/{deviceDeployId}")
    public ApiResponse<DeviceDeployment> getDevice(@PathVariable Long deviceDeployId) {
        return ApiResponse.success(deploymentService.getDevice(deviceDeployId));
    }

    /** 设备部署点位列表 */
    @GetMapping("/{deviceDeployId}/points")
    public ApiResponse<List<PointDeployment>> listPoints(@PathVariable Long deviceDeployId) {
        return ApiResponse.success(deploymentService.listPoints(deviceDeployId));
    }

    /** 部署新设备（自动从模板生成点位） */
    @PostMapping
    public ApiResponse<DeviceDeployment> createDeployment(@RequestBody DeviceDeployment deployment) {
        return ApiResponse.success(deploymentService.createDeployment(deployment));
    }

    /** 更新设备部署信息 */
    @PutMapping("/{deviceDeployId}")
    public ApiResponse<String> updateDevice(@PathVariable Long deviceDeployId,
                                            @RequestBody DeviceDeployment deployment) {
        deploymentService.updateDevice(deviceDeployId, deployment);
        return ApiResponse.success("更新设备部署成功");
    }

    /** 移动设备到其他节点 */
    @PutMapping("/{deviceDeployId}/move")
    public ApiResponse<String> moveDevice(@PathVariable Long deviceDeployId,
                                          @RequestBody Map<String, Object> body) {
        Long targetNodeId = body.get("targetNodeId") == null ? null
                : Long.valueOf(body.get("targetNodeId").toString());
        String newName = body.get("newName") == null ? null : body.get("newName").toString();
        deploymentService.moveDevice(deviceDeployId, targetNodeId, newName);
        return ApiResponse.success("移动设备成功");
    }

    /** 更新部署点位 */
    @PutMapping("/points/{pointDeployId}")
    public ApiResponse<String> updatePoint(@PathVariable Long pointDeployId,
                                           @RequestBody PointDeployment point) {
        deploymentService.updatePoint(pointDeployId, point);
        return ApiResponse.success("更新点位成功");
    }

    /** 删除设备部署（级联删除点位） */
    @DeleteMapping("/{deviceDeployId}")
    public ApiResponse<String> deleteDeployment(@PathVariable Long deviceDeployId) {
        deploymentService.deleteDeployment(deviceDeployId);
        return ApiResponse.success("删除设备部署成功");
    }
}
