/** Mirrors com.hireflow.auth.dto.* exactly. */

export interface RegisterRequest {
  email: string;
  password: string;
}

export interface LoginRequest {
  email: string;
  password: string;
}

export interface RefreshRequest {
  refreshToken: string;
}

/** Returned by POST /api/auth/login and POST /api/auth/refresh. */
export interface AuthResponse {
  accessToken: string;
  refreshToken: string;
}

export interface PasswordResetRequest {
  email: string;
}

/**
 * `resetToken`/`expiresAt` are both null when the email doesn't match an account. When present,
 * `resetToken` is a dev-mode stand-in for what a real deployment would email instead — see the
 * README's "Password reset" section and ForgotPasswordComponent's class comment.
 */
export interface PasswordResetResponse {
  resetToken: string | null;
  expiresAt: string | null;
}

export interface PasswordResetConfirmRequest {
  token: string;
  newPassword: string;
}
