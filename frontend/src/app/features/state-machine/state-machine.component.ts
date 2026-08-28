import { Component, OnInit, computed, inject, signal } from '@angular/core';
import { ApplicationService } from '../../core/services/application.service';
import { AuthService } from '../../core/services/auth.service';
import { UiStateService } from '../../core/state/ui-state.service';
import { ApplicationResponse, ApplicationStatus } from '../../core/models/application.model';
import { extractErrorMessage } from '../../core/utils/api-error.util';
import { STATUS_ORDER, TERMINAL_ORDER } from '../../shared/transition/transition-validator';

/**
 * `/state-machine` — new page, didn't exist before this redesign. A visual graph of the happy
 * path (APPLIED → SCREENING → INTERVIEW → OFFER → ACCEPTED) plus the two terminal branches
 * (REJECTED, WITHDRAWN), mirroring the same rules `TransitionValidator` enforces server-side and
 * `shared/transition/transition-validator.ts` mirrors client-side.
 *
 * Node counts reflect the current role's visible-application scope (same `scoped()` fetch as
 * Overview/Applications). The "LIT: <STATUS>" indicator mirrors whichever application was most
 * recently selected on the Applications page (see UiStateService.lastSelectedStatus) — the
 * closest real-app equivalent of the design prototype's single shared `selected` state.
 */
@Component({
  selector: 'app-state-machine',
  standalone: true,
  imports: [],
  templateUrl: './state-machine.component.html',
  styleUrl: './state-machine.component.css'
})
export class StateMachineComponent implements OnInit {
  private readonly applicationService = inject(ApplicationService);
  private readonly authService = inject(AuthService);
  readonly uiState = inject(UiStateService);

  readonly loading = signal(true);
  readonly errorMessage = signal<string | null>(null);
  readonly applications = signal<ApplicationResponse[]>([]);

  readonly statusOrder = STATUS_ORDER;
  readonly terminalOrder = TERMINAL_ORDER;

  readonly legend = [
    { swatchClass: 'swatch-recruiter', label: 'RECRUITER-OWNED EDGE' },
    { swatchClass: 'swatch-candidate', label: 'CANDIDATE-OWNED EDGE' },
    { swatchClass: 'swatch-none', label: 'NO EDGE FOR ADMIN' }
  ];

  readonly facts = [
    { kicker: 'STATES', value: '7', body: 'Four in motion, three terminal. Nothing free-text — the column is an enum.' },
    { kicker: 'LEGAL EDGES', value: '6', body: 'Everything else is a 400 or a 403, with both cases unit-tested.' },
    { kicker: 'ROLES THAT CAN MOVE WORK', value: '2', body: 'Admin owns postings and users, never a candidate’s stage.' }
  ];

  readonly counts = computed(() => {
    const apps = this.applications();
    const map = new Map<ApplicationStatus, number>();
    for (const status of [...this.statusOrder, ...this.terminalOrder]) {
      map.set(status, apps.filter((a) => a.status === status).length);
    }
    return map;
  });

  readonly litStatus = computed(() => this.uiState.lastSelectedStatus());

  ngOnInit(): void {
    const role = this.authService.currentUser()?.role ?? 'CANDIDATE';
    this.applicationService.scoped(role).subscribe({
      next: (apps) => {
        this.applications.set(apps);
        this.loading.set(false);
      },
      error: (err) => {
        this.errorMessage.set(extractErrorMessage(err));
        this.loading.set(false);
      }
    });
  }

  count(status: ApplicationStatus): string {
    return String(this.counts().get(status) ?? 0).padStart(2, '0');
  }

  /** Every forward edge is recruiter-owned except OFFER -> ACCEPTED, which the candidate owns. */
  edgeOwner(target: ApplicationStatus): 'RECRUITER' | 'CANDIDATE' {
    return target === 'ACCEPTED' ? 'CANDIDATE' : 'RECRUITER';
  }
}
