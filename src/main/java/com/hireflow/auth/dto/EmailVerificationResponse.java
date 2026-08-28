package com.hireflow.auth.dto;

import java.time.Instant;

/**
 * {@code verificationToken}/{@code expiresAt} are null when the email doesn't match an account
 * OR when it's already verified - both collapse to the same "nothing to do" response so this
 * endpoint doesn't reveal which case it was. Same dev-mode tradeoff as
 * {@code PasswordResetResponse}: a real deployment emails this token instead of returning it.
 */
public record EmailVerificationResponse(String verificationToken, Instant expiresAt) {
}
