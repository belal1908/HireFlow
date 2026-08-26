import { Component, OnInit, signal, ViewChild } from '@angular/core';
import { DatePipe } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { MatTable, MatTableModule } from '@angular/material/table';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatSelectModule } from '@angular/material/select';
import { MatButtonModule } from '@angular/material/button';
import { MatCardModule } from '@angular/material/card';
import { AuthService } from '../../../core/services/auth.service';
import { ApplicationService } from '../../../core/services/application.service';
import { PostingService } from '../../../core/services/posting.service';
import {
  ApplicationEventResponse,
  ApplicationResponse,
  ApplicationStatus,
  nextForwardStatus,
  TERMINAL_STATUSES
} from '../../../core/models/application.model';
import { PostingResponse } from '../../../core/models/posting.model';
import { extractErrorMessage } from '../../../core/utils/api-error.util';
import { flashElement, revealList, advanceCard } from '../../../shared/animation/motion';
import { StatusBadgeComponent } from '../../../shared/status-badge/status-badge.component';
import { PagerComponent } from '../../../shared/pager/pager.component';
import { PipelineProgressComponent } from '../../../shared/pipeline-progress/pipeline-progress.component';

const ALL_STATUSES: ApplicationStatus[] = [
  'APPLIED',
  'SCREENING',
  'INTERVIEW',
  'OFFER',
  'ACCEPTED',
  'REJECTED',
  'WITHDRAWN'
];

type ViewMode = 'table' | 'kanban';

@Component({
  selector: 'app-applications-dashboard',
  standalone: true,
  imports: [
    FormsModule,
    DatePipe,
    StatusBadgeComponent,
    PagerComponent,
    PipelineProgressComponent,
    MatTableModule,
    MatFormFieldModule,
    MatSelectModule,
    MatButtonModule,
    MatCardModule
  ],
  templateUrl: './applications-dashboard.component.html',
  styleUrl: './applications-dashboard.component.css'
})
export class ApplicationsDashboardComponent implements OnInit {
  readonly allStatuses = ALL_STATUSES;
  readonly pageSize = 20;

  /** Columns for the native `<table mat-table>` — the audit-trail detail row is rendered via a
   *  separate matRowDef (see `isExpandedRow`) and isn't part of this list, matching the
   *  Angular Material "expandable rows" table example. */
  readonly displayedColumns = ['id', 'posting', 'candidate', 'status', 'updated', 'resume', 'actions'];

  /**
   * CdkTable only re-evaluates `matRowDef`'s `when` predicate when it diffs a *new* dataSource
   * array reference — toggling `expandedIds` (a separate signal from `applications`) doesn't
   * change that reference, so without an explicit `renderRows()` call the audit-trail detail row
   * would never actually appear/disappear despite the "View events"/"Hide events" label (an
   * ordinary interpolation) flipping correctly. `toggleEvents()` below calls `renderRows()` after
   * updating `expandedIds` to force CdkTable to re-check `isExpandedRow` for every row.
   */
  @ViewChild(MatTable) table?: MatTable<ApplicationResponse>;

  readonly applications = signal<ApplicationResponse[]>([]);
  readonly postings = signal<PostingResponse[]>([]);
  readonly loading = signal(true);
  readonly errorMessage = signal<string | null>(null);
  readonly actionError = signal<string | null>(null);
  readonly actingOnId = signal<number | null>(null);

  readonly expandedIds = signal<Set<number>>(new Set());
  readonly eventsByApplication = signal<Map<number, ApplicationEventResponse[]>>(new Map());
  readonly eventsLoadingId = signal<number | null>(null);
  readonly downloadingResumeId = signal<number | null>(null);

  /** Table is the default view — the working filter/transition-action flow (and the e2e suite) targets it. Kanban is additive, behind this toggle. */
  readonly viewMode = signal<ViewMode>('table');

  readonly page = signal(0);
  readonly totalPages = signal(0);
  readonly totalElements = signal(0);
  readonly hasNext = signal(false);

