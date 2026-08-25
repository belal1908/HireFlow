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
