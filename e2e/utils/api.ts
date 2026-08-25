import { APIRequestContext, expect } from '@playwright/test';
import { API_URL } from './env';

export type Role = 'CANDIDATE' | 'RECRUITER' | 'ADMIN';
export type ApplicationStatus = 'APPLIED' | 'SCREENING' | 'INTERVIEW' | 'OFFER' | 'ACCEPTED' | 'REJECTED' | 'WITHDRAWN';

export interface AuthResponse {
  accessToken: string;
  refreshToken: string;
}

export interface PostingResponse {
  id: number;
  title: string;
  description: string | null;
  status: 'OPEN' | 'CLOSED';
  createdBy: number;
  createdAt: string;
}

export interface ApplicationResponse {
  id: number;
  candidateId: number;
  jobPostingId: number;
  status: ApplicationStatus;
  createdAt: string;
  updatedAt: string;
}

export function uniqueEmail(prefix: string): string {
  return `${prefix}-${Date.now()}-${Math.random().toString(36).slice(2, 8)}@hireflow.e2e`;
}

export const DEFAULT_PASSWORD = 'Password123!';

/** Self-registration; always creates a CANDIDATE server-side regardless of caller intent. */
export async function registerCandidate(
  request: APIRequestContext,
  email: string,
  password = DEFAULT_PASSWORD
): Promise<void> {
  const res = await request.post(`${API_URL}/api/auth/register`, { data: { email, password } });
  expect(res.status(), `register(${email}) failed: ${await res.text()}`).toBe(201);
}

export async function login(request: APIRequestContext, email: string, password = DEFAULT_PASSWORD): Promise<AuthResponse> {
  const res = await request.post(`${API_URL}/api/auth/login`, { data: { email, password } });
  expect(res.status(), `login(${email}) failed: ${await res.text()}`).toBe(200);
  return res.json();
}

export function authHeader(accessToken: string): { Authorization: string } {
  return { Authorization: `Bearer ${accessToken}` };
}

export async function createPosting(
  request: APIRequestContext,
  adminToken: string,
  title: string,
  description = 'Seeded by Playwright e2e'
): Promise<PostingResponse> {
  const res = await request.post(`${API_URL}/api/postings`, {
    headers: authHeader(adminToken),
    data: { title, description }
  });
  expect(res.status(), `createPosting failed: ${await res.text()}`).toBe(201);
  return res.json();
}

export async function apply(request: APIRequestContext, candidateToken: string, jobPostingId: number): Promise<ApplicationResponse> {
  const res = await request.post(`${API_URL}/api/applications`, {
    headers: authHeader(candidateToken),
    data: { jobPostingId }
  });
  expect(res.status(), `apply failed: ${await res.text()}`).toBe(201);
  return res.json();
}

export async function updateStatus(
  request: APIRequestContext,
  actorToken: string,
  applicationId: number,
  targetStatus: ApplicationStatus
): Promise<ApplicationResponse> {
  const res = await request.patch(`${API_URL}/api/applications/${applicationId}/status`, {
    headers: authHeader(actorToken),
    data: { targetStatus }
  });
  expect(res.status(), `updateStatus(${applicationId} -> ${targetStatus}) failed: ${await res.text()}`).toBe(200);
  return res.json();
}