  filterPostingId: number | null = null;
  filterStatus: ApplicationStatus | '' = '';

  constructor(
    public authService: AuthService,
    private applicationService: ApplicationService,
    private postingService: PostingService
  ) {}

  ngOnInit(): void {
    // A generous single fetch purely to resolve posting titles / populate the filter dropdown —
    // this is metadata lookup, not the primary paginated surface (that's the applications list
    // itself, below, which is what actually pages through results).
    this.postingService.list(0, 200).subscribe({
      next: (result) => this.postings.set(result.content),
      error: () => {
        /* Non-fatal: dashboard still works with numeric posting ids if this fails. */
      }
    });
    this.load();
  }

  load(): void {
    this.loading.set(true);
    this.errorMessage.set(null);
    this.applicationService.list(this.filterPostingId, this.filterStatus || null, this.page(), this.pageSize).subscribe({
      next: (result) => {
        this.applications.set(result.content);
        this.totalPages.set(result.totalPages);
        this.totalElements.set(result.totalElements);
        this.hasNext.set(result.hasNext);
        this.loading.set(false);
        this.revealRows();
      },
      error: (err) => {
        this.errorMessage.set(extractErrorMessage(err));
        this.loading.set(false);
      }
    });
  }

  /**
   * Staggered reveal of whichever surface is currently rendered. Deferred a frame so Angular has
   * actually flushed the new rows/cards into the DOM before anime.js queries for them - without
   * that wait the selector matches the *previous* page's nodes (or nothing at all on first load).
   */
  private revealRows(): void {
    requestAnimationFrame(() => {
      revealList(this.viewMode() === 'kanban' ? '.kanban-card' : 'tbody tr[id^="app-row-"]');
    });
  }

  goToPage(page: number): void {
    this.page.set(page);
    this.load();
  }

  /** Filter changes always reset back to page 0 - the previously selected page may no longer exist under the new filter. */
  onFilterChange(): void {
    this.page.set(0);
    this.load();
  }

  clearFilters(): void {
    this.filterPostingId = null;
    this.filterStatus = '';
    this.page.set(0);
    this.load();
  }

  postingTitle(jobPostingId: number): string {
    return this.postings().find((p) => p.id === jobPostingId)?.title ?? `Posting #${jobPostingId}`;
  }

  isTerminal(status: ApplicationStatus): boolean {
    return TERMINAL_STATUSES.has(status);
  }

  nextStatus(status: ApplicationStatus): ApplicationStatus | null {
    return nextForwardStatus(status);
  }

  /** Switching surfaces re-runs the entrance stagger, so the newly-shown view resolves in rather than snapping. */
  setViewMode(mode: ViewMode): void {
    if (this.viewMode() === mode) {
      return;
    }
    this.viewMode.set(mode);
    this.revealRows();
  }

  /** Groups the CURRENT PAGE's applications by status for the kanban columns - it does not fetch additional pages. */
  applicationsByStatus(status: ApplicationStatus): ApplicationResponse[] {
    return this.applications().filter((a) => a.status === status);
  }

  advance(application: ApplicationResponse): void {
    const target = this.nextStatus(application.status);
    if (!target) {
      return;
    }
    this.transition(application, target);
  }

  reject(application: ApplicationResponse): void {
    this.transition(application, 'REJECTED');
  }

  private transition(application: ApplicationResponse, target: ApplicationStatus): void {
    this.actingOnId.set(application.id);
    this.actionError.set(null);
    this.applicationService.updateStatus(application.id, target).subscribe({
      next: (updated) => {
        this.actingOnId.set(null);
        this.applyTransitionResult(updated);
        // Refresh the audit trail inline if it's currently expanded, so the new event shows up.
        if (this.expandedIds().has(updated.id)) {
          this.loadEvents(updated.id);
        }
      },
      error: (err) => {
        this.actingOnId.set(null);
        this.actionError.set(extractErrorMessage(err));
      }
    });
  }

