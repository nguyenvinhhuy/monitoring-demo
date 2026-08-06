package com.monitoringdemo.product.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;

public record ProductRequest(
        @NotBlank(message = "name không được để trống") String name,
        @NotNull(message = "price không được để trống") @DecimalMin(value = "0.0", inclusive = true, message = "price phải >= 0")
                BigDecimal price,
        @NotNull(message = "stockQuantity không được để trống") @Min(value = 0, message = "stockQuantity phải >= 0")
                Integer stockQuantity) {
}
