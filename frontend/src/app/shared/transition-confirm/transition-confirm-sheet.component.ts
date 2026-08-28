import { Component, EventEmitter, Input, Output } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { ApplicationStatus } from '../../core/models/application.model';
import { Role } from '../../core/models/user.model';
import { StatusBadgeComponent } from '../status-badge/status-badge.component';

export interface PendingTransition {
  applicationId: number;
  from: ApplicationStatus;
  to: ApplicationStatus;
  /** Posting title, used as the modal's heading — the design's "role · company" with no
   *  separate "company" concept in HireFlow's actual data model (see README judgment-call note). */
  title: string;
}

/**
 * The transition confirm-sheet modal (design README section 5). A dumb/presentational
 * component: the Applications page owns `pendingTransition` state and the actual PATCH call —
 * this component just renders the sheet and emits `confirm`/`cancel`. Opening this modal instead
 * of firing the transition directly on button click is new in this redesign (the pre-redesign
 * recruiter dashboard advanced/rejected applications immediately); the optional note field is
 * written to the real audit log via `ApplicationService.updateStatus(id, target, note)`.
 */
@Component({
  selector: 'app-transition-confirm-sheet',
  standalone: true,
  imports: [FormsModule, StatusBadgeComponent],
  templateUrl: './transition-confirm-sheet.component.html',
  styleUrl: './transition-confirm-sheet.component.css'
})
export class TransitionConfirmSheetComponent {
  @Input({ required: true }) pending!: PendingTransition;
  @Input({ required: true }) actorRole!: Role;
  @Input() submitting = false;

  @Output() confirm = new EventEmitter<string>();
  @Output() cancel = new EventEmitter<void>();

  note = '';

  get confirmLabel(): string {
    switch (this.pending.to) {
      case 'REJECTED':
        return 'Reject application';
      case 'WITHDRAWN':
        return 'Withdraw application';
      case 'ACCEPTED':
        return 'Accept offer';
      default:
        return 'Confirm transition';
    }
  }

  get confirmClass(): string {
    switch (this.pending.to) {
      case 'REJECTED':
        return 'btn btn-confirm-reject';
      case 'ACCEPTED':
        return 'btn btn-accept';
      case 'WITHDRAWN':
        return 'btn btn-confirm-withdraw';
      default:
        return 'btn btn-ink';
    }
  }

  onConfirm(): void {
    this.confirm.emit(this.note.trim());
  }
}
