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

    /** List all deployed devices */
    @GetMapping
    public ApiResponse<List<DeviceDeployment>> listAll() {
        return ApiResponse.success(deploymentService.listAll());
    }

    /** List deployed devices under a navigation node */
    @GetMapping("/nodes/{nodeId}")
    public ApiResponse<List<DeviceDeployment>> listByNode(@PathVariable Long nodeId) {
        return ApiResponse.success(deploymentService.listByNode(nodeId));
    }

    /** Get deployed device details */
    @GetMapping("/{deviceDeployId}")
    public ApiResponse<DeviceDeployment> getDevice(@PathVariable Long deviceDeployId) {
        return ApiResponse.success(deploymentService.getDevice(deviceDeployId));
    }

    /** List points for a deployed device */
    @GetMapping("/{deviceDeployId}/points")
    public ApiResponse<List<PointDeployment>> listPoints(@PathVariable Long deviceDeployId) {
        return ApiResponse.success(deploymentService.listPoints(deviceDeployId));
    }

    /** Deploy new device (generate points from template) */
    @PostMapping
    public ApiResponse<DeviceDeployment> createDeployment(@RequestBody DeviceDeployment deployment) {
        return ApiResponse.success(deploymentService.createDeployment(deployment));
    }

    /** Update deployed device */
    @PutMapping("/{deviceDeployId}")
    public ApiResponse<String> updateDevice(@PathVariable Long deviceDeployId,
                                            @RequestBody DeviceDeployment deployment) {
        deploymentService.updateDevice(deviceDeployId, deployment);
        return ApiResponse.success("Device deployment updated successfully");
    }

    /** Move device to another node */
    @PutMapping("/{deviceDeployId}/move")
    public ApiResponse<String> moveDevice(@PathVariable Long deviceDeployId,
                                          @RequestBody Map<String, Object> body) {
        Long targetNodeId = body.get("targetNodeId") == null ? null
                : Long.valueOf(body.get("targetNodeId").toString());
        String newName = body.get("newName") == null ? null : body.get("newName").toString();
        deploymentService.moveDevice(deviceDeployId, targetNodeId, newName);
        return ApiResponse.success("Device moved successfully");
    }

    /** Update deployed point */
    @PutMapping("/points/{pointDeployId}")
    public ApiResponse<String> updatePoint(@PathVariable Long pointDeployId,
                                           @RequestBody PointDeployment point) {
        deploymentService.updatePoint(pointDeployId, point);
        return ApiResponse.success("Point updated successfully");
    }

    /** Delete device deployment (cascade deletes points) */
    @DeleteMapping("/{deviceDeployId}")
    public ApiResponse<String> deleteDeployment(@PathVariable Long deviceDeployId) {
        deploymentService.deleteDeployment(deviceDeployId);
        return ApiResponse.success("Device deployment deleted successfully");
    }
}
