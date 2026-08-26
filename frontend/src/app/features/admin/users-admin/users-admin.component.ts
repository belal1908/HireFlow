import { Component, OnInit, inject, signal } from '@angular/core';
import { DatePipe } from '@angular/common';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { UserService } from '../../../core/services/user.service';
import { UserResponse } from '../../../core/models/user.model';
import { extractErrorMessage } from '../../../core/utils/api-error.util';
import { PagerComponent } from '../../../shared/pager/pager.component';

/**
 * ADMIN-only screen for the stretch-goal PRD feature: closes the README's documented
 * "No admin-user-creation endpoint" gap. Only RECRUITER/ADMIN accounts can be created here —
 * CANDIDATE is deliberately not an option in the role selector, mirroring the backend's
 * rejection of that role on POST /api/admin/users (self-registration already owns that path).
 */
@Component({
  selector: 'app-users-admin',
  standalone: true,
  imports: [ReactiveFormsModule, DatePipe, PagerComponent],
  templateUrl: './users-admin.component.html',
  styleUrl: './users-admin.component.css'
})
export class UsersAdminComponent implements OnInit {
  private readonly fb = inject(FormBuilder);
  private readonly userService = inject(UserService);

  readonly users = signal<UserResponse[]>([]);
  readonly loading = signal(true);
  readonly errorMessage = signal<string | null>(null);
  readonly creating = signal(false);
  readonly createError = signal<string | null>(null);

  readonly page = signal(0);
  readonly totalPages = signal(0);
  readonly totalElements = signal(0);
  readonly hasNext = signal(false);

  readonly createForm = this.fb.nonNullable.group({
    email: ['', [Validators.required, Validators.email]],
    password: ['', [Validators.required, Validators.minLength(8)]],
    role: ['RECRUITER' as 'RECRUITER' | 'ADMIN', [Validators.required]]
  });

  ngOnInit(): void {
    this.load();
  }

  private load(): void {
    this.loading.set(true);
    this.errorMessage.set(null);
    this.userService.list(undefined, this.page()).subscribe({
      next: (result) => {
        this.users.set(result.content);
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

  createUser(): void {
    if (this.createForm.invalid) {
      this.createForm.markAllAsTouched();
      return;
    }
    this.creating.set(true);
    this.createError.set(null);
    const { email, password, role } = this.createForm.getRawValue();
    this.userService.create({ email, password, role }).subscribe({
      next: (user) => {
        this.creating.set(false);
        this.users.update((list) => [user, ...list]);
        this.createForm.reset({ email: '', password: '', role: 'RECRUITER' });
      },
      error: (err) => {
        this.creating.set(false);
        this.createError.set(extractErrorMessage(err));
      }
    });
  }
}
