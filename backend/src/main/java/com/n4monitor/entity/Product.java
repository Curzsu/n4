package com.n4monitor.entity;

import lombok.Data;

@Data
public class Product {
    private Long productId;
    private String productName;
    private String productType;
    private String model;
    private String manufacturer;
    private String manuDate;
    private Boolean isDeleted;
    private java.time.LocalDateTime createTime;
    private java.time.LocalDateTime updateTime;
}
