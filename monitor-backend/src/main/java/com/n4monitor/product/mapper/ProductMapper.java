package com.n4monitor.product.mapper;

import com.n4monitor.product.entity.Product;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

@Mapper
public interface ProductMapper {

    List<Product> findAll();

    Product findByProductId(@Param("productId") Long productId);

    int insert(Product product);

    int update(Product product);

    int logicalDelete(@Param("productId") Long productId);
}
