import { Component, inject, signal } from '@angular/core';
import { ReactiveFormsModule, FormBuilder, Validators } from '@angular/forms';
import { RouterLink } from '@angular/router';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatInputModule } from '@angular/material/input';
import { AuthService } from '../../../core/services/auth.service';
import { extractErrorMessage } from '../../../core/utils/api-error.util';

/**
 * POSTs to /api/auth/password-reset/request, which always responds 200 whether or not the email
 * matches an account (see the backend's AuthService#requestPasswordReset Javadoc) - so this page
 * always shows the same generic confirmation line, never "no account found".
 *
 * The one place this page is deliberately NOT production-shaped: there's no SMTP server behind
 * this backend, so the reset token is handed back directly in the response body instead of being
 * emailed. When it's present, this page displays it in an explicit "dev mode" callout with a link
 * straight into ResetPasswordComponent - standing in for the email a real deployment would send.
 * See the README's "Password reset" section for the full tradeoff.
 */
@Component({
  selector: 'app-forgot-password',
  standalone: true,
  imports: [ReactiveFormsModule, RouterLink, MatFormFieldModule, MatInputModule],
  templateUrl: './forgot-password.component.html',
  styleUrl: './forgot-password.component.css'
})
export class ForgotPasswordComponent {
  private readonly fb = inject(FormBuilder);
  private readonly authService = inject(AuthService);

  readonly form = this.fb.nonNullable.group({
    email: ['', [Validators.required, Validators.email]]
  });

  readonly submitting = signal(false);
  readonly submitted = signal(false);
  readonly errorMessage = signal<string | null>(null);
  readonly devResetToken = signal<string | null>(null);

  submit(): void {
    if (this.form.invalid) {
      this.form.markAllAsTouched();
      return;
    }
    this.submitting.set(true);
    this.errorMessage.set(null);

    this.authService.requestPasswordReset(this.form.getRawValue()).subscribe({
      next: (response) => {
        this.submitting.set(false);
        this.submitted.set(true);
        this.devResetToken.set(response.resetToken);
      },
      error: (err) => {
        this.submitting.set(false);
        this.errorMessage.set(extractErrorMessage(err));
      }
    });
  }
}
