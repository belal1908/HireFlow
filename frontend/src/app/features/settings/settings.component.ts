import { Component, computed, inject, signal } from '@angular/core';
import { RouterLink } from '@angular/router';
import { AuthService } from '../../core/services/auth.service';
import { ApplicationStatus } from '../../core/models/application.model';
import { Role } from '../../core/models/user.model';
import { validateTransition } from '../../shared/transition/transition-validator';
import { extractErrorMessage } from '../../core/utils/api-error.util';

interface MatrixRow {
  transition: string;
  from: ApplicationStatus;
  to: ApplicationStatus;
  candidate: boolean;
  recruiter: boolean;
  admin: boolean;
}

/**
 * `/settings` — new page (README section 8): a read-only Profile card and a Transition matrix
 * card whose ALLOW/DENY cells are computed live from the same ported `validateTransition()` used
 * on the Applications page and State machine page — not hardcoded to the design's example rows,
 * per the task's explicit instruction. Also the new home for ADMIN's user management (the
 * pre-redesign `UsersAdminComponent`, a real PRD stretch-goal feature outside this design's
 * documented scope): rather than embed that whole table inline here, this page links to it as a
 * still-guarded `/admin/users` route, restyled to the new tokens — lower-risk than folding a
 * second data-fetching table into this page, and the feature keeps its own guard.
 *
 * Also hosts the email-verification prompt for an unverified account. Same dev-mode tradeoff as
 * ForgotPasswordComponent: no SMTP infrastructure exists, so the verification token is shown
 * directly on screen instead of being emailed, in an explicit "DEV MODE" callout — but since the
 * user requesting it is already authenticated (unlike password reset), the whole flow fits on
 * this one page instead of needing a separate confirm page reached via a link.
 */
@Component({
  selector: 'app-settings',
  standalone: true,
  imports: [RouterLink],
  templateUrl: './settings.component.html',
  styleUrl: './settings.component.css'
})
export class SettingsComponent {
  readonly authService = inject(AuthService);

  readonly isAdmin = computed(() => this.authService.currentUser()?.role === 'ADMIN');

  readonly requestingVerification = signal(false);
  readonly confirmingVerification = signal(false);
  readonly verificationToken = signal<string | null>(null);
  readonly verificationError = signal<string | null>(null);
  readonly verificationConfirmed = signal(false);

  sendVerificationToken(): void {
    const email = this.authService.currentUser()?.email;
    if (!email) {
      return;
    }
    this.requestingVerification.set(true);
    this.verificationError.set(null);
    this.authService.requestEmailVerification({ email }).subscribe({
      next: (response) => {
        this.requestingVerification.set(false);
        this.verificationToken.set(response.verificationToken);
      },
      error: (err) => {
        this.requestingVerification.set(false);
        this.verificationError.set(extractErrorMessage(err));
      }
    });
  }

  confirmVerification(): void {
    const token = this.verificationToken();
    if (!token) {
      return;
    }
    this.confirmingVerification.set(true);
    this.verificationError.set(null);
    this.authService.confirmEmailVerification({ token }).subscribe({
      next: () => {
        this.confirmingVerification.set(false);
        this.verificationToken.set(null);
        this.verificationConfirmed.set(true);
        // The JWT's emailVerified claim was baked in at login/refresh time - refresh now so the
        // "Verified" pill flips without forcing a full re-login.
        this.authService.refreshAccessToken().subscribe();
      },
      error: (err) => {
        this.confirmingVerification.set(false);
        this.verificationError.set(extractErrorMessage(err));
      }
    });
  }

  readonly matrix = computed<MatrixRow[]>(() => {
    const rows: Array<{ label: string; from: ApplicationStatus; to: ApplicationStatus }> = [
      { label: 'APPLIED → SCREENING', from: 'APPLIED', to: 'SCREENING' },
      { label: 'SCREENING → INTERVIEW', from: 'SCREENING', to: 'INTERVIEW' },
      { label: 'INTERVIEW → OFFER', from: 'INTERVIEW', to: 'OFFER' },
      { label: 'OFFER → ACCEPTED', from: 'OFFER', to: 'ACCEPTED' },
      { label: '* → REJECTED', from: 'APPLIED', to: 'REJECTED' },
      { label: '* → WITHDRAWN', from: 'APPLIED', to: 'WITHDRAWN' },
      { label: 'TERMINAL → *', from: 'ACCEPTED', to: 'WITHDRAWN' }
    ];
    const roles: Role[] = ['CANDIDATE', 'RECRUITER', 'ADMIN'];
    return rows.map((row) => {
      const [candidate, recruiter, admin] = roles.map((role) => validateTransition(row.from, row.to, role).ok);
      return { transition: row.label, from: row.from, to: row.to, candidate, recruiter, admin };
    });
  });
}
