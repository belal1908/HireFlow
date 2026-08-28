import { Component, OnInit, computed, inject, signal } from '@angular/core';
import { ActivatedRoute, Router } from '@angular/router';
import { forkJoin } from 'rxjs';
import { ApplicationService } from '../../core/services/application.service';
import { PostingService } from '../../core/services/posting.service';
import { AuthService } from '../../core/services/auth.service';
import { UserDirectoryService } from '../../core/services/user-directory.service';
import { UiStateService } from '../../core/state/ui-state.service';
import { ApplicationEventResponse, ApplicationResponse, ApplicationStatus, TERMINAL_STATUSES } from '../../core/models/application.model';
import { PostingResponse } from '../../core/models/posting.model';
import { extractErrorMessage } from '../../core/utils/api-error.util';
import { transitionOptions, AllowedAction, DeniedAction } from '../../shared/transition/transition-validator';
import { StatusBadgeComponent } from '../../shared/status-badge/status-badge.component';
import { PipelineProgressComponent } from '../../shared/pipeline-progress/pipeline-progress.component';
import { PendingTransition, TransitionConfirmSheetComponent } from '../../shared/transition-confirm/transition-confirm-sheet.component';

type StatusFilter = 'All' | 'Active' | 'Closed';

/**
 * `/applications` — merges the pre-redesign `MyApplicationsComponent` (candidate) and
 * `ApplicationsDashboardComponent` (recruiter/admin) into one role-adaptive route, per the
 * README's route mapping. Master-detail layout: searchable/filterable list on the left, a sticky
 * detail panel on the right with the 5-step stepper, the ported `validateTransition()`-driven
 * action row, an optional "denied by TransitionValidator" panel, and the audit trail.
 *
 * Data scope: CANDIDATE gets `GET /api/applications/mine` (their own only); RECRUITER/ADMIN get
 * `GET /api/applications` (all, via `ApplicationService.scoped()`). Search/status-filter run
 * client-side over that loaded set rather than a server-side free-text param — the real API only
 * exposes `postingId`/`status` filters (no text search), so this trades true server pagination
 * for search that actually works end-to-end; `scoped()` fetches a generous single page (500) to
 * keep that trade reasonable at portfolio scale. Documented in the redesign's final report.
 */
@Component({
  selector: 'app-applications',
  standalone: true,
  imports: [StatusBadgeComponent, PipelineProgressComponent, TransitionConfirmSheetComponent],
  templateUrl: './applications.component.html',
  styleUrl: './applications.component.css'
})
export class ApplicationsComponent implements OnInit {
  private readonly applicationService = inject(ApplicationService);
  private readonly postingService = inject(PostingService);
  private readonly userDirectory = inject(UserDirectoryService);
  private readonly router = inject(Router);
  private readonly route = inject(ActivatedRoute);
  readonly authService = inject(AuthService);
  readonly uiState = inject(UiStateService);

  readonly loading = signal(true);
  readonly errorMessage = signal<string | null>(null);
  readonly actionError = signal<string | null>(null);

  readonly applications = signal<ApplicationResponse[]>([]);
  readonly postings = signal<Map<number, PostingResponse>>(new Map());

  readonly query = signal('');
  readonly filter = signal<StatusFilter>('All');
  /** Set when arriving via Postings' "View pipeline" link (?postingId=). Client-side only —
   *  there's no dedicated filter UI for it in the design, just an incoming deep link. */
  readonly postingIdFilter = signal<number | null>(null);
  readonly selectedId = signal<number | null>(null);
  readonly mobileDetailOpen = signal(false);

  readonly selectedEvents = signal<ApplicationEventResponse[]>([]);
  readonly eventsLoading = signal(false);

  readonly pendingTransition = signal<PendingTransition | null>(null);
  readonly confirmSubmitting = signal(false);

  readonly uploadingResume = signal(false);
  readonly uploadError = signal<string | null>(null);
  readonly downloadingResume = signal(false);

  readonly role = computed(() => this.authService.currentUser()?.role ?? 'CANDIDATE');

