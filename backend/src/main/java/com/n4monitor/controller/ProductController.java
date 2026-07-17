package com.n4monitor.controller;

import com.n4monitor.dto.ApiResponse;
import com.n4monitor.entity.Product;
import com.n4monitor.service.ProductService;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/products")
public class ProductController {

    private final ProductService productService;
    private final com.n4monitor.mapper.DeviceMapper deviceMapper;
    private final com.n4monitor.mapper.MonitorPointMapper monitorPointMapper;

    public ProductController(ProductService productService,
                             com.n4monitor.mapper.DeviceMapper deviceMapper,
                             com.n4monitor.mapper.MonitorPointMapper monitorPointMapper) {
        this.productService = productService;
        this.deviceMapper = deviceMapper;
        this.monitorPointMapper = monitorPointMapper;
    }

    @GetMapping
    public ApiResponse<List<Product>> getAllProducts() {
        return ApiResponse.success(productService.getAllProducts());
    }

    @GetMapping("/{id}")
    public ApiResponse<Product> getProductById(@PathVariable("id") Long id) {
        Product product = productService.getProductById(id);
        if (product == null) {
            return ApiResponse.error(404, "产品不存在");
        }
        return ApiResponse.success(product);
    }

    @PostMapping
    public ApiResponse<Product> createProduct(@RequestBody Product product) {
        try {
            productService.createProduct(product);
            return ApiResponse.success(product);
        } catch (IllegalArgumentException e) {
            e.printStackTrace();
            return ApiResponse.error(400, e.getMessage());
        } catch (Exception e) {
            e.printStackTrace();
            return ApiResponse.error(500, e.getMessage());
        }
    }

    @PutMapping("/{id}")
    public ApiResponse<String> updateProduct(@PathVariable("id") Long id, @RequestBody Product product) {
        try {
            product.setProductId(id);
            productService.updateProduct(product);
            return ApiResponse.success("更新成功");
        } catch (IllegalArgumentException e) {
            return ApiResponse.error(400, e.getMessage());
        } catch (Exception e) {
            return ApiResponse.error(500, "服务器错误: " + e.getMessage());
        }
    }

    @DeleteMapping("/{id}")
    public ApiResponse<String> deleteProduct(@PathVariable("id") Long id) {
        try {
            productService.deleteProduct(id);
            return ApiResponse.success("删除成功");
        } catch (IllegalArgumentException e) {
            return ApiResponse.error(400, e.getMessage());
        } catch (Exception e) {
            return ApiResponse.error(500, "服务器错误: " + e.getMessage());
        }
    }

    @GetMapping("/{id}/points")
    public ApiResponse<List<com.n4monitor.entity.MonitorPoint>> getProductPoints(@PathVariable("id") Long id) {
        try {
            com.n4monitor.entity.Device templateDevice = productService.getOrCreateTemplateDevice(id);
            return ApiResponse.success(monitorPointMapper.findByDeviceId(templateDevice.getDeviceId()));
        } catch (Exception e) {
            return ApiResponse.error(500, e.getMessage());
        }
    }
}
