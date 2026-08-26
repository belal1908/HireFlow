import {
  AfterViewInit,
  Component,
  ElementRef,
  Input,
  OnChanges,
  SimpleChanges,
  ViewChild
} from '@angular/core';
import { animate } from 'animejs';
import { ApplicationStatus } from '../../core/models/application.model';
import { DURATION, EASE, prefersReducedMotion } from '../animation/motion';

/** The forward pipeline, mirroring the backend's TransitionValidator FORWARD_PATH. */
const PIPELINE: ApplicationStatus[] = ['APPLIED', 'SCREENING', 'INTERVIEW', 'OFFER', 'ACCEPTED'];

/** Terminal states that end an application off the happy path. */
const TERMINAL_OFF_PATH: ApplicationStatus[] = ['REJECTED', 'WITHDRAWN'];

/**
 * A compact stepper visualising where an application sits in the pipeline
 * (APPLIED → SCREENING → INTERVIEW → OFFER → ACCEPTED), with a connecting track that fills to
 * the current stage.
 *
 * This is the one piece of UI that makes the state machine - the actual point of the whole
 * project - visible at a glance rather than as a single status word. The fill is animated
 * because the *distance travelled* is the information: seeing the bar grow when a recruiter
 * advances someone communicates progress in a way a text change doesn't.
 *
 * Applications that ended in REJECTED/WITHDRAWN render the track in a muted/terminal treatment
 * rather than pretending they're mid-pipeline - they left the path, and the UI says so.
 */
@Component({
  selector: 'app-pipeline-progress',
  standalone: true,
  template: `
    <div
      class="pipeline"
      [class.pipeline--terminal]="isOffPath"
      [attr.data-status]="status"
      role="img"
      [attr.aria-label]="ariaLabel"
    >
      <div class="pipeline__track" aria-hidden="true">
        <div class="pipeline__fill" #fill [attr.data-status]="status"></div>
      </div>
      <ol class="pipeline__steps" aria-hidden="true">
        @for (step of pipeline; track step; let i = $index) {
          <li
            class="pipeline__step"
            [class.is-done]="i <= reachedIndex && !isOffPath"
            [class.is-current]="i === reachedIndex && !isOffPath"
            [title]="step"
          >
            <span class="pipeline__dot"></span>
          </li>
        }
      </ol>
    </div>
  `,
  styles: [`
    .pipeline {
      position: relative;
      width: 100%;
      min-width: 140px;
      padding: 6px 0 2px;
    }

    .pipeline__track {
      position: absolute;
      top: 11px;
      left: 4px;
      right: 4px;
      height: 3px;
      border-radius: 999px;
      background: #e5e7eb;
      overflow: hidden;
    }

    .pipeline__fill {
      height: 100%;
      width: 0;
      border-radius: 999px;
      background: #2454ff;
    }

    /* Terminal states colour the whole track so it reads as "ended", not "in progress". */
    .pipeline__fill[data-status='REJECTED'] { background: #b91c1c; }
    .pipeline__fill[data-status='WITHDRAWN'] { background: #6b7280; }
    .pipeline__fill[data-status='ACCEPTED'] { background: #15803d; }

    .pipeline__steps {
      position: relative;
      display: flex;
      justify-content: space-between;
      margin: 0;
      padding: 0;
      list-style: none;
    }

    .pipeline__step {
      display: flex;
      align-items: center;
      justify-content: center;
    }

    .pipeline__dot {
      display: block;
      width: 9px;
      height: 9px;
      border-radius: 50%;
      background: #fff;
      box-shadow: inset 0 0 0 2px #d1d5db;
      transition: box-shadow 160ms ease, transform 160ms ease;
    }

    .pipeline__step.is-done .pipeline__dot {
      box-shadow: inset 0 0 0 5px #2454ff;
    }

    .pipeline__step.is-current .pipeline__dot {
      transform: scale(1.25);
    }

    .pipeline--terminal .pipeline__dot {
      box-shadow: inset 0 0 0 2px #d1d5db;
    }

    @media (prefers-reduced-motion: reduce) {
      .pipeline__dot { transition: none; }
    }
  `]
})
export class PipelineProgressComponent implements AfterViewInit, OnChanges {
  @Input({ required: true }) status!: ApplicationStatus;

  @ViewChild('fill') fillRef?: ElementRef<HTMLElement>;

  readonly pipeline = PIPELINE;

  get isOffPath(): boolean {
    return TERMINAL_OFF_PATH.includes(this.status);
  }

  /** Index of the furthest reached stage; -1 for off-path terminals. */
  get reachedIndex(): number {
    return PIPELINE.indexOf(this.status);
  }

  get ariaLabel(): string {
    if (this.isOffPath) {
      return `Application ${this.status.toLowerCase()}`;
    }
    const position = this.reachedIndex + 1;
    return `Pipeline stage ${position} of ${PIPELINE.length}: ${this.status}`;
  }

  ngAfterViewInit(): void {
    this.renderFill(true);
  }

  ngOnChanges(changes: SimpleChanges): void {
    if (changes['status'] && !changes['status'].firstChange) {
      this.renderFill(false);
    }
  }

  /**
   * Grows the track fill to the current stage. `immediate` skips the animation for the very
   * first paint - a list of twenty rows all animating their pipelines on load would be noise,
   * so the entrance stagger (revealList) carries that moment instead, and the fill only animates
   * when a status actually *changes* while the user is looking at it.
   */
  private renderFill(immediate: boolean): void {
    const el = this.fillRef?.nativeElement;
    if (!el) {
      return;
    }

    // Off-path terminals show a full, differently-coloured track: the application is finished,
    // it just didn't finish by reaching the end of the funnel.
    const targetPercent = this.isOffPath
      ? 100
      : (this.reachedIndex / (PIPELINE.length - 1)) * 100;

    if (immediate || prefersReducedMotion()) {
      el.style.width = `${targetPercent}%`;
      return;
    }

    animate(el, {
      width: `${targetPercent}%`,
      duration: DURATION.emphasis,
      ease: EASE.out
    });
  }
}
