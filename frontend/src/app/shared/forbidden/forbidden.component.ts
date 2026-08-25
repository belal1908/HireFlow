import { Component } from '@angular/core';
import { RouterLink } from '@angular/router';

@Component({
  selector: 'app-forbidden',
  standalone: true,
  imports: [RouterLink],
  template: `
    <div class="forbidden">
      <h1>403 — Forbidden</h1>
      <p>Your account role doesn't have access to this page.</p>
      <a routerLink="/">Go home</a>
    </div>
  `,
  styles: [
    `
      .forbidden {
        text-align: center;
        padding: 4rem 1rem;
      }
      h1 {
        margin-bottom: 0.5rem;
      }
    `
  ]
})
export class ForbiddenComponent {}
