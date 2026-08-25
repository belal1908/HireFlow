import { Component, OnInit, signal } from '@angular/core';
import { DatePipe } from '@angular/common';
import { FormsModule } from '@angular/forms';
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
import { flashElement } from '../../../shared/animation/flash-element';

const ALL_STATUSES: ApplicationStatus[] = [
  'APPLIED',
  'SCREENING',
  'INTERVIEW',
  'OFFER',
  'ACCEPTED',
  'REJECTED',
  'WITHDRAWN'
];

@Component({
  selector: 'app-applications-dashboard',
  standalone: true,
  imports: [FormsModule, DatePipe],
  templateUrl: './applications-dashboard.component.html',
  styleUrl: './applications-dashboard.component.css'
})
export class ApplicationsDashboardComponent implements OnInit {
  readonly allStatuses = ALL_STATUSES;

  readonly applications = signal<ApplicationResponse[]>([]);
  readonly postings = signal<PostingResponse[]>([]);
  readonly loading = signal(true);
  readonly errorMessage = signal<string | null>(null);
  readonly actionError = signal<string | null>(null);
  readonly actingOnId = signal<number | null>(null);

  readonly expandedIds = signal<Set<number>>(new Set());
  readonly eventsByApplication = signal<Map<number, ApplicationEventResponse[]>>(new Map());
  readonly eventsLoadingId = signal<number | null>(null);

  filterPostingId: number | null = null;
  filterStatus: ApplicationStatus | '' = '';

  constructor(
    public authService: AuthService,
    private applicationService: ApplicationService,
    private postingService: PostingService
  ) {}

  ngOnInit(): void {
    this.postingService.list().subscribe({
      next: (postings) => this.postings.set(postings),
      error: () => {
        /* Non-fatal: dashboard still works with numeric posting ids if this fails. */
      }
    });
    this.load();
  }

  load(): void {
    this.loading.set(true);
    this.errorMessage.set(null);
    this.applicationService.list(this.filterPostingId, this.filterStatus || null).subscribe({
      next: (applications) => {
        this.applications.set(applications);
        this.loading.set(false);
      },
      error: (err) => {
        this.errorMessage.set(extractErrorMessage(err));
        this.loading.set(false);
      }
    });
  }

  clearFilters(): void {
    this.filterPostingId = null;
    this.filterStatus = '';
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
        this.applications.update((list) => list.map((a) => (a.id === updated.id ? updated : a)));
        flashElement(`app-row-${updated.id}`);
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

  toggleEvents(applicationId: number): void {
    const expanded = new Set(this.expandedIds());
    if (expanded.has(applicationId)) {
      expanded.delete(applicationId);
      this.expandedIds.set(expanded);
      return;
    }
    expanded.add(applicationId);
    this.expandedIds.set(expanded);
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
}
