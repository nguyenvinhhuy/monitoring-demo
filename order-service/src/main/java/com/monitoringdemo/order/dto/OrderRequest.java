package com.monitoringdemo.order.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

public record OrderRequest(
        @NotNull(message = "userId không được để trống") Long userId,
        @NotNull(message = "productId không được để trống") Long productId,
        @NotNull(message = "quantity không được để trống") @Min(value = 1, message = "quantity phải >= 1") Integer quantity) {
}
