package com.hireflow.auth.dto;

import jakarta.validation.constraints.NotBlank;

public record EmailVerificationConfirmRequest(@NotBlank String token) {
}
