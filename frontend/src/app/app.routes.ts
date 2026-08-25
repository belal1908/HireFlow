import { Routes } from '@angular/router';
import { authGuard } from './core/guards/auth.guard';
import { roleGuard } from './core/guards/role.guard';
import { homeRedirectGuard } from './core/guards/home-redirect.guard';

export const routes: Routes = [
  {
    path: 'login',
    loadComponent: () => import('./features/auth/login/login.component').then((m) => m.LoginComponent)
  },
  {
    path: 'register',
    loadComponent: () => import('./features/auth/register/register.component').then((m) => m.RegisterComponent)
  },
  {
    path: 'forbidden',
    loadComponent: () => import('./shared/forbidden/forbidden.component').then((m) => m.ForbiddenComponent)
  },
  {
    path: 'postings',
    canActivate: [authGuard, roleGuard('CANDIDATE')],
    loadComponent: () =>
      import('./features/candidate/postings-list/postings-list.component').then((m) => m.PostingsListComponent)
  },
  {
    path: 'applications/mine',
    canActivate: [authGuard, roleGuard('CANDIDATE')],
    loadComponent: () =>
      import('./features/candidate/my-applications/my-applications.component').then((m) => m.MyApplicationsComponent)
  },
  {
    path: 'recruiter/applications',
    canActivate: [authGuard, roleGuard('RECRUITER', 'ADMIN')],
    loadComponent: () =>
      import('./features/recruiter/applications-dashboard/applications-dashboard.component').then(
        (m) => m.ApplicationsDashboardComponent
      )
  },
  {
    path: 'admin/postings',
    canActivate: [authGuard, roleGuard('ADMIN')],
    loadComponent: () =>
      import('./features/admin/postings-admin/postings-admin.component').then((m) => m.PostingsAdminComponent)
  },
  {
    path: 'admin/users',
    canActivate: [authGuard, roleGuard('ADMIN')],
    loadComponent: () =>
      import('./features/admin/users-admin/users-admin.component').then((m) => m.UsersAdminComponent)
  },
  {
    path: '',
    pathMatch: 'full',
    canActivate: [homeRedirectGuard],
    children: []
  },
  { path: '**', redirectTo: 'login' }
];
