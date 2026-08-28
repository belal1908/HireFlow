/** Mirrors com.hireflow.user.entity.Role exactly. */
export type Role = 'CANDIDATE' | 'RECRUITER' | 'ADMIN';

/** Mirrors com.hireflow.user.dto.UserResponse (returned by POST /api/auth/register). */
export interface UserResponse {
  id: number;
  email: string;
  role: Role;
  createdAt: string;
  emailVerified: boolean;
}

/**
 * Mirrors com.hireflow.admin.dto.CreateUserRequest (body for POST /api/admin/users).
 * `role` is restricted to RECRUITER/ADMIN in the UI — CANDIDATE is rejected server-side with
 * 400, since that account type is only ever created via self-registration.
 */
export interface CreateUserRequest {
  email: string;
  password: string;
  role: Exclude<Role, 'CANDIDATE'>;
}

/**
 * Claims embedded in the access-token JWT by JwtService#generateAccessToken:
 * sub (user id), email, role, iat, exp. Decoded client-side purely for display/routing —
 * the backend re-validates the token's signature on every request, so nothing here is
 * trusted for authorization decisions beyond driving the UI.
 */
export interface DecodedAccessToken {
  sub: string;
  email: string;
  role: Role;
  emailVerified: boolean;
  iat: number;
  exp: number;
}

/** Convenience shape derived from a decoded token, used throughout the app. */
export interface CurrentUser {
  id: number;
  email: string;
  role: Role;
  emailVerified: boolean;
}
