package com.monitoringdemo.order.client.dto;

import java.math.BigDecimal;

/** Hình dạng dữ liệu product-service trả về khi order-service gọi {@code GET/POST /api/products/...}. */
public record ProductResponse(Long id, String name, BigDecimal price, Integer stockQuantity) {
}