  readonly visible = computed(() => {
    let list = this.applications();
    const postingId = this.postingIdFilter();
    if (postingId != null) {
      list = list.filter((a) => a.jobPostingId === postingId);
    }
    const filter = this.filter();
    if (filter === 'Active') {
      list = list.filter((a) => !TERMINAL_STATUSES.has(a.status));
    } else if (filter === 'Closed') {
      list = list.filter((a) => TERMINAL_STATUSES.has(a.status));
    }
    const q = this.query().trim().toLowerCase();
    if (q) {
      list = list.filter((a) => {
        const haystack = `${this.postingTitle(a.jobPostingId)} ${this.candidateLabel(a)}`.toLowerCase();
        return haystack.includes(q);
      });
    }
    return list;
  });

  readonly selected = computed(() => this.applications().find((a) => a.id === this.selectedId()) ?? null);

  readonly options = computed(() => {
    const sel = this.selected();
    if (!sel) {
      return { allowed: [] as AllowedAction[], denied: [] as DeniedAction[] };
    }
    return transitionOptions(sel.status, this.role());
  });

  readonly noActionsReason = computed(() => {
    const sel = this.selected();
    if (!sel) {
      return '';
    }
    if (TERMINAL_STATUSES.has(sel.status)) {
      return `${sel.status} is terminal. No role can move this application again — including ADMIN.`;
    }
    return `No transition on this application is permitted for ${this.role()}. The buttons are absent because the API would return 403.`;
  });

  readonly emptyState = computed(() => {
    const q = this.query().trim();
    const candidate = this.role() === 'CANDIDATE';
    if (q) {
      return {
        title: 'Nothing matches that search',
        body: `No application in your scope matches “${q}”. Remember you only ever see records the API returns for your role.`,
        cta: 'Clear search'
      };
    }
    if (this.filter() === 'Closed') {
      return { title: 'No closed applications', body: 'Nothing in your scope has reached a terminal state yet.', cta: 'Show all records' };
    }
    return {
      title: 'Nothing in motion',
      body: candidate
        ? 'Browse the open roles and apply — your first application starts in APPLIED.'
        : 'Every application in your scope has reached a terminal state.',
      cta: candidate ? 'Browse open roles' : 'Show all records'
    };
  });

  ngOnInit(): void {
    const postingIdParam = this.route.snapshot.queryParamMap.get('postingId');
    if (postingIdParam) {
      this.postingIdFilter.set(Number(postingIdParam));
    }
    this.load();
  }

  clearPostingFilter(): void {
    this.postingIdFilter.set(null);
    this.router.navigate([], { queryParams: {} });
  }

  private load(): void {
    this.loading.set(true);
    this.errorMessage.set(null);
    const role = this.role();

    forkJoin({
      applications: this.applicationService.scoped(role),
      postings: this.postingService.titleMap(),
      directory: this.userDirectory.ensureLoaded()
    }).subscribe({
      next: ({ applications, postings }) => {
        this.applications.set(applications);
        this.postings.set(postings);
        this.loading.set(false);
      },
      error: (err) => {
        this.errorMessage.set(extractErrorMessage(err));
        this.loading.set(false);
      }
    });
  }

  postingTitle(jobPostingId: number): string {
    return this.postings().get(jobPostingId)?.title ?? `Posting #${jobPostingId}`;
  }

  postingMeta(jobPostingId: number): string {
    const description = this.postings().get(jobPostingId)?.description;
    if (!description) {
      return '';
    }
    return description.length > 64 ? `${description.slice(0, 64)}…` : description;
  }

  /** RECRUITER can't resolve any candidate's email (see UserDirectoryService); ADMIN can. */
  candidateLabel(app: ApplicationResponse): string {
    if (this.role() === 'CANDIDATE') {
      return this.authService.currentUser()?.email ?? `Candidate #${app.candidateId}`;
    }
    return this.userDirectory.emailFor(app.candidateId) ?? `Candidate #${app.candidateId}`;
  }

