package com.n4monitor.template.controller;

import com.n4monitor.common.ApiResponse;
import com.n4monitor.template.entity.DeviceTemplate;
import com.n4monitor.template.entity.PointTemplate;
import com.n4monitor.template.service.TemplateService;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/templates")
public class TemplateController {

    private final TemplateService templateService;

    public TemplateController(TemplateService templateService) {
        this.templateService = templateService;
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
    public ApiResponse<DeviceTemplate> createTemplate(@RequestBody DeviceTemplate template) {
        return ApiResponse.success(templateService.createTemplate(template));
    }

    /** Update template */
    @PutMapping("/{id}")
    public ApiResponse<String> updateTemplate(@PathVariable("id") Long id, @RequestBody DeviceTemplate template) {
        templateService.updateTemplate(id, template);
        return ApiResponse.success("Template updated successfully");
    }

    /** Delete template (logical delete) */
    @DeleteMapping("/{id}")
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
    public ApiResponse<PointTemplate> createPoint(@PathVariable("id") Long id, @RequestBody PointTemplate point) {
        return ApiResponse.success(templateService.createPoint(id, point));
    }

    /** Update point */
    @PutMapping("/points/{pointId}")
    public ApiResponse<String> updatePoint(@PathVariable("pointId") Long pointId, @RequestBody PointTemplate point) {
        templateService.updatePoint(pointId, point);
        return ApiResponse.success("Point updated successfully");
    }

    /** Delete point (logical delete) */
    @DeleteMapping("/points/{pointId}")
    public ApiResponse<String> deletePoint(@PathVariable("pointId") Long pointId) {
        templateService.deletePoint(pointId);
        return ApiResponse.success("Point deleted successfully");
    }
}
