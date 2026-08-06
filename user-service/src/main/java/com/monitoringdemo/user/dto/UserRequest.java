package com.monitoringdemo.user.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;

public record UserRequest(
        @NotBlank(message = "fullName không được để trống") String fullName,
        @NotBlank(message = "email không được để trống") @Email(message = "email không hợp lệ") String email) {
}
