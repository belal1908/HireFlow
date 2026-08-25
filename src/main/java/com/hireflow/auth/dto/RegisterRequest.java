package com.hireflow.auth.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * No {@code role} field on purpose: self-registration always creates a CANDIDATE. Even if a
 * client sends an extra "role" property in the JSON body, it is ignored (see application.yml's
 * {@code spring.jackson.deserialization.fail-on-unknown-properties: false}) and the server
 * always forces CANDIDATE - see AuthService#register.
 */
public record RegisterRequest(
        @NotBlank @Email String email,
        @NotBlank @Size(min = 8, max = 100) String password
) {
}
