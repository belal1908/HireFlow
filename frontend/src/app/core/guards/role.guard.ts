import { inject } from '@angular/core';
import { CanActivateFn, Router } from '@angular/router';
import { AuthService } from '../services/auth.service';
import { Role } from '../models/user.model';

/**
 * Guard factory: `roleGuard('ADMIN')` or `roleGuard('RECRUITER', 'ADMIN')`.
 * Not logged in -> /login. Logged in but wrong role -> /forbidden. This is a real router guard
 * (blocks navigation/activation), not just conditionally-hidden UI — a direct URL hit by a
 * wrong-role user never activates the component.
 */
export function roleGuard(...allowedRoles: Role[]): CanActivateFn {
  return (_route, state) => {
    const authService = inject(AuthService);
    const router = inject(Router);

    if (!authService.isLoggedIn()) {
      return router.createUrlTree(['/login'], { queryParams: { redirect: state.url } });
    }
    if (authService.hasRole(...allowedRoles)) {
      return true;
    }
    return router.createUrlTree(['/forbidden']);
  };
}
