package com.monitoringdemo.product.dto;

import com.monitoringdemo.product.domain.Product;
import java.math.BigDecimal;
import java.time.Instant;

public record ProductResponse(Long id, String name, BigDecimal price, Integer stockQuantity, Instant createdAt) {

    public static ProductResponse from(Product product) {
        return new ProductResponse(
                product.getId(), product.getName(), product.getPrice(), product.getStockQuantity(),
                product.getCreatedAt());
    }
}
