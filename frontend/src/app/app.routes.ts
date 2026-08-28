import { Routes } from '@angular/router';
import { authGuard } from './core/guards/auth.guard';
import { roleGuard } from './core/guards/role.guard';

/**
 * Route table for the ApplyTrack-styled redesign. The old per-role pages
 * (`/postings`, `/applications/mine`, `/recruiter/applications`, `/admin/postings`) are gone;
 * every authenticated user now lands on the same five routes — `/`, `/applications`,
 * `/state-machine`, `/postings`, `/settings` — each rendering role-adaptive content instead of a
 * role-specific page. `homeRedirectGuard` (which used to send `/` to a per-role landing page) is
 * gone with it: `/` is now a real page (Overview) for every role, so authGuard alone is enough.
 *
 * All five, plus the admin-only `/admin/users`, are children of the `AppShellComponent` layout
 * route so the sidebar/header chrome mounts exactly once and survives navigation between them.
 * `roleGuard` still protects the one route that's genuinely role-exclusive: `/admin/users`.
 */
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
    path: '',
    canActivate: [authGuard],
    loadComponent: () => import('./shared/shell/app-shell.component').then((m) => m.AppShellComponent),
    children: [
      {
        path: '',
        pathMatch: 'full',
        loadComponent: () => import('./features/overview/overview.component').then((m) => m.OverviewComponent)
      },
      {
        path: 'applications',
        loadComponent: () => import('./features/applications/applications.component').then((m) => m.ApplicationsComponent)
      },
      {
        path: 'state-machine',
        loadComponent: () => import('./features/state-machine/state-machine.component').then((m) => m.StateMachineComponent)
      },
      {
        path: 'postings',
        loadComponent: () => import('./features/postings/postings.component').then((m) => m.PostingsComponent)
      },
      {
        path: 'settings',
        loadComponent: () => import('./features/settings/settings.component').then((m) => m.SettingsComponent)
      },
      {
        path: 'admin/users',
        canActivate: [roleGuard('ADMIN')],
        loadComponent: () => import('./features/admin/users-admin/users-admin.component').then((m) => m.UsersAdminComponent)
      }
    ]
  },
  { path: '**', redirectTo: 'login' }
];
