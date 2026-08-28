package com.hireflow.auth.dto;

import java.time.Instant;

/**
 * {@code resetToken}/{@code expiresAt} are both null when the email doesn't match an account -
 * this endpoint always returns 200 either way (see {@code AuthService#requestPasswordReset}'s
 * Javadoc for why, and why that still isn't enough to call this endpoint production-safe).
 */
public record PasswordResetResponse(String resetToken, Instant expiresAt) {
}
