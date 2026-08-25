import { TestBed } from '@angular/core/testing';
import { HttpClient, provideHttpClient, withInterceptors } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { Router, provideRouter } from '@angular/router';
import { authInterceptor } from './auth.interceptor';
import { AuthService } from '../services/auth.service';
import { environment } from '../../../environments/environment';

/** Builds a syntactically-valid (unsigned) JWT so AuthService's client-side claim decoding works. */
function fakeAccessToken(claims: Record<string, unknown> = {}): string {
  const payload = btoa(
    JSON.stringify({
      sub: '1',
      email: 'user@example.com',
      role: 'CANDIDATE',
      iat: Math.floor(Date.now() / 1000),
      exp: Math.floor(Date.now() / 1000) + 900,
      ...claims
    })
  );
  return `fake-header.${payload}.fake-signature`;
}

describe('authInterceptor', () => {
  let httpClient: HttpClient;
  let httpMock: HttpTestingController;
  let authService: AuthService;
  let router: Router;

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [
        provideHttpClient(withInterceptors([authInterceptor])),
        provideHttpClientTesting(),
        provideRouter([])
      ]
    });
    httpClient = TestBed.inject(HttpClient);
    httpMock = TestBed.inject(HttpTestingController);
    authService = TestBed.inject(AuthService);
    router = TestBed.inject(Router);
    spyOn(router, 'navigate');
  });

  afterEach(() => {
    httpMock.verify();
  });

  function login(accessToken: string, refreshToken: string): void {
    authService.login({ email: 'user@example.com', password: 'password123' }).subscribe();
    httpMock.expectOne(`${environment.apiUrl}/api/auth/login`).flush({ accessToken, refreshToken });
  }

  it('attaches Authorization: Bearer <token> to outgoing API requests once logged in', () => {
    login(fakeAccessToken(), 'refresh-token-1');

    httpClient.get(`${environment.apiUrl}/api/postings`).subscribe();
    const req = httpMock.expectOne(`${environment.apiUrl}/api/postings`);

    expect(req.request.headers.get('Authorization')).toBe(`Bearer ${authService.getAccessToken()}`);
    req.flush([]);
  });

  it('does not attach a bearer token to /api/auth/* requests (they carry their own credentials)', () => {
    login(fakeAccessToken(), 'refresh-token-1');

    authService.refreshAccessToken().subscribe();
    const req = httpMock.expectOne(`${environment.apiUrl}/api/auth/refresh`);

    expect(req.request.headers.has('Authorization')).toBeFalse();
    req.flush({ accessToken: fakeAccessToken(), refreshToken: 'refresh-token-2' });
  });

  it('does not attempt a refresh for a 401 returned by an auth endpoint itself', () => {
    let caught: unknown;
    authService.login({ email: 'user@example.com', password: 'wrong' }).subscribe({
      error: (err) => (caught = err)
    });

    const req = httpMock.expectOne(`${environment.apiUrl}/api/auth/login`);
    req.flush({ status: 401, error: 'Unauthorized', message: 'Invalid email or password' }, { status: 401, statusText: 'Unauthorized' });

    httpMock.expectNone(`${environment.apiUrl}/api/auth/refresh`);
    expect(caught).toBeTruthy();
  });

  it('on a 401, silently refreshes and retries the original request once, transparently to the caller', () => {
    login(fakeAccessToken({ sub: '1' }), 'refresh-token-old');
    let result: unknown;

    httpClient.get(`${environment.apiUrl}/api/postings`).subscribe((r) => (result = r));
    const firstAttempt = httpMock.expectOne(`${environment.apiUrl}/api/postings`);
    firstAttempt.flush({ message: 'expired' }, { status: 401, statusText: 'Unauthorized' });

    const refreshReq = httpMock.expectOne(`${environment.apiUrl}/api/auth/refresh`);
    expect(refreshReq.request.body).toEqual({ refreshToken: 'refresh-token-old' });
    const newAccessToken = fakeAccessToken({ sub: '1' });
    refreshReq.flush({ accessToken: newAccessToken, refreshToken: 'refresh-token-new' });

    const retry = httpMock.expectOne(`${environment.apiUrl}/api/postings`);
    expect(retry.request.headers.get('Authorization')).toBe(`Bearer ${newAccessToken}`);
    retry.flush([{ id: 1, title: 'Engineer' }]);

    expect(result).toEqual([{ id: 1, title: 'Engineer' }]);
  });

  it('shares a single in-flight refresh call across concurrent 401s instead of firing one per request', () => {
    login(fakeAccessToken(), 'refresh-token-old');
    let result1: unknown;
    let result2: unknown;

    httpClient.get(`${environment.apiUrl}/api/postings`).subscribe((r) => (result1 = r));
    httpClient.get(`${environment.apiUrl}/api/applications/mine`).subscribe((r) => (result2 = r));

    const req1 = httpMock.expectOne(`${environment.apiUrl}/api/postings`);
    const req2 = httpMock.expectOne(`${environment.apiUrl}/api/applications/mine`);
    req1.flush({ message: 'expired' }, { status: 401, statusText: 'Unauthorized' });
    req2.flush({ message: 'expired' }, { status: 401, statusText: 'Unauthorized' });

    // Exactly one refresh call must be pending, even though two requests failed with 401.
    const refreshReq = httpMock.expectOne(`${environment.apiUrl}/api/auth/refresh`);
    const newAccessToken = fakeAccessToken();
    refreshReq.flush({ accessToken: newAccessToken, refreshToken: 'refresh-token-new' });

    const retry1 = httpMock.expectOne(`${environment.apiUrl}/api/postings`);
    const retry2 = httpMock.expectOne(`${environment.apiUrl}/api/applications/mine`);
    retry1.flush([{ id: 1 }]);
    retry2.flush([{ id: 2 }]);

    expect(result1).toEqual([{ id: 1 }]);
    expect(result2).toEqual([{ id: 2 }]);
    // httpMock.verify() in afterEach additionally proves no stray/duplicate refresh calls were made.
  });

  it('logs out and redirects to /login when the refresh call itself fails', () => {
    login(fakeAccessToken(), 'refresh-token-old');
    let caught: unknown;

    httpClient.get(`${environment.apiUrl}/api/postings`).subscribe({ error: (err) => (caught = err) });
    const req = httpMock.expectOne(`${environment.apiUrl}/api/postings`);
    req.flush({ message: 'expired' }, { status: 401, statusText: 'Unauthorized' });

    const refreshReq = httpMock.expectOne(`${environment.apiUrl}/api/auth/refresh`);
    refreshReq.flush(
      { message: 'Refresh token has expired or already been used' },
      { status: 401, statusText: 'Unauthorized' }
    );

    expect(authService.isLoggedIn()).toBeFalse();
    expect(router.navigate).toHaveBeenCalledWith(['/login']);
    expect(caught).toBeTruthy();
  });
});
