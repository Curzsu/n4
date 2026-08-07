package com.n4monitor.controller;

import com.n4monitor.dto.ApiResponse;
import com.n4monitor.entity.DeviceTemplate;
import com.n4monitor.entity.PointTemplate;
import com.n4monitor.service.TemplateService;
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
        try {
            return ApiResponse.success(templateService.getTemplate(id));
        } catch (IllegalArgumentException e) {
            return ApiResponse.error(404, e.getMessage());
        }
    }

    /** 新增模板（Mock） */
    @PostMapping
    public ApiResponse<DeviceTemplate> createTemplate(@RequestBody DeviceTemplate template) {
        try {
            return ApiResponse.success(templateService.createTemplate(template));
        } catch (IllegalArgumentException e) {
            return ApiResponse.error(400, e.getMessage());
        } catch (Exception e) {
            return ApiResponse.error(500, "服务器错误: " + e.getMessage());
        }
    }

    /** 编辑模板 */
    @PutMapping("/{id}")
    public ApiResponse<String> updateTemplate(@PathVariable("id") Long id, @RequestBody DeviceTemplate template) {
        try {
            templateService.updateTemplate(id, template);
            return ApiResponse.success("更新成功");
        } catch (IllegalArgumentException e) {
            return ApiResponse.error(400, e.getMessage());
        } catch (Exception e) {
            return ApiResponse.error(500, "服务器错误: " + e.getMessage());
        }
    }

    /** 删除模板（逻辑删除） */
    @DeleteMapping("/{id}")
    public ApiResponse<String> deleteTemplate(@PathVariable("id") Long id) {
        try {
            templateService.deleteTemplate(id);
            return ApiResponse.success("删除成功");
        } catch (IllegalArgumentException e) {
            return ApiResponse.error(404, e.getMessage());
        }
    }

    /** 模板点位列表（含真实/Mock 标识） */
    @GetMapping("/{id}/points")
    public ApiResponse<List<PointTemplate>> listPoints(@PathVariable("id") Long id) {
        try {
            return ApiResponse.success(templateService.listPoints(id));
        } catch (IllegalArgumentException e) {
            return ApiResponse.error(404, e.getMessage());
        }
    }

    /** 模板新增点位 */
    @PostMapping("/{id}/points")
    public ApiResponse<PointTemplate> createPoint(@PathVariable("id") Long id, @RequestBody PointTemplate point) {
        try {
            return ApiResponse.success(templateService.createPoint(id, point));
        } catch (IllegalArgumentException e) {
            return ApiResponse.error(400, e.getMessage());
        } catch (Exception e) {
            return ApiResponse.error(500, "服务器错误: " + e.getMessage());
        }
    }

    /** 修改点位 */
    @PutMapping("/points/{pointId}")
    public ApiResponse<String> updatePoint(@PathVariable("pointId") Long pointId, @RequestBody PointTemplate point) {
        try {
            templateService.updatePoint(pointId, point);
            return ApiResponse.success("更新成功");
        } catch (IllegalArgumentException e) {
            return ApiResponse.error(400, e.getMessage());
        }
    }

    /** 删除点位（逻辑删除） */
    @DeleteMapping("/points/{pointId}")
    public ApiResponse<String> deletePoint(@PathVariable("pointId") Long pointId) {
        try {
            templateService.deletePoint(pointId);
            return ApiResponse.success("删除成功");
        } catch (IllegalArgumentException e) {
            return ApiResponse.error(404, e.getMessage());
        }
    }
}
