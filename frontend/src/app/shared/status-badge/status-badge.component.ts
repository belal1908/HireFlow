import { Component, Input } from '@angular/core';
import { ApplicationStatus } from '../../core/models/application.model';

/**
 * Single reusable rendering of an ApplicationStatus badge. The actual color treatment per
 * status lives in ONE place — the global `.status-badge[data-status=...]` rules in
 * src/styles.css — so this component just supplies the markup (also previously copy-pasted
 * across my-applications/applications-dashboard) and stays a plain, DOM-attribute-driven span
 * with no component-scoped styles of its own to keep in sync.
 */
@Component({
  selector: 'app-status-badge',
  standalone: true,
  template: `<span class="status-badge" [attr.data-status]="status">{{ status }}</span>`
})
export class StatusBadgeComponent {
  @Input({ required: true }) status!: ApplicationStatus;
}
