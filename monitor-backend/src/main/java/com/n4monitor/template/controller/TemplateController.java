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

    /** 设备模板列表（含真实/Mock 标识与点位数量） */
    @GetMapping
    public ApiResponse<List<DeviceTemplate>> listTemplates() {
        return ApiResponse.success(templateService.listTemplates());
    }

    /** 模板详情 */
    @GetMapping("/{id}")
    public ApiResponse<DeviceTemplate> getTemplate(@PathVariable("id") Long id) {
        return ApiResponse.success(templateService.getTemplate(id));
    }

    /** 新增模板（Mock） */
    @PostMapping
    public ApiResponse<DeviceTemplate> createTemplate(@RequestBody DeviceTemplate template) {
        return ApiResponse.success(templateService.createTemplate(template));
    }

    /** 编辑模板 */
    @PutMapping("/{id}")
    public ApiResponse<String> updateTemplate(@PathVariable("id") Long id, @RequestBody DeviceTemplate template) {
        templateService.updateTemplate(id, template);
        return ApiResponse.success("更新成功");
    }

    /** 删除模板（逻辑删除） */
    @DeleteMapping("/{id}")
    public ApiResponse<String> deleteTemplate(@PathVariable("id") Long id) {
        templateService.deleteTemplate(id);
        return ApiResponse.success("删除成功");
    }

    /** 模板点位列表（含真实/Mock 标识） */
    @GetMapping("/{id}/points")
    public ApiResponse<List<PointTemplate>> listPoints(@PathVariable("id") Long id) {
        return ApiResponse.success(templateService.listPoints(id));
    }

    /** 模板新增点位 */
    @PostMapping("/{id}/points")
    public ApiResponse<PointTemplate> createPoint(@PathVariable("id") Long id, @RequestBody PointTemplate point) {
        return ApiResponse.success(templateService.createPoint(id, point));
    }

    /** 修改点位 */
    @PutMapping("/points/{pointId}")
    public ApiResponse<String> updatePoint(@PathVariable("pointId") Long pointId, @RequestBody PointTemplate point) {
        templateService.updatePoint(pointId, point);
        return ApiResponse.success("更新成功");
    }

    /** 删除点位（逻辑删除） */
    @DeleteMapping("/points/{pointId}")
    public ApiResponse<String> deletePoint(@PathVariable("pointId") Long pointId) {
        templateService.deletePoint(pointId);
        return ApiResponse.success("删除成功");
    }
}
