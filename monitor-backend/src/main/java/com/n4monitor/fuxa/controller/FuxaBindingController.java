package com.n4monitor.fuxa.controller;

import com.n4monitor.common.ApiResponse;
import com.n4monitor.fuxa.dto.FuxaCabinSessionDto;
import com.n4monitor.fuxa.service.FuxaCabinClientService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/fuxa")
public class FuxaBindingController {
    private final FuxaCabinClientService cabinClientService;

    public FuxaBindingController(
        FuxaCabinClientService cabinClientService
    ) {
        this.cabinClientService = cabinClientService;
    }

    @GetMapping("/cabins")
    public ApiResponse<java.util.List<String>> getConfiguredCabins() {
        return ApiResponse.success(cabinClientService.getConfiguredCabins());
    }

    @PostMapping("/cabins/{cabinName}/runtime/preload")
    public ApiResponse<FuxaCabinSessionDto> preloadCabinRuntime(@PathVariable String cabinName) {
        try {
            return ApiResponse.success(cabinClientService.preloadRuntime(cabinName));
        } catch (IllegalArgumentException ex) {
            return ApiResponse.error(404, ex.getMessage());
        } catch (IllegalStateException ex) {
            return ApiResponse.error(502, ex.getMessage());
        }
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
