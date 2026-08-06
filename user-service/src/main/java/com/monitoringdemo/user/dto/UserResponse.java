package com.monitoringdemo.user.dto;

import com.monitoringdemo.user.domain.User;
import java.time.Instant;

public record UserResponse(Long id, String fullName, String email, Instant createdAt) {

    public static UserResponse from(User user) {
        return new UserResponse(user.getId(), user.getFullName(), user.getEmail(), user.getCreatedAt());
    }
}
