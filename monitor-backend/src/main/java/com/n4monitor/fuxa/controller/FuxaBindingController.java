package com.n4monitor.fuxa.controller;

import com.n4monitor.common.ApiResponse;
import com.n4monitor.fuxa.dto.FuxaBindingDto;
import com.n4monitor.fuxa.dto.FuxaCabinSessionDto;
import com.n4monitor.fuxa.dto.FuxaDiscoveryRequest;
import com.n4monitor.fuxa.service.FuxaCabinClientService;
import com.n4monitor.fuxa.service.FuxaBindingService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.DeleteMapping;
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
    private final FuxaCabinClientService cabinClientService;

    public FuxaBindingController(
        FuxaBindingService bindingService,
        FuxaCabinClientService cabinClientService
    ) {
        this.bindingService = bindingService;
        this.cabinClientService = cabinClientService;
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

    @GetMapping("/cabins")
    public ApiResponse<java.util.List<String>> getConfiguredCabins() {
        return ApiResponse.success(cabinClientService.getConfiguredCabins());
    }

    @PostMapping("/cabins/{cabinName}/sessions/{sessionId}")
    public ApiResponse<FuxaCabinSessionDto> openCabinSession(
        @PathVariable String cabinName,
        @PathVariable String sessionId
    ) {
        try {
            return ApiResponse.success(cabinClientService.openSession(cabinName, sessionId));
        } catch (IllegalArgumentException ex) {
            return ApiResponse.error(404, ex.getMessage());
        } catch (IllegalStateException ex) {
            return ApiResponse.error(502, ex.getMessage());
        }
    }

    @DeleteMapping("/cabins/{cabinName}/sessions/{sessionId}")
    public ApiResponse<FuxaCabinSessionDto> releaseCabinSession(
        @PathVariable String cabinName,
        @PathVariable String sessionId
    ) {
        try {
            return ApiResponse.success(cabinClientService.releaseSession(cabinName, sessionId));
        } catch (IllegalArgumentException ex) {
            return ApiResponse.error(404, ex.getMessage());
        } catch (IllegalStateException ex) {
            return ApiResponse.error(502, ex.getMessage());
        }
    }

    @GetMapping("/cabins/{cabinName}")
    public ApiResponse<FuxaCabinSessionDto> getCabinStatus(@PathVariable String cabinName) {
        try {
            return ApiResponse.success(cabinClientService.status(cabinName));
        } catch (IllegalArgumentException ex) {
            return ApiResponse.error(404, ex.getMessage());
        } catch (IllegalStateException ex) {
            return ApiResponse.error(502, ex.getMessage());
        }
    }
}
