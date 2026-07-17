package com.n4monitor.controller;

import com.n4monitor.dto.ApiResponse;
import com.n4monitor.dto.FuxaBindingDto;
import com.n4monitor.dto.FuxaDiscoveryRequest;
import com.n4monitor.service.FuxaBindingService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequestMapping("/api/fuxa")
public class FuxaBindingController {
    private final FuxaBindingService bindingService;

    public FuxaBindingController(FuxaBindingService bindingService) {
        this.bindingService = bindingService;
    }

    @PostMapping("/discover")
    public ApiResponse<FuxaBindingDto> discover(@RequestBody FuxaDiscoveryRequest request) {
        return ApiResponse.success(bindingService.discover(request));
    }

    @GetMapping("/devices/{deviceId}")
    public ApiResponse<FuxaBindingDto> getDeviceBinding(@PathVariable Long deviceId) {
        try {
            return ApiResponse.success(bindingService.getByDeviceId(deviceId));
        } catch (IllegalArgumentException ex) {
            return ApiResponse.error(404, ex.getMessage());
        }
    }

    @PostMapping("/template/prepare")
    public ApiResponse<Map<String, Object>> prepareTemplate() {
        return ApiResponse.success(bindingService.prepareTemplate());
    }
}
