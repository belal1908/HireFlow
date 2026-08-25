import { inject } from '@angular/core';
import { CanActivateFn, Router } from '@angular/router';
import { AuthService } from '../services/auth.service';

/** Sends `/` to the right per-role landing page (or /login if not authenticated). */
export const homeRedirectGuard: CanActivateFn = () => {
  const authService = inject(AuthService);
  const router = inject(Router);

  const user = authService.currentUser();
  if (!user) {
    return router.createUrlTree(['/login']);
  }
  switch (user.role) {
    case 'CANDIDATE':
      return router.createUrlTree(['/postings']);
    case 'RECRUITER':
      return router.createUrlTree(['/recruiter/applications']);
    case 'ADMIN':
      return router.createUrlTree(['/admin/postings']);
  }
};
