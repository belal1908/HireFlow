import { TestBed } from '@angular/core/testing';
import { Router, UrlTree, provideRouter } from '@angular/router';
import { roleGuard } from './role.guard';
import { AuthService } from '../services/auth.service';
import { Role } from '../models/user.model';

describe('roleGuard', () => {
  let authServiceSpy: jasmine.SpyObj<AuthService>;
  let router: Router;

  beforeEach(() => {
    authServiceSpy = jasmine.createSpyObj('AuthService', ['isLoggedIn', 'hasRole']);
    TestBed.configureTestingModule({
      providers: [{ provide: AuthService, useValue: authServiceSpy }, provideRouter([])]
    });
    router = TestBed.inject(Router);
  });

  function runGuard(allowedRoles: Role[], url = '/admin/postings') {
    const guard = roleGuard(...allowedRoles);
    return TestBed.runInInjectionContext(() => guard({} as never, { url } as never));
  }

  it('redirects to /login when the user is not logged in at all', () => {
    authServiceSpy.isLoggedIn.and.returnValue(false);

    const result = runGuard(['ADMIN']) as UrlTree;

    expect(result instanceof UrlTree).toBeTrue();
    expect(router.serializeUrl(result)).toContain('/login');
    expect(authServiceSpy.hasRole).not.toHaveBeenCalled();
  });

  it('allows navigation when the user is logged in with an allowed role', () => {
    authServiceSpy.isLoggedIn.and.returnValue(true);
    authServiceSpy.hasRole.and.returnValue(true);

    const result = runGuard(['RECRUITER', 'ADMIN']);

    expect(result).toBeTrue();
    expect(authServiceSpy.hasRole).toHaveBeenCalledWith('RECRUITER', 'ADMIN');
  });

  it('blocks navigation and redirects to /forbidden when logged in with the wrong role', () => {
    authServiceSpy.isLoggedIn.and.returnValue(true);
    authServiceSpy.hasRole.and.returnValue(false);

    const result = runGuard(['ADMIN']) as UrlTree;

    expect(result instanceof UrlTree).toBeTrue();
    expect(router.serializeUrl(result)).toBe('/forbidden');
  });
});
