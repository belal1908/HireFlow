import { Injectable, signal } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable, finalize, shareReplay, tap, throwError } from 'rxjs';
import { environment } from '../../../environments/environment';
import { AuthResponse, LoginRequest, RefreshRequest, RegisterRequest } from '../models/auth.model';
import { UserResponse } from '../models/user.model';
import { CurrentUser, DecodedAccessToken, Role } from '../models/user.model';

/**
 * Holds the access + refresh tokens in memory only (plain instance fields, never
 * localStorage/sessionStorage). This is deliberate: an XSS payload that can execute arbitrary
 * JS on the page can always call fetch()/XHR itself, but it cannot read anything out of
 * localStorage that was never written there in the first place. The trade-off is that a full
 * page reload loses the session (no token survives a refresh) — for this portfolio-scope app
 * that trade-off is accepted and documented in the README rather than reached for
 * localStorage as a workaround. A production system would instead put the refresh token in an
 * httpOnly cookie set by the backend, which needs a backend change out of scope for Week 2.
 */
@Injectable({ providedIn: 'root' })
export class AuthService {
  private accessToken: string | null = null;
  private refreshTokenValue: string | null = null;

  /** Shared in-flight refresh call so concurrent 401s trigger exactly one /api/auth/refresh request. */
  private refreshInFlight$: Observable<AuthResponse> | null = null;

  /** Current user, derived from the access token's claims. Signal so templates/guards react to it. */
  readonly currentUser = signal<CurrentUser | null>(null);

  constructor(private http: HttpClient) {}

  register(request: RegisterRequest): Observable<UserResponse> {
    return this.http.post<UserResponse>(`${environment.apiUrl}/api/auth/register`, request);
  }

  login(request: LoginRequest): Observable<AuthResponse> {
    return this.http
      .post<AuthResponse>(`${environment.apiUrl}/api/auth/login`, request)
      .pipe(tap((response) => this.setSession(response)));
  }

  /**
   * Performs a token refresh, sharing a single in-flight HTTP call across any number of
   * concurrent callers (the interceptor calls this once per failed request; without sharing,
   * N simultaneous 401s would fire N refresh calls and rotate the refresh token N times,
   * invalidating all but the last one since the backend burns each refresh token on use).
   */
  refreshAccessToken(): Observable<AuthResponse> {
    if (this.refreshInFlight$) {
      return this.refreshInFlight$;
    }
    const refreshToken = this.refreshTokenValue;
    if (!refreshToken) {
      return throwError(() => new Error('No refresh token available'));
    }
    const body: RefreshRequest = { refreshToken };
    this.refreshInFlight$ = this.http.post<AuthResponse>(`${environment.apiUrl}/api/auth/refresh`, body).pipe(
      tap((response) => this.setSession(response)),
      shareReplay(1),
      finalize(() => {
        this.refreshInFlight$ = null;
      })
    );
    return this.refreshInFlight$;
  }

  logout(): void {
    this.accessToken = null;
    this.refreshTokenValue = null;
    this.refreshInFlight$ = null;
    this.currentUser.set(null);
  }

  getAccessToken(): string | null {
    return this.accessToken;
  }

  isLoggedIn(): boolean {
    return this.accessToken !== null && this.currentUser() !== null;
  }

  hasRole(...roles: Role[]): boolean {
    const user = this.currentUser();
    return user !== null && roles.includes(user.role);
  }

  private setSession(response: AuthResponse): void {
    this.accessToken = response.accessToken;
    this.refreshTokenValue = response.refreshToken;
    this.currentUser.set(this.decodeUser(response.accessToken));
  }

  private decodeUser(token: string): CurrentUser | null {
    try {
      const payload = token.split('.')[1];
      // JWT uses base64url; atob() needs standard base64, so translate the alphabet first.
      const base64 = payload.replace(/-/g, '+').replace(/_/g, '/');
      const decoded: DecodedAccessToken = JSON.parse(atob(base64));
      return { id: Number(decoded.sub), email: decoded.email, role: decoded.role };
    } catch {
      return null;
    }
  }
}
