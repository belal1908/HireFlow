import { HttpErrorResponse, HttpInterceptorFn, HttpRequest } from '@angular/common/http';
import { inject } from '@angular/core';
import { Router } from '@angular/router';
import { catchError, switchMap, throwError } from 'rxjs';
import { AuthService } from '../services/auth.service';

const AUTH_ENDPOINT = '/api/auth/';

function withAuthHeader(req: HttpRequest<unknown>, token: string | null): HttpRequest<unknown> {
  if (!token) {
    return req;
  }
  return req.clone({ setHeaders: { Authorization: `Bearer ${token}` } });
}

/**
 * Functional HttpInterceptor (Angular 15+ style):
 *  - Attaches `Authorization: Bearer <accessToken>` to every request except the public auth
 *    endpoints (register/login/refresh don't need it, and refresh must never carry a stale
 *    access token to avoid confusing the server).
 *  - On a 401 from a non-auth request, attempts exactly one silent refresh (shared across
 *    concurrent requests by AuthService.refreshAccessToken) and retries the original request
 *    once with the new token.
 *  - If the refresh itself fails (expired/revoked/reused refresh token), logs the user out and
 *    redirects to /login instead of looping.
 */
export const authInterceptor: HttpInterceptorFn = (req, next) => {
  const authService = inject(AuthService);
  const router = inject(Router);

  const isAuthEndpoint = req.url.includes(AUTH_ENDPOINT);
  const outgoing = isAuthEndpoint ? req : withAuthHeader(req, authService.getAccessToken());

  return next(outgoing).pipe(
    catchError((error: unknown) => {
      const isUnauthorized = error instanceof HttpErrorResponse && error.status === 401;
      if (!isUnauthorized || isAuthEndpoint) {
        return throwError(() => error);
      }

      return authService.refreshAccessToken().pipe(
        switchMap((tokens) => next(withAuthHeader(req, tokens.accessToken))),
        catchError((refreshError: unknown) => {
          authService.logout();
          router.navigate(['/login']);
          return throwError(() => refreshError);
        })
      );
    })
  );
};
