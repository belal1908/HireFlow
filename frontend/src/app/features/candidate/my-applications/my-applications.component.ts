import { Component, OnInit, signal } from '@angular/core';
import { DatePipe } from '@angular/common';
import { forkJoin } from 'rxjs';
import { ApplicationService } from '../../../core/services/application.service';
import { PostingService } from '../../../core/services/posting.service';
import { ApplicationResponse, TERMINAL_STATUSES } from '../../../core/models/application.model';
import { extractErrorMessage } from '../../../core/utils/api-error.util';
import { flashElement } from '../../../shared/animation/flash-element';

@Component({
  selector: 'app-my-applications',
  standalone: true,
  imports: [DatePipe],
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
      postings: this.postingService.list()
    }).subscribe({
      next: ({ applications, postings }) => {
        this.applications.set(applications);
        this.postingTitles.set(new Map(postings.map((p) => [p.id, p.title])));
        this.loading.set(false);
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
}
