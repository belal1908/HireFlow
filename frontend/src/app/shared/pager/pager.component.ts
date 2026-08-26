import { Component, EventEmitter, Input, Output } from '@angular/core';

/**
 * A minimal, reusable prev/next pager — deliberately not a full pagination UI component
 * library (page-number links, jump-to-page, etc.), just enough to page through the three
 * paginated list endpoints (applications, postings, admin users). Used by
 * applications-dashboard, postings-admin, and users-admin.
 */
@Component({
  selector: 'app-pager',
  standalone: true,
  template: `
    @if (totalPages > 1) {
      <div class="pager">
        <button type="button" [disabled]="page === 0" (click)="pageChange.emit(page - 1)">← Prev</button>
        <span class="pager-info">Page {{ page + 1 }} of {{ totalPages }} · {{ totalElements }} total</span>
        <button type="button" [disabled]="!hasNext" (click)="pageChange.emit(page + 1)">Next →</button>
      </div>
    }
  `,
  styles: [
    `
      .pager {
        display: flex;
        align-items: center;
        gap: 0.75rem;
        margin-top: 1rem;
        font-size: 0.82rem;
      }
      .pager button {
        padding: 0.35rem 0.7rem;
        border: 1px solid #d0d0d0;
        border-radius: 4px;
        background: white;
        cursor: pointer;
        font-size: 0.8rem;
      }
      .pager button:disabled {
        opacity: 0.5;
        cursor: default;
      }
      .pager-info {
        color: #666;
      }
    `
  ]
})
export class PagerComponent {
  @Input({ required: true }) page = 0;
  @Input({ required: true }) totalPages = 0;
  @Input({ required: true }) totalElements = 0;
  @Input({ required: true }) hasNext = false;
  @Output() pageChange = new EventEmitter<number>();
}
