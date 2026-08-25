import { Component, OnInit, signal } from '@angular/core';
import { Router } from '@angular/router';
import { forkJoin } from 'rxjs';
import { PostingService } from '../../../core/services/posting.service';
import { ApplicationService } from '../../../core/services/application.service';
import { PostingResponse } from '../../../core/models/posting.model';
import { ApplicationResponse } from '../../../core/models/application.model';
import { extractErrorMessage } from '../../../core/utils/api-error.util';

@Component({
  selector: 'app-postings-list',
  standalone: true,
  imports: [],
  templateUrl: './postings-list.component.html',
  styleUrl: './postings-list.component.css'
})
export class PostingsListComponent implements OnInit {
  readonly postings = signal<PostingResponse[]>([]);
  /** jobPostingIds this candidate has already applied to, so we can disable/relabel Apply. */
  readonly appliedPostingIds = signal<Set<number>>(new Set());
  readonly loading = signal(true);
  readonly errorMessage = signal<string | null>(null);
  readonly applyingId = signal<number | null>(null);
  readonly applyError = signal<string | null>(null);

  constructor(
    private postingService: PostingService,
    private applicationService: ApplicationService,
    private router: Router
  ) {}

  ngOnInit(): void {
    this.load();
  }

  private load(): void {
    this.loading.set(true);
    this.errorMessage.set(null);
    forkJoin({
      postings: this.postingService.list(),
      applications: this.applicationService.mine()
    }).subscribe({
      next: ({ postings, applications }: { postings: PostingResponse[]; applications: ApplicationResponse[] }) => {
        this.postings.set(postings.filter((p) => p.status === 'OPEN'));
        this.appliedPostingIds.set(new Set(applications.map((a) => a.jobPostingId)));
        this.loading.set(false);
      },
      error: (err) => {
        this.errorMessage.set(extractErrorMessage(err));
        this.loading.set(false);
      }
    });
  }

  apply(posting: PostingResponse): void {
    this.applyingId.set(posting.id);
    this.applyError.set(null);
    this.applicationService.apply(posting.id).subscribe({
      next: () => {
        this.applyingId.set(null);
        this.appliedPostingIds.update((set) => new Set(set).add(posting.id));
      },
      error: (err) => {
        this.applyingId.set(null);
        this.applyError.set(extractErrorMessage(err));
      }
    });
  }

  hasApplied(postingId: number): boolean {
    return this.appliedPostingIds().has(postingId);
  }

  viewMyApplications(): void {
    this.router.navigateByUrl('/applications/mine');
  }
}
