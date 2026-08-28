import { Component } from '@angular/core';
import { Router, RouterLink, RouterLinkActive, RouterOutlet } from '@angular/router';
import { MatIconModule } from '@angular/material/icon';
import { AuthService } from '../../core/services/auth.service';
import { UiStateService } from '../../core/state/ui-state.service';

interface NavItem {
  path: string;
  label: string;
  icon: string;
  exact: boolean;
}

/**
 * The authenticated app shell: sidebar (logo, nav, "enforced server-side" note, user row) +
 * header (sync indicator, "Inspect permissions" toggle) + a router-outlet for the page content.
 * Every authenticated route (`/`, `/applications`, `/state-machine`, `/postings`, `/settings`,
 * `/admin/users`) renders as a child of the shell route so this chrome is never re-mounted on
 * navigation — see app.routes.ts.
 *
 * Replaces the old top `<app-nav>` mat-toolbar entirely; unlike that bar, the role tabs shown in
 * the design's header prototype are deliberately NOT reproduced here — the README is explicit
 * that "the role control is a demo affordance. In production it does not exist; role is read
 * from the token," and this app always reads the role from the real JWT via AuthService.
 */
@Component({
  selector: 'app-shell',
  standalone: true,
  imports: [RouterLink, RouterLinkActive, RouterOutlet, MatIconModule],
  templateUrl: './app-shell.component.html',
  styleUrl: './app-shell.component.css'
})
export class AppShellComponent {
  readonly navItems: NavItem[] = [
    { path: '/', label: 'Overview', icon: 'dashboard', exact: true },
    { path: '/applications', label: 'Applications', icon: 'description', exact: false },
    { path: '/state-machine', label: 'State machine', icon: 'account_tree', exact: false },
    { path: '/postings', label: 'Job postings', icon: 'work', exact: false },
    { path: '/settings', label: 'Settings', icon: 'settings', exact: false }
  ];

  constructor(
    public authService: AuthService,
    public uiState: UiStateService,
    private router: Router
  ) {}

  get roleInitial(): string {
    return this.authService.currentUser()?.role.charAt(0) ?? '?';
  }

  logout(): void {
    this.authService.logout();
    this.router.navigateByUrl('/login');
  }
}
