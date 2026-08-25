import { Component, inject, signal } from '@angular/core';
import { ReactiveFormsModule, FormBuilder, Validators } from '@angular/forms';
import { Router, RouterLink } from '@angular/router';
import { switchMap } from 'rxjs';
import { AuthService } from '../../../core/services/auth.service';
import { extractErrorMessage } from '../../../core/utils/api-error.util';

/**
 * Self-registration always creates a CANDIDATE server-side (AuthService#register on the
 * backend forces it regardless of what's sent) — there is no role picker here on purpose.
 * After a successful register, we immediately call login with the same credentials so the new
 * user lands signed-in rather than being bounced back to a bare login form; register itself
 * returns only a UserResponse, no tokens.
 */
@Component({
  selector: 'app-register',
  standalone: true,
  imports: [ReactiveFormsModule, RouterLink],
  templateUrl: './register.component.html',
  styleUrl: './register.component.css'
})
export class RegisterComponent {
  private readonly fb = inject(FormBuilder);
  private readonly authService = inject(AuthService);
  private readonly router = inject(Router);

  readonly form = this.fb.nonNullable.group({
    email: ['', [Validators.required, Validators.email]],
    password: ['', [Validators.required, Validators.minLength(8)]]
  });

  readonly submitting = signal(false);
  readonly errorMessage = signal<string | null>(null);

  submit(): void {
    if (this.form.invalid) {
      this.form.markAllAsTouched();
      return;
    }
    this.submitting.set(true);
    this.errorMessage.set(null);

    const credentials = this.form.getRawValue();
    this.authService
      .register(credentials)
      .pipe(switchMap(() => this.authService.login(credentials)))
      .subscribe({
        next: () => {
          this.submitting.set(false);
          this.router.navigateByUrl('/');
        },
        error: (err) => {
          this.submitting.set(false);
          this.errorMessage.set(extractErrorMessage(err));
        }
      });
  }
}
