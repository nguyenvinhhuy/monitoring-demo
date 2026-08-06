package com.monitoringdemo.order.dto;

import com.monitoringdemo.order.domain.Order;
import com.monitoringdemo.order.domain.OrderStatus;
import java.math.BigDecimal;
import java.time.Instant;

public record OrderResponse(
        Long id,
        Long userId,
        Long productId,
        Integer quantity,
        BigDecimal totalPrice,
        OrderStatus status,
        Instant createdAt) {

    public static OrderResponse from(Order order) {
        return new OrderResponse(
                order.getId(),
                order.getUserId(),
                order.getProductId(),
                order.getQuantity(),
                order.getTotalPrice(),
                order.getStatus(),
                order.getCreatedAt());
    }
}
