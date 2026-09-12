package com.n4monitor.template.controller;

import com.n4monitor.common.ApiResponse;
import com.n4monitor.template.entity.DeviceTemplate;
import com.n4monitor.template.entity.PointTemplate;
import com.n4monitor.template.service.TemplateService;
import com.n4monitor.transfer.ImportSummary;
import com.n4monitor.transfer.template.TemplateExportService;
import com.n4monitor.transfer.template.TemplateImportTemplateService;
import com.n4monitor.transfer.template.TemplateTransferService;
import com.n4monitor.annotation.BusinessType;
import com.n4monitor.annotation.OperationLog;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/templates")
public class TemplateController {

    private final TemplateService templateService;
    private final TemplateTransferService templateTransferService;
    private final TemplateExportService templateExportService;
    private final TemplateImportTemplateService templateImportTemplateService;

    public TemplateController(TemplateService templateService,
                              TemplateTransferService templateTransferService,
                              TemplateExportService templateExportService,
                              TemplateImportTemplateService templateImportTemplateService) {
        this.templateService = templateService;
        this.templateTransferService = templateTransferService;
        this.templateExportService = templateExportService;
        this.templateImportTemplateService = templateImportTemplateService;
    }

    /** List device templates with point counts and real/mock tags */
    @GetMapping
    public ApiResponse<List<DeviceTemplate>> listTemplates() {
        return ApiResponse.success(templateService.listTemplates());
    }

    /** Get template detail */
    @GetMapping("/{id}")
    public ApiResponse<DeviceTemplate> getTemplate(@PathVariable("id") Long id) {
        return ApiResponse.success(templateService.getTemplate(id));
    }

    /** Create template */
    @PostMapping
    @PreAuthorize("hasAuthority('device:template:add')")
    public ApiResponse<DeviceTemplate> createTemplate(@RequestBody DeviceTemplate template) {
        return ApiResponse.success(templateService.createTemplate(template));
    }

    /** Update template */
    @PutMapping("/{id}")
    @PreAuthorize("hasAuthority('device:template:edit')")
    public ApiResponse<String> updateTemplate(@PathVariable("id") Long id, @RequestBody DeviceTemplate template) {
        templateService.updateTemplate(id, template);
        return ApiResponse.success("Template updated successfully");
    }

    /** Delete template (logical delete) */
    @DeleteMapping("/{id}")
    @PreAuthorize("hasAuthority('device:template:delete')")
    public ApiResponse<String> deleteTemplate(@PathVariable("id") Long id) {
        templateService.deleteTemplate(id);
        return ApiResponse.success("Template deleted successfully");
    }

    /** List points under a template */
    @GetMapping("/{id}/points")
    public ApiResponse<List<PointTemplate>> listPoints(@PathVariable("id") Long id) {
        return ApiResponse.success(templateService.listPoints(id));
    }

    /** Create point under a template */
    @PostMapping("/{id}/points")
    @PreAuthorize("hasAuthority('device:template:point:add')")
    public ApiResponse<PointTemplate> createPoint(@PathVariable("id") Long id, @RequestBody PointTemplate point) {
        return ApiResponse.success(templateService.createPoint(id, point));
    }

    /** Update point */
    @PutMapping("/points/{pointId}")
    @PreAuthorize("hasAuthority('device:template:point:edit')")
    public ApiResponse<String> updatePoint(@PathVariable("pointId") Long pointId, @RequestBody PointTemplate point) {
        templateService.updatePoint(pointId, point);
        return ApiResponse.success("Point updated successfully");
    }

    /** Delete point (logical delete) */
    @DeleteMapping("/points/{pointId}")
    @PreAuthorize("hasAuthority('device:template:point:delete')")
    public ApiResponse<String> deletePoint(@PathVariable("pointId") Long pointId) {
        templateService.deletePoint(pointId);
        return ApiResponse.success("Point deleted successfully");
    }

    /** Preview or import the teacher-provided template workbook. */
    @PostMapping(value = "/import", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @PreAuthorize("hasAuthority('device:template:import')")
    @OperationLog(module = "Template Management", operation = "Import template workbook", businessType = BusinessType.IMPORT)
    public ApiResponse<ImportSummary> importWorkbook(
            @RequestPart("file") MultipartFile file,
            @RequestParam(value = "dryRun", defaultValue = "false") boolean dryRun) {
        return ApiResponse.success(templateTransferService.importWorkbook(file, dryRun));
    }

    /** Download a workbook with the teacher-provided template layout. */
    @GetMapping("/import-template")
    @PreAuthorize("hasAuthority('device:template:import')")
    @OperationLog(module = "Template Management", operation = "Download template import template", businessType = BusinessType.EXPORT)
    public ResponseEntity<ByteArrayResource> downloadImportTemplate() {
        byte[] content = templateImportTemplateService.generate();
        HttpHeaders headers = new HttpHeaders();
        headers.setContentDisposition(ContentDisposition.attachment().filename("KVTP-A-single-panel-plan.xlsx").build());
        headers.setContentType(MediaType.parseMediaType(
                "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"));
        return ResponseEntity.ok().headers(headers).body(new ByteArrayResource(content));
    }

    /** Export a database snapshot of active device and point templates. */
    @GetMapping("/export")
    @PreAuthorize("hasAuthority('device:template:export')")
    @OperationLog(module = "Template Management", operation = "Export template snapshot", businessType = BusinessType.EXPORT)
    public ResponseEntity<ByteArrayResource> exportWorkbook() {
        byte[] content = templateExportService.exportWorkbook();
        HttpHeaders headers = new HttpHeaders();
        headers.setContentDisposition(ContentDisposition.attachment().filename("n4-template-snapshot.xlsx").build());
        headers.setContentType(MediaType.parseMediaType(
                "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"));
        return ResponseEntity.ok().headers(headers).body(new ByteArrayResource(content));
    }
}
