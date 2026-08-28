import { Component, OnInit, computed, inject, signal } from '@angular/core';
import { HttpErrorResponse } from '@angular/common/http';
import { Router } from '@angular/router';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { forkJoin, of } from 'rxjs';
import { catchError, map } from 'rxjs/operators';
import { PostingService } from '../../core/services/posting.service';
import { ApplicationService } from '../../core/services/application.service';
import { AuthService } from '../../core/services/auth.service';
import { PostingResponse } from '../../core/models/posting.model';
import { extractErrorMessage } from '../../core/utils/api-error.util';
import { PagerComponent } from '../../shared/pager/pager.component';

interface CardModel {
  posting: PostingResponse;
  applicationCount: number | null;
}

/**
 * `/postings` — merges the pre-redesign `PostingsListComponent` (candidate browse) and
 * `PostingsAdminComponent` (admin manage) into one role-adaptive route, per the README's route
 * mapping. ADMIN gets create/edit/close; CANDIDATE gets Apply; RECRUITER gets a "View pipeline"
 * link into `/applications` filtered to that posting.
 *
 * The 403 demo is a REAL API call, not a simulated one: non-admins clicking the dashed
 * "Call POST /api/postings as <ROLE> →" button issue an actual `PostingService.create()` POST
 * with their own token, and the panel below renders the genuine `HttpErrorResponse` — real
 * status code, real `ApiError.message` from `GlobalExceptionHandler` — not a hardcoded 403. This
 * is the project's whole thesis (authorization enforced at every layer) demonstrated rather than
 * described.
 */
@Component({
  selector: 'app-postings',
  standalone: true,
  imports: [ReactiveFormsModule, PagerComponent],
  templateUrl: './postings.component.html',
  styleUrl: './postings.component.css'
})
export class PostingsComponent implements OnInit {
  private readonly postingService = inject(PostingService);
  private readonly applicationService = inject(ApplicationService);
  private readonly router = inject(Router);
  private readonly fb = inject(FormBuilder);
  readonly authService = inject(AuthService);

  readonly loading = signal(true);
  readonly errorMessage = signal<string | null>(null);
  readonly cards = signal<CardModel[]>([]);
  readonly appliedPostingIds = signal<Set<number>>(new Set());
  readonly applyingId = signal<number | null>(null);
  readonly applyError = signal<string | null>(null);

  readonly page = signal(0);
  readonly totalPages = signal(0);
  readonly totalElements = signal(0);
  readonly hasNext = signal(false);

  readonly role = computed(() => this.authService.currentUser()?.role ?? 'CANDIDATE');
  readonly canManage = computed(() => this.role() === 'ADMIN');

  // 403 demo state
  readonly forbiddenVisible = signal(false);
  readonly forbiddenLoading = signal(false);
  readonly forbiddenStatus = signal<number | null>(null);
  readonly forbiddenMessage = signal<string | null>(null);

  // Create/edit modal state (ADMIN only)
  readonly modalMode = signal<'create' | 'edit' | null>(null);
  readonly modalSaving = signal(false);
  readonly modalError = signal<string | null>(null);
  private editingId: number | null = null;

  readonly form = this.fb.nonNullable.group({
    title: ['', [Validators.required, Validators.maxLength(200)]],
    description: ['', [Validators.maxLength(10000)]]
  });

  ngOnInit(): void {
    this.load();
  }

