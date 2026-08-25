import { APIRequestContext } from '@playwright/test';
import { AuthResponse, DEFAULT_PASSWORD, Role, login, registerCandidate, uniqueEmail } from './api';
import { promoteUserRole } from './db';

export interface SeededUser {
  email: string;
  password: string;
  role: Role;
  auth: AuthResponse;
}

/** Registers a fresh CANDIDATE (the only self-service path) and logs in. */
export async function seedCandidate(request: APIRequestContext, prefix = 'candidate'): Promise<SeededUser> {
  const email = uniqueEmail(prefix);
  await registerCandidate(request, email, DEFAULT_PASSWORD);
  const auth = await login(request, email, DEFAULT_PASSWORD);
  return { email, password: DEFAULT_PASSWORD, role: 'CANDIDATE', auth };
}

/**
 * Registers a CANDIDATE, then promotes it to RECRUITER/ADMIN directly in Postgres (the same
 * `UPDATE users SET role = ...` approach the README documents), then logs in again so the JWT
 * actually carries the new role — the role is baked into the token at login time, so an old
 * token from before the promotion would still say CANDIDATE.
 */
export async function seedPrivilegedUser(
  request: APIRequestContext,
  role: 'RECRUITER' | 'ADMIN',
  prefix = role.toLowerCase()
): Promise<SeededUser> {
  const email = uniqueEmail(prefix);
  await registerCandidate(request, email, DEFAULT_PASSWORD);
  await promoteUserRole(email, role);
  const auth = await login(request, email, DEFAULT_PASSWORD);
  return { email, password: DEFAULT_PASSWORD, role, auth };
}
