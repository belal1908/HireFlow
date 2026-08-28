import { Component } from '@angular/core';
import { RouterLink } from '@angular/router';

@Component({
  selector: 'app-forbidden',
  standalone: true,
  imports: [RouterLink],
  template: `
    <div class="forbidden">
      <div class="code mono">403</div>
      <h1>Forbidden</h1>
      <p>Your account role doesn't have access to this page.</p>
      <a class="btn btn-ink" routerLink="/">Go home</a>
    </div>
  `,
  styles: [
    `
      .forbidden {
        min-height: 100vh;
        display: flex;
        flex-direction: column;
        align-items: center;
        justify-content: center;
        text-align: center;
        padding: 4rem 1rem;
        background: var(--bg);
        color: var(--ink);
        font-family: var(--font-sans);
      }
      .code {
        font-size: 56px;
        font-weight: 600;
        color: var(--red);
        letter-spacing: -0.02em;
        margin-bottom: 8px;
      }
      h1 {
        margin-bottom: 0.5rem;
        font-size: 25px;
      }
      p {
        color: var(--ink-60);
        margin: 0 0 24px;
      }
    `
  ]
})
export class ForbiddenComponent {}
