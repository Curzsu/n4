package com.n4monitor.service;

import com.n4monitor.entity.Product;
import com.n4monitor.mapper.ProductMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class ProductService {

    private final ProductMapper productMapper;
    private final com.n4monitor.mapper.DeviceMapper deviceMapper;
    private final com.n4monitor.mapper.MonitorPointMapper monitorPointMapper;

    public ProductService(ProductMapper productMapper, com.n4monitor.mapper.DeviceMapper deviceMapper, com.n4monitor.mapper.MonitorPointMapper monitorPointMapper) {
        this.productMapper = productMapper;
        this.deviceMapper = deviceMapper;
        this.monitorPointMapper = monitorPointMapper;
    }

    public List<Product> getAllProducts() {
        return productMapper.findAll();
    }

    public Product getProductById(Long id) {
        return productMapper.findByProductId(id);
    }

    @Transactional
    public void createProduct(Product product) {
        if (product.getProductName() == null || product.getProductName().trim().isEmpty()) {
            throw new IllegalArgumentException("产品名称不能为空");
        }
        if (product.getProductType() == null || product.getProductType().trim().isEmpty()) {
            throw new IllegalArgumentException("产品类型不能为空");
        }
        productMapper.insert(product);

        // Auto-create a hidden template device for this product to host template points
        com.n4monitor.entity.Device templateDevice = new com.n4monitor.entity.Device();
        templateDevice.setProductId(product.getProductId());
        templateDevice.setDeviceName(product.getProductName() + "_模板");
        templateDevice.setDeviceCode("TEMPLATE_" + product.getProductId());
        templateDevice.setSysPath("/TEMPLATES/" + product.getProductName());
        templateDevice.setSysLevel(1);
        templateDevice.setSysParentId(null);
        templateDevice.setLocPath("/TEMPLATES/" + product.getProductName());
        templateDevice.setLocLevel(1);
        templateDevice.setLocParentId(null);
        templateDevice.setStatus(1); // 1 = normal
        templateDevice.setIsDeleted(false);
        deviceMapper.insert(templateDevice);

        // Add a default point "通信状态" for this template device
        com.n4monitor.entity.MonitorPoint defaultPoint = new com.n4monitor.entity.MonitorPoint();
        defaultPoint.setDeviceId(templateDevice.getDeviceId());
        defaultPoint.setRegisterId("191001");
        defaultPoint.setPointName("通信状态");
        defaultPoint.setPointCode(templateDevice.getDeviceId() + "_191001");
        defaultPoint.setPointType(1); // DI
        defaultPoint.setDataType(1);  // int
        defaultPoint.setUnit("无");
        defaultPoint.setDisplayPrecision(0);
        defaultPoint.setPointValue("1");
        defaultPoint.setPointStatus(1); // normal
        monitorPointMapper.insert(defaultPoint);
    }

    @Transactional
    public void updateProduct(Product product) {
        if (product.getProductId() == null) {
            throw new IllegalArgumentException("产品ID不能为空");
        }
        if (product.getProductName() == null || product.getProductName().trim().isEmpty()) {
            throw new IllegalArgumentException("产品名称不能为空");
        }
        if (product.getProductType() == null || product.getProductType().trim().isEmpty()) {
            throw new IllegalArgumentException("产品类型不能为空");
        }
        productMapper.update(product);
    }

    @Transactional
    public void deleteProduct(Long id) {
        if (id == null) {
            throw new IllegalArgumentException("产品ID不能为空");
        }
        productMapper.logicalDelete(id);
    }

    @Transactional
    public com.n4monitor.entity.Device getOrCreateTemplateDevice(Long productId) {
        List<com.n4monitor.entity.Device> allDevices = deviceMapper.findAll();
        for (com.n4monitor.entity.Device d : allDevices) {
            if (("TEMPLATE_" + productId).equals(d.getDeviceCode())) {
                return d;
            }
        }
        
        Product product = productMapper.findByProductId(productId);
        if (product == null) {
            throw new IllegalArgumentException("产品不存在");
        }
        
        com.n4monitor.entity.Device templateDevice = new com.n4monitor.entity.Device();
        templateDevice.setProductId(productId);
        templateDevice.setDeviceName(product.getProductName() + "_模板");
        templateDevice.setDeviceCode("TEMPLATE_" + productId);
        templateDevice.setSysPath("/TEMPLATES/" + product.getProductName());
        templateDevice.setSysLevel(1);
        templateDevice.setSysParentId(null);
        templateDevice.setLocPath("/TEMPLATES/" + product.getProductName());
        templateDevice.setLocLevel(1);
        templateDevice.setLocParentId(null);
        templateDevice.setStatus(1);
        templateDevice.setIsDeleted(false);
        deviceMapper.insert(templateDevice);

        com.n4monitor.entity.Device existingRealDevice = null;
        for (com.n4monitor.entity.Device d : allDevices) {
            if (productId.equals(d.getProductId()) && !d.getDeviceCode().startsWith("TEMPLATE_")) {
                existingRealDevice = d;
                break;
            }
        }

        if (existingRealDevice != null) {
            List<com.n4monitor.entity.MonitorPoint> realPoints = monitorPointMapper.findByDeviceId(existingRealDevice.getDeviceId());
            if (!realPoints.isEmpty()) {
                java.util.List<com.n4monitor.entity.MonitorPoint> clonedPoints = new java.util.ArrayList<>();
                for (com.n4monitor.entity.MonitorPoint rp : realPoints) {
                    com.n4monitor.entity.MonitorPoint np = new com.n4monitor.entity.MonitorPoint();
                    np.setDeviceId(templateDevice.getDeviceId());
                    np.setRegisterId(rp.getRegisterId());
                    np.setPointName(rp.getPointName());
                    np.setPointCode(templateDevice.getDeviceId() + "_" + rp.getRegisterId());
                    np.setPointType(rp.getPointType());
                    np.setDataType(rp.getDataType());
                    np.setUnit(rp.getUnit());
                    np.setDisplayPrecision(rp.getDisplayPrecision());
                    np.setPointValue(rp.getPointValue());
                    np.setPointStatus(1);
                    clonedPoints.add(np);
                }
                monitorPointMapper.batchInsert(clonedPoints);
            }
        } else {
            com.n4monitor.entity.MonitorPoint defaultPoint = new com.n4monitor.entity.MonitorPoint();
            defaultPoint.setDeviceId(templateDevice.getDeviceId());
            defaultPoint.setRegisterId("191001");
            defaultPoint.setPointName("通信状态");
            defaultPoint.setPointCode(templateDevice.getDeviceId() + "_191001");
            defaultPoint.setPointType(1); // DI
            defaultPoint.setDataType(1);  // int
            defaultPoint.setUnit("无");
            defaultPoint.setDisplayPrecision(0);
            defaultPoint.setPointValue("1");
            defaultPoint.setPointStatus(1);
            monitorPointMapper.insert(defaultPoint);
        }
        return templateDevice;
    }
}
