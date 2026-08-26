import { Component, OnInit, inject, signal } from '@angular/core';
import { DatePipe } from '@angular/common';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { PostingService } from '../../../core/services/posting.service';
import { PostingResponse, PostingStatus } from '../../../core/models/posting.model';
import { extractErrorMessage } from '../../../core/utils/api-error.util';
import { PagerComponent } from '../../../shared/pager/pager.component';

@Component({
  selector: 'app-postings-admin',
  standalone: true,
  imports: [ReactiveFormsModule, DatePipe, PagerComponent],
  templateUrl: './postings-admin.component.html',
  styleUrl: './postings-admin.component.css'
})
export class PostingsAdminComponent implements OnInit {
  private readonly fb = inject(FormBuilder);
  private readonly postingService = inject(PostingService);

  readonly postings = signal<PostingResponse[]>([]);
  readonly loading = signal(true);
  readonly errorMessage = signal<string | null>(null);
  readonly creating = signal(false);
  readonly createError = signal<string | null>(null);

  readonly page = signal(0);
  readonly totalPages = signal(0);
  readonly totalElements = signal(0);
  readonly hasNext = signal(false);

  readonly editingId = signal<number | null>(null);
  readonly savingId = signal<number | null>(null);
  readonly editError = signal<string | null>(null);

  readonly createForm = this.fb.nonNullable.group({
    title: ['', [Validators.required, Validators.maxLength(200)]],
    description: ['', [Validators.maxLength(10000)]]
  });

  readonly editForm = this.fb.nonNullable.group({
    title: ['', [Validators.required, Validators.maxLength(200)]],
    description: ['', [Validators.maxLength(10000)]],
    status: 'OPEN' as PostingStatus
  });

  ngOnInit(): void {
    this.load();
  }

  private load(): void {
    this.loading.set(true);
    this.errorMessage.set(null);
    this.postingService.list(this.page()).subscribe({
      next: (result) => {
        this.postings.set(result.content);
        this.totalPages.set(result.totalPages);
        this.totalElements.set(result.totalElements);
        this.hasNext.set(result.hasNext);
        this.loading.set(false);
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

  createPosting(): void {
    if (this.createForm.invalid) {
      this.createForm.markAllAsTouched();
      return;
    }
    this.creating.set(true);
    this.createError.set(null);
    const { title, description } = this.createForm.getRawValue();
    this.postingService.create({ title, description: description || undefined }).subscribe({
      next: (posting) => {
        this.creating.set(false);
        this.postings.update((list) => [posting, ...list]);
        this.createForm.reset({ title: '', description: '' });
      },
      error: (err) => {
        this.creating.set(false);
        this.createError.set(extractErrorMessage(err));
      }
    });
  }

  startEdit(posting: PostingResponse): void {
    this.editingId.set(posting.id);
    this.editError.set(null);
    this.editForm.setValue({
      title: posting.title,
      description: posting.description ?? '',
      status: posting.status
    });
  }

  cancelEdit(): void {
    this.editingId.set(null);
    this.editError.set(null);
  }

  saveEdit(id: number): void {
    if (this.editForm.invalid) {
      this.editForm.markAllAsTouched();
      return;
    }
    this.savingId.set(id);
    this.editError.set(null);
    const { title, description, status } = this.editForm.getRawValue();
    this.postingService.update(id, { title, description: description || undefined, status }).subscribe({
      next: (updated) => {
        this.savingId.set(null);
        this.editingId.set(null);
        this.postings.update((list) => list.map((p) => (p.id === updated.id ? updated : p)));
      },
      error: (err) => {
        this.savingId.set(null);
        this.editError.set(extractErrorMessage(err));
      }
    });
  }

  toggleOpenClosed(posting: PostingResponse): void {
    const target: PostingStatus = posting.status === 'OPEN' ? 'CLOSED' : 'OPEN';
    this.savingId.set(posting.id);
    this.errorMessage.set(null);
    this.postingService.update(posting.id, { status: target }).subscribe({
      next: (updated) => {
        this.savingId.set(null);
        this.postings.update((list) => list.map((p) => (p.id === updated.id ? updated : p)));
      },
      error: (err) => {
        this.savingId.set(null);
        this.errorMessage.set(extractErrorMessage(err));
      }
    });
  }
}
