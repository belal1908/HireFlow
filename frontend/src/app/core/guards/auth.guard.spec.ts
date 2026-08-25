import { TestBed } from '@angular/core/testing';
import { Router, UrlTree, provideRouter } from '@angular/router';
import { authGuard } from './auth.guard';
import { AuthService } from '../services/auth.service';

describe('authGuard', () => {
  let authServiceSpy: jasmine.SpyObj<AuthService>;
  let router: Router;

  beforeEach(() => {
    authServiceSpy = jasmine.createSpyObj('AuthService', ['isLoggedIn']);
    TestBed.configureTestingModule({
      providers: [{ provide: AuthService, useValue: authServiceSpy }, provideRouter([])]
    });
    router = TestBed.inject(Router);
  });

  function runGuard(url: string) {
    return TestBed.runInInjectionContext(() => authGuard({} as never, { url } as never));
  }

  it('allows navigation when the user is logged in', () => {
    authServiceSpy.isLoggedIn.and.returnValue(true);

    const result = runGuard('/postings');

    expect(result).toBeTrue();
  });

  it('blocks navigation and redirects to /login (preserving the attempted URL) when not logged in', () => {
    authServiceSpy.isLoggedIn.and.returnValue(false);

    const result = runGuard('/postings') as UrlTree;

    expect(result instanceof UrlTree).toBeTrue();
    expect(router.serializeUrl(result)).toBe('/login?redirect=%2Fpostings');
  });
});
