package com.n4monitor.product.controller;

import com.n4monitor.common.ApiResponse;
import com.n4monitor.product.entity.Product;
import com.n4monitor.product.service.ProductService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@Slf4j
@RestController
@RequestMapping("/api/products")
public class ProductController {

    private final ProductService productService;
    private final com.n4monitor.device.mapper.DeviceMapper deviceMapper;
    private final com.n4monitor.device.mapper.MonitorPointMapper monitorPointMapper;

    public ProductController(ProductService productService,
                             com.n4monitor.device.mapper.DeviceMapper deviceMapper,
                             com.n4monitor.device.mapper.MonitorPointMapper monitorPointMapper) {
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
            return ApiResponse.error(404, "Product not found");
        }
        return ApiResponse.success(product);
    }

    @PostMapping
    public ApiResponse<Product> createProduct(@RequestBody Product product) {
        productService.createProduct(product);
        return ApiResponse.success(product);
    }

    @PutMapping("/{id}")
    public ApiResponse<String> updateProduct(@PathVariable("id") Long id, @RequestBody Product product) {
        product.setProductId(id);
        productService.updateProduct(product);
        return ApiResponse.success("Product updated successfully");
    }

    @DeleteMapping("/{id}")
    public ApiResponse<String> deleteProduct(@PathVariable("id") Long id) {
        productService.deleteProduct(id);
        return ApiResponse.success("Product deleted successfully");
    }

    @GetMapping("/{id}/points")
    public ApiResponse<List<com.n4monitor.device.entity.MonitorPoint>> getProductPoints(@PathVariable("id") Long id) {
        com.n4monitor.device.entity.Device templateDevice = productService.getOrCreateTemplateDevice(id);
        return ApiResponse.success(monitorPointMapper.findByDeviceId(templateDevice.getDeviceId()));
    }
}
