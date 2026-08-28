import { Component, EventEmitter, Input, Output } from '@angular/core';
import { MatPaginatorModule, PageEvent } from '@angular/material/paginator';

/**
 * A thin wrapper around MatPaginator that keeps the same page-based @Input/@Output contract its
 * callers (Postings, users-admin) already used pre-redesign — none of them needed to change.
 * Both backends paginate with a fixed server-side page size of 20 (see
 * PostingService/UserService), so `pageSize` is fixed here too rather than exposed as a
 * page-size picker the backend doesn't support. Kept as a Material component per the task's
 * "keep Material wired in" guidance, restyled via the global `.mat-mdc-paginator` overrides in
 * src/styles.css rather than the M3 Azure/Blue defaults.
 */
@Component({
  selector: 'app-pager',
  standalone: true,
  imports: [MatPaginatorModule],
  template: `
    @if (totalPages > 1) {
      <mat-paginator
        class="pager"
        [length]="totalElements"
        [pageIndex]="page"
        [pageSize]="pageSize"
        [hidePageSize]="true"
        [showFirstLastButtons]="true"
        (page)="onPage($event)"
      ></mat-paginator>
    }
  `,
  styles: [
    `
      .pager {
        margin-top: 1rem;
        background: transparent;
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

  readonly pageSize = 20;

  onPage(event: PageEvent): void {
    this.pageChange.emit(event.pageIndex);
  }
}
