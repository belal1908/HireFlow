package com.hireflow.user.dto;

import com.hireflow.user.entity.Role;
import com.hireflow.user.entity.User;

import java.time.Instant;

public record UserResponse(Long id, String email, Role role, Instant createdAt) {

    public static UserResponse from(User user) {
        return new UserResponse(user.getId(), user.getEmail(), user.getRole(), user.getCreatedAt());
    }
}