  /**
   * Commits a completed transition to the list, choosing the motion that matches the surface.
   *
   * In kanban the card is about to be re-grouped into a *different column*, so it animates out
   * of its current one first and the incoming column's card is revealed on the other side -
   * the movement is the feedback. In the table the row stays exactly where it is and only its
   * status cell changes, so a flash is the honest signal; sliding it around would imply a
   * movement that didn't happen.
   */
  private applyTransitionResult(updated: ApplicationResponse): void {
    const commit = () => {
      this.applications.update((list) => list.map((a) => (a.id === updated.id ? updated : a)));
    };

    if (this.viewMode() !== 'kanban') {
      commit();
      flashElement(`app-row-${updated.id}`);
      return;
    }

    const card = document.getElementById(`app-row-${updated.id}`);
    if (!card) {
      commit();
      return;
    }

    const leavingForward = !TERMINAL_STATUSES.has(updated.status);
    advanceCard(card, leavingForward ? 'forward' : 'out').then(() => {
      commit();
      requestAnimationFrame(() => {
        const landed = document.getElementById(`app-row-${updated.id}`);
        if (landed) {
          revealList(`#app-row-${updated.id}`);
        }
      });
    });
  }

  /**
   * matRowDef `when` predicate for the audit-trail detail row: renders that row for a given
   * application only while it's expanded, so the DOM keeps the same "one <tr class="events-row">
   * injected right after its data row, only when expanded" shape the e2e suite and the CSS both
   * depend on — not an always-present row that's merely hidden.
   */
  isExpandedRow = (_index: number, app: ApplicationResponse): boolean => this.expandedIds().has(app.id);

  toggleEvents(applicationId: number): void {
    const expanded = new Set(this.expandedIds());
    if (expanded.has(applicationId)) {
      expanded.delete(applicationId);
      this.expandedIds.set(expanded);
      this.table?.renderRows();
      return;
    }
    expanded.add(applicationId);
    this.expandedIds.set(expanded);
    // See the `table` ViewChild's doc comment: expandedIds changing doesn't touch the
    // dataSource array CdkTable actually diffs, so the `isExpandedRow`-gated detail row needs an
    // explicit renderRows() to be inserted/removed — it won't happen on its own.
    this.table?.renderRows();
    this.loadEvents(applicationId);
  }

  private loadEvents(applicationId: number): void {
    this.eventsLoadingId.set(applicationId);
    this.applicationService.events(applicationId).subscribe({
      next: (events) => {
        this.eventsLoadingId.set(null);
        const map = new Map(this.eventsByApplication());
        map.set(applicationId, events);
        this.eventsByApplication.set(map);
        // Reveal the trail oldest-to-newest. The audit log is a sequence of things that happened
        // in order, so letting the entries arrive in that order reads as a history unfolding
        // rather than a block of text appearing. Scoped to this application's list so expanding a
        // second row doesn't re-animate the first.
        requestAnimationFrame(() => revealList(`#events-${applicationId} li`, { stagger: 45 }));
      },
      error: (err) => {
        this.eventsLoadingId.set(null);
        this.actionError.set(extractErrorMessage(err));
      }
    });
  }

  eventsFor(applicationId: number): ApplicationEventResponse[] {
    return this.eventsByApplication().get(applicationId) ?? [];
  }

  downloadResume(application: ApplicationResponse): void {
    this.downloadingResumeId.set(application.id);
    this.applicationService.downloadResume(application.id).subscribe({
      next: (response) => {
        this.downloadingResumeId.set(null);
        const blob = response.body;
        if (!blob) {
          return;
        }
        const url = URL.createObjectURL(blob);
        const link = document.createElement('a');
        link.href = url;
        link.download = application.resumeFilename ?? 'resume.pdf';
        link.click();
        URL.revokeObjectURL(url);
      },
      error: (err) => {
        this.downloadingResumeId.set(null);
        this.actionError.set(extractErrorMessage(err));
      }
    });
  }
}
