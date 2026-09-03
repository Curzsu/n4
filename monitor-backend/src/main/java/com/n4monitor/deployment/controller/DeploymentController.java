package com.n4monitor.deployment.controller;

import com.n4monitor.common.ApiResponse;
import com.n4monitor.deployment.entity.DeviceDeployment;
import com.n4monitor.deployment.entity.PointDeployment;
import com.n4monitor.deployment.service.DeploymentService;
import com.n4monitor.annotation.BusinessType;
import com.n4monitor.annotation.OperationLog;
import com.n4monitor.transfer.ImportSummary;
import com.n4monitor.transfer.deployment.DeploymentExportService;
import com.n4monitor.transfer.deployment.DeploymentImportTemplateService;
import com.n4monitor.transfer.deployment.DeploymentTransferService;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/deployments")
public class DeploymentController {

    private final DeploymentService deploymentService;
    private final DeploymentTransferService deploymentTransferService;
    private final DeploymentExportService deploymentExportService;
    private final DeploymentImportTemplateService deploymentImportTemplateService;

    public DeploymentController(DeploymentService deploymentService,
                                DeploymentTransferService deploymentTransferService,
                                DeploymentExportService deploymentExportService,
                                DeploymentImportTemplateService deploymentImportTemplateService) {
        this.deploymentService = deploymentService;
        this.deploymentTransferService = deploymentTransferService;
        this.deploymentExportService = deploymentExportService;
        this.deploymentImportTemplateService = deploymentImportTemplateService;
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

    /** Preview or import one or more deployment CSV files or a ZIP archive. */
    @PostMapping(value = "/import", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @PreAuthorize("hasAuthority('device:deploy:view')")
    @OperationLog(module = "Deployment Management", operation = "Import deployment files", businessType = BusinessType.IMPORT)
    public ApiResponse<ImportSummary> importFiles(
            @RequestPart("files") List<MultipartFile> files,
            @RequestParam(value = "dryRun", defaultValue = "false") boolean dryRun) {
        return ApiResponse.success(deploymentTransferService.importFiles(files, dryRun));
    }

    /** Download a CSV with the teacher-provided deployment layout. */
    @GetMapping("/import-template")
    @PreAuthorize("hasAuthority('device:deploy:view')")
    @OperationLog(module = "Deployment Management", operation = "Download deployment import template", businessType = BusinessType.EXPORT)
    public ResponseEntity<ByteArrayResource> downloadImportTemplate() {
        byte[] content = deploymentImportTemplateService.generate();
        HttpHeaders headers = new HttpHeaders();
        headers.setContentDisposition(ContentDisposition.attachment().filename("n4-deployment-import-template.csv").build());
        headers.setContentType(MediaType.parseMediaType("text/csv;charset=UTF-8"));
        return ResponseEntity.ok().headers(headers).body(new ByteArrayResource(content));
    }

    /** Export a database snapshot of deployment and alarm-rule tables. */
    @GetMapping("/export")
    @PreAuthorize("hasAuthority('device:deploy:view')")
    @OperationLog(module = "Deployment Management", operation = "Export deployment snapshot", businessType = BusinessType.EXPORT)
    public ResponseEntity<ByteArrayResource> exportSnapshot() {
        byte[] content = deploymentExportService.exportSnapshot();
        HttpHeaders headers = new HttpHeaders();
        headers.setContentDisposition(ContentDisposition.attachment().filename("n4-deployment-snapshot.zip").build());
        headers.setContentType(MediaType.APPLICATION_OCTET_STREAM);
        return ResponseEntity.ok().headers(headers).body(new ByteArrayResource(content));
    }
}