  onQueryChange(value: string): void {
    this.query.set(value);
  }

  setFilter(filter: StatusFilter): void {
    this.filter.set(filter);
  }

  select(app: ApplicationResponse): void {
    this.selectedId.set(app.id);
    this.mobileDetailOpen.set(true);
    this.uiState.lastSelectedStatus.set(app.status);
    this.loadEvents(app.id);
  }

  closeMobileDetail(): void {
    this.mobileDetailOpen.set(false);
  }

  runEmptyAction(): void {
    if (this.query().trim()) {
      this.query.set('');
      return;
    }
    if (this.filter() === 'Closed') {
      this.filter.set('All');
      return;
    }
    if (this.role() === 'CANDIDATE') {
      this.router.navigateByUrl('/postings');
    } else {
      this.filter.set('All');
    }
  }

  private loadEvents(applicationId: number): void {
    this.eventsLoading.set(true);
    this.applicationService.events(applicationId).subscribe({
      next: (events) => {
        this.selectedEvents.set(events);
        this.eventsLoading.set(false);
      },
      error: (err) => {
        this.actionError.set(extractErrorMessage(err));
        this.eventsLoading.set(false);
      }
    });
  }

  openConfirm(target: ApplicationStatus): void {
    const sel = this.selected();
    if (!sel) {
      return;
    }
    this.actionError.set(null);
    this.pendingTransition.set({
      applicationId: sel.id,
      from: sel.status,
      to: target,
      title: this.postingTitle(sel.jobPostingId)
    });
  }

  cancelConfirm(): void {
    this.pendingTransition.set(null);
  }

  confirmTransition(note: string): void {
    const pending = this.pendingTransition();
    if (!pending) {
      return;
    }
    this.confirmSubmitting.set(true);
    this.actionError.set(null);
    this.applicationService.updateStatus(pending.applicationId, pending.to, note || undefined).subscribe({
      next: (updated) => {
        this.applications.update((list) => list.map((a) => (a.id === updated.id ? updated : a)));
        this.confirmSubmitting.set(false);
        this.pendingTransition.set(null);
        this.uiState.lastSelectedStatus.set(updated.status);
        this.loadEvents(updated.id);
      },
      error: (err) => {
        this.confirmSubmitting.set(false);
        this.actionError.set(extractErrorMessage(err));
      }
    });
  }

  onResumeFileSelected(event: Event): void {
    const sel = this.selected();
    const input = event.target as HTMLInputElement;
    const file = input.files?.[0];
    if (!sel || !file) {
      return;
    }
    this.uploadingResume.set(true);
    this.uploadError.set(null);
    this.applicationService.uploadResume(sel.id, file).subscribe({
      next: (updated) => {
        this.uploadingResume.set(false);
        this.applications.update((list) => list.map((a) => (a.id === updated.id ? updated : a)));
        input.value = '';
      },
      error: (err) => {
        this.uploadingResume.set(false);
        this.uploadError.set(extractErrorMessage(err));
        input.value = '';
      }
    });
  }

  downloadResume(): void {
    const sel = this.selected();
    if (!sel) {
      return;
    }
    this.downloadingResume.set(true);
    this.applicationService.downloadResume(sel.id).subscribe({
      next: (response) => {
        this.downloadingResume.set(false);
        const blob = response.body;
        if (!blob) {
          return;
        }
        const url = URL.createObjectURL(blob);
        const link = document.createElement('a');
        link.href = url;
        link.download = sel.resumeFilename ?? 'resume.pdf';
        link.click();
        URL.revokeObjectURL(url);
      },
      error: (err) => {
        this.downloadingResume.set(false);
        this.actionError.set(extractErrorMessage(err));
      }
    });
  }

  formatSize(bytes: number | null): string {
    if (bytes == null) {
      return '';
    }
    return `${(bytes / 1024).toFixed(0)} KB`;
  }

  formatDate(iso: string): string {
    return new Date(iso).toLocaleDateString('en-US', { month: 'short', day: '2-digit', year: 'numeric' });
  }
}
