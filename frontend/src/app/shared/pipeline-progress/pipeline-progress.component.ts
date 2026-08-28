import { Component, Input } from '@angular/core';
import { ApplicationStatus } from '../../core/models/application.model';
import { STATUS_ORDER } from '../transition/transition-validator';

/**
 * The Applications detail panel's stepper: five equal columns (APP/SCR/INT/OFF/ACC), each a 5px
 * bar plus a mono label. Ported pixel-for-pixel from the design handoff's `selSteps` builder
 * (ApplyTrack.dc.html ~line 801): a bar for step *i* takes ITS OWN status color once the
 * application has reached-or-passed it (`i <= currentIndex`), not the current status's color —
 * so an application sitting at INTERVIEW shows an amber bar for INTERVIEW, a blue one for
 * SCREENING, and a slate one for APPLIED, all lit, with OFFER/ACCEPTED still dim. A terminal
 * status (REJECTED/WITHDRAWN, not on the happy path) lights nothing, per the spec.
 *
 * Previously this component was a single continuous dot-track (see git history) with an
 * anime.js-driven fill animation; the new design calls for "none beyond default hover color
 * changes," so this version is plain CSS with no JS-driven motion — restyled in place rather
 * than replaced, since the @Input contract and the places that use it are unchanged.
 */
@Component({
  selector: 'app-pipeline-progress',
  standalone: true,
  template: `
    <div class="stepper" role="img" [attr.aria-label]="ariaLabel">
      @for (step of steps; track step.status) {
        <div class="stepper-col">
          <span class="stepper-bar" [class.is-lit]="step.lit" [attr.data-status]="step.lit ? step.status : null"></span>
          <span class="stepper-label" [class.is-lit]="step.lit">{{ step.label }}</span>
        </div>
      }
    </div>
  `,
  styles: [
    `
      .stepper {
        display: flex;
        align-items: flex-start;
        gap: 3px;
        width: 100%;
      }

      .stepper-col {
        flex: 1;
        min-width: 0;
        display: flex;
        flex-direction: column;
        gap: 7px;
        align-items: flex-start;
      }

      .stepper-bar {
        height: 5px;
        width: 100%;
        border-radius: 999px;
        background: rgba(21, 34, 50, 0.12);
        display: block;
      }

      .stepper-bar.is-lit[data-status='APPLIED'] { background: var(--status-applied); }
      .stepper-bar.is-lit[data-status='SCREENING'] { background: var(--status-screening); }
      .stepper-bar.is-lit[data-status='INTERVIEW'] { background: var(--status-interview); }
      .stepper-bar.is-lit[data-status='OFFER'] { background: var(--status-offer); }
      .stepper-bar.is-lit[data-status='ACCEPTED'] { background: var(--status-accepted); }

      .stepper-label {
        font-family: var(--font-mono);
        font-size: 9.5px;
        letter-spacing: 0.1em;
        text-transform: uppercase;
        color: var(--ink-35);
      }

      .stepper-label.is-lit {
        color: var(--ink-55);
      }
    `
  ]
})
export class PipelineProgressComponent {
  @Input({ required: true }) status!: ApplicationStatus;

  get isOffPath(): boolean {
    return STATUS_ORDER.indexOf(this.status) === -1;
  }

  get steps(): Array<{ status: ApplicationStatus; label: string; lit: boolean }> {
    const currentIndex = STATUS_ORDER.indexOf(this.status);
    const off = currentIndex < 0;
    return STATUS_ORDER.map((status, i) => ({
      status,
      label: status.slice(0, 3),
      lit: !off && i <= currentIndex
    }));
  }

  get ariaLabel(): string {
    if (this.isOffPath) {
      return `Application ${this.status.toLowerCase()}`;
    }
    const position = STATUS_ORDER.indexOf(this.status) + 1;
    return `Pipeline stage ${position} of ${STATUS_ORDER.length}: ${this.status}`;
  }
}
