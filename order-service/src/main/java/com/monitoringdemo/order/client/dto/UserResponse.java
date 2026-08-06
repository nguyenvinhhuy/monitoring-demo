package com.monitoringdemo.order.client.dto;

/** Hình dạng dữ liệu user-service trả về khi order-service gọi {@code GET /api/users/{id}}. */
public record UserResponse(Long id, String fullName, String email) {
}