  private load(): void {
    this.loading.set(true);
    this.errorMessage.set(null);
    const role = this.role();

    const postings$ = this.postingService.list(this.page(), 20);
    const mine$ = role === 'CANDIDATE' ? this.applicationService.mine() : of([]);

    forkJoin({ postings: postings$, mine: mine$ }).subscribe({
      next: ({ postings, mine }) => {
        this.totalPages.set(postings.totalPages);
        this.totalElements.set(postings.totalElements);
        this.hasNext.set(postings.hasNext);
        this.appliedPostingIds.set(new Set(mine.map((a) => a.jobPostingId)));

        if (role === 'CANDIDATE') {
          this.cards.set(postings.content.filter((p) => p.status === 'OPEN').map((posting) => ({ posting, applicationCount: null })));
          this.loading.set(false);
          return;
        }

        // RECRUITER/ADMIN also see a per-posting application count — a handful of small
        // `size=1` requests (just to read totalElements) on top of the one page of postings,
        // not a new backend capability.
        if (postings.content.length === 0) {
          this.cards.set([]);
          this.loading.set(false);
          return;
        }
        forkJoin(
          postings.content.map((posting) =>
            this.applicationService.list(posting.id, null, 0, 1).pipe(
              map((page) => page.totalElements),
              catchError(() => of(null))
            )
          )
        ).subscribe((counts) => {
          this.cards.set(postings.content.map((posting, i) => ({ posting, applicationCount: counts[i] })));
          this.loading.set(false);
        });
      },
      error: (err) => {
        this.errorMessage.set(extractErrorMessage(err));
        this.loading.set(false);
      }
    });
  }

  goToPage(page: number): void {
    this.page.set(page);
    this.load();
  }

  hasApplied(postingId: number): boolean {
    return this.appliedPostingIds().has(postingId);
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

  viewPipeline(posting: PostingResponse): void {
    this.router.navigate(['/applications'], { queryParams: { postingId: posting.id } });
  }

  postedLabel(posting: PostingResponse): string {
    return `Posted ${new Date(posting.createdAt).toLocaleDateString('en-US', { month: 'short', day: '2-digit' })}`;
  }

  /** The 403 demo: a genuine POST /api/postings with the current (non-admin) user's token. */
  tryAdminEndpoint(): void {
    this.forbiddenLoading.set(true);
    this.forbiddenVisible.set(true);
    this.forbiddenStatus.set(null);
    this.forbiddenMessage.set(null);
    this.postingService.create({ title: '[e2e demo] Should be rejected', description: 'Issued by the 403 demo on /postings.' }).subscribe({
      next: () => {
        // Should never happen for a non-admin token — surfaced honestly rather than hidden.
        this.forbiddenLoading.set(false);
        this.forbiddenStatus.set(200);
        this.forbiddenMessage.set('Unexpected: the API accepted this request. Authorization may not be enforced as documented.');
      },
      error: (err: unknown) => {
        this.forbiddenLoading.set(false);
        this.forbiddenStatus.set(err instanceof HttpErrorResponse ? err.status : null);
        this.forbiddenMessage.set(extractErrorMessage(err));
      }
    });
  }

  dismissForbidden(): void {
    this.forbiddenVisible.set(false);
  }

  openCreate(): void {
    this.modalMode.set('create');
    this.modalError.set(null);
    this.editingId = null;
    this.form.reset({ title: '', description: '' });
  }

  openEdit(posting: PostingResponse): void {
    this.modalMode.set('edit');
    this.modalError.set(null);
    this.editingId = posting.id;
    this.form.reset({ title: posting.title, description: posting.description ?? '' });
  }

  closeModal(): void {
    this.modalMode.set(null);
  }

  saveModal(): void {
    if (this.form.invalid) {
      this.form.markAllAsTouched();
      return;
    }
    const { title, description } = this.form.getRawValue();
    this.modalSaving.set(true);
    this.modalError.set(null);

    const request$ =
      this.modalMode() === 'edit' && this.editingId != null
        ? this.postingService.update(this.editingId, { title, description: description || undefined })
        : this.postingService.create({ title, description: description || undefined });

    request$.subscribe({
      next: () => {
        this.modalSaving.set(false);
        this.modalMode.set(null);
        this.load();
      },
      error: (err) => {
        this.modalSaving.set(false);
        this.modalError.set(extractErrorMessage(err));
      }
    });
  }

  toggleOpenClosed(posting: PostingResponse): void {
    const target = posting.status === 'OPEN' ? 'CLOSED' : 'OPEN';
    this.postingService.update(posting.id, { status: target }).subscribe({
      next: (updated) => {
        this.cards.update((list) => list.map((c) => (c.posting.id === updated.id ? { ...c, posting: updated } : c)));
      },
      error: (err) => {
        this.errorMessage.set(extractErrorMessage(err));
      }
    });
  }
}
