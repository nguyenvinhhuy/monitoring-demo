package com.monitoringdemo.order.client.dto;

/** Request body order-service gửi đi khi gọi {@code POST /api/products/{id}/reserve}. */
public record ReserveStockRequest(Integer quantity) {
}
