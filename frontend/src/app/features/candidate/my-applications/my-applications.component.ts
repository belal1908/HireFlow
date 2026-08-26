import { Component, OnInit, signal } from '@angular/core';
import { DatePipe } from '@angular/common';
import { forkJoin } from 'rxjs';
import { ApplicationService } from '../../../core/services/application.service';
import { PostingService } from '../../../core/services/posting.service';
import { ApplicationResponse, TERMINAL_STATUSES } from '../../../core/models/application.model';
import { extractErrorMessage } from '../../../core/utils/api-error.util';
import { flashElement, revealList } from '../../../shared/animation/motion';
import { StatusBadgeComponent } from '../../../shared/status-badge/status-badge.component';
import { PipelineProgressComponent } from '../../../shared/pipeline-progress/pipeline-progress.component';

@Component({
  selector: 'app-my-applications',
  standalone: true,
  imports: [DatePipe, StatusBadgeComponent, PipelineProgressComponent],
  templateUrl: './my-applications.component.html',
  styleUrl: './my-applications.component.css'
})
export class MyApplicationsComponent implements OnInit {
  readonly applications = signal<ApplicationResponse[]>([]);
  readonly postingTitles = signal<Map<number, string>>(new Map());
  readonly loading = signal(true);
  readonly errorMessage = signal<string | null>(null);
  readonly actionError = signal<string | null>(null);
  readonly actingOnId = signal<number | null>(null);

  readonly uploadingId = signal<number | null>(null);
  readonly uploadErrorId = signal<number | null>(null);
  readonly uploadError = signal<string | null>(null);
  readonly downloadingId = signal<number | null>(null);

  constructor(
    private applicationService: ApplicationService,
    private postingService: PostingService
  ) {}

  ngOnInit(): void {
    this.load();
  }

  private load(): void {
    this.loading.set(true);
    this.errorMessage.set(null);
    forkJoin({
      applications: this.applicationService.mine(),
      // Large size purely to resolve posting titles for display - a metadata lookup, not the
      // paginated browse view (that's postings-list.component.ts).
      postings: this.postingService.list(0, 200)
    }).subscribe({
      next: ({ applications, postings }) => {
        this.applications.set(applications);
        this.postingTitles.set(new Map(postings.content.map((p) => [p.id, p.title])));
        this.loading.set(false);
        // Deferred a frame so the rows exist in the DOM before the stagger queries for them.
        requestAnimationFrame(() => revealList('tbody tr[id^="app-row-"]'));
      },
      error: (err) => {
        this.errorMessage.set(extractErrorMessage(err));
        this.loading.set(false);
      }
    });
  }

  postingTitle(jobPostingId: number): string {
    return this.postingTitles().get(jobPostingId) ?? `Posting #${jobPostingId}`;
  }

  isTerminal(status: ApplicationResponse['status']): boolean {
    return TERMINAL_STATUSES.has(status);
  }

  withdraw(application: ApplicationResponse): void {
    this.transition(application, 'WITHDRAWN');
  }

  acceptOffer(application: ApplicationResponse): void {
    this.transition(application, 'ACCEPTED');
  }

  private transition(application: ApplicationResponse, target: 'WITHDRAWN' | 'ACCEPTED'): void {
    this.actingOnId.set(application.id);
    this.actionError.set(null);
    this.applicationService.updateStatus(application.id, target).subscribe({
      next: (updated) => {
        this.actingOnId.set(null);
        this.applications.update((list) => list.map((a) => (a.id === updated.id ? updated : a)));
        flashElement(`app-row-${updated.id}`);
      },
      error: (err) => {
        this.actingOnId.set(null);
        this.actionError.set(extractErrorMessage(err));
      }
    });
  }

  onResumeFileSelected(application: ApplicationResponse, event: Event): void {
    const input = event.target as HTMLInputElement;
    const file = input.files?.[0];
    if (!file) {
      return;
    }
    this.uploadingId.set(application.id);
    this.uploadErrorId.set(null);
    this.uploadError.set(null);
    this.applicationService.uploadResume(application.id, file).subscribe({
      next: (updated) => {
        this.uploadingId.set(null);
        this.applications.update((list) => list.map((a) => (a.id === updated.id ? updated : a)));
        input.value = ''; // allow re-selecting the same filename later
      },
      error: (err) => {
        this.uploadingId.set(null);
        this.uploadErrorId.set(application.id);
        this.uploadError.set(extractErrorMessage(err));
        input.value = '';
      }
    });
  }

  downloadResume(application: ApplicationResponse): void {
    this.downloadingId.set(application.id);
    this.applicationService.downloadResume(application.id).subscribe({
      next: (response) => {
        this.downloadingId.set(null);
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
        this.downloadingId.set(null);
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
}
