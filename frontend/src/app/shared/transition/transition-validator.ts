import { ApplicationStatus, TERMINAL_STATUSES } from '../../core/models/application.model';
import { Role } from '../../core/models/user.model';

/**
 * Client-side mirror of the backend's `TransitionValidator`
 * (src/main/java/com/hireflow/application/transition/TransitionValidator.java) and, more
 * directly, a faithful TypeScript port of the `validate(from, to, role)` function from the
 * design handoff's prototype (design/design_handoff_applytrack/ApplyTrack.dc.html, ~line 570).
 *
 * This exists purely to drive UI enablement — which action buttons render, what the "Inspect
 * permissions" panel lists as denied-with-reason, and the Settings transition matrix. It is
 * NOT the authority: every real transition still goes through the same
 * `ApplicationService.updateStatus()` -> `PATCH /api/applications/{id}/status` the app always
 * used, and the server's own TransitionValidator makes the actual allow/deny call independently.
 * If this file and the server ever disagree, the server wins and the UI is just wrong about what
 * it offered — which is exactly the point the design's "Postings" 403 demo is trying to make
 * about authorization never truly living in the client.
 */

/** APPLIED -> SCREENING -> INTERVIEW -> OFFER, one step at a time (RECRUITER-driven). */
const FORWARD: Partial<Record<ApplicationStatus, ApplicationStatus>> = {
  APPLIED: 'SCREENING',
  SCREENING: 'INTERVIEW',
  INTERVIEW: 'OFFER'
};

/** The happy-path node order used by the Applications stepper and the State machine graph. */
export const STATUS_ORDER: ApplicationStatus[] = ['APPLIED', 'SCREENING', 'INTERVIEW', 'OFFER', 'ACCEPTED'];

/** The two terminal branches reachable from any non-terminal state, and who owns each edge. */
export const TERMINAL_ORDER: ApplicationStatus[] = ['REJECTED', 'WITHDRAWN'];

export interface ValidationResult {
  ok: boolean;
  reason?: string;
}

/** Pure function, no HTTP, no Angular DI — mirrors the prototype's `validate()` exactly. */
export function validateTransition(from: ApplicationStatus, to: ApplicationStatus, role: Role): ValidationResult {
  if (TERMINAL_STATUSES.has(from)) {
    return { ok: false, reason: `${from} is terminal — no outbound transitions for any role` };
  }
  if (to === 'ACCEPTED') {
    if (from !== 'OFFER') {
      return { ok: false, reason: 'ACCEPTED is reachable only from OFFER' };
    }
    return role === 'CANDIDATE' ? { ok: true } : { ok: false, reason: 'only the candidate may accept an offer' };
  }
  if (to === 'WITHDRAWN') {
    return role === 'CANDIDATE' ? { ok: true } : { ok: false, reason: 'withdrawal is candidate-only' };
  }
  if (to === 'REJECTED') {
    return role === 'RECRUITER' ? { ok: true } : { ok: false, reason: 'rejection is recruiter-only' };
  }
  if (FORWARD[from] === to) {
    return role === 'RECRUITER' ? { ok: true } : { ok: false, reason: 'forward progression is recruiter-only' };
  }
  return { ok: false, reason: 'not an edge in the state machine' };
}

/** Human label for a target status, per the design's action-button copy. */
export function actionLabel(target: ApplicationStatus): string {
  switch (target) {
    case 'REJECTED':
      return 'Reject';
    case 'WITHDRAWN':
      return 'Withdraw';
    case 'ACCEPTED':
      return 'Accept offer';
    default:
      return `Advance to ${target.toLowerCase()}`;
  }
}

/** CSS class for the action button, per the design's per-target button styling. */
export function actionButtonClass(target: ApplicationStatus): string {
  switch (target) {
    case 'REJECTED':
      return 'btn btn-sm btn-reject';
    case 'WITHDRAWN':
      return 'btn btn-sm btn-withdraw';
    case 'ACCEPTED':
      return 'btn btn-sm btn-accept';
    default:
      return 'btn btn-sm btn-ink';
  }
}

/** The six targets ever worth evaluating from a given state (mirrors the prototype's `targets` list). */
export const CANDIDATE_TARGETS: ApplicationStatus[] = ['SCREENING', 'INTERVIEW', 'OFFER', 'ACCEPTED', 'REJECTED', 'WITHDRAWN'];

export interface AllowedAction {
  target: ApplicationStatus;
  label: string;
  buttonClass: string;
}

export interface DeniedAction {
  target: ApplicationStatus;
  reason: string;
}

export interface TransitionOptions {
  allowed: AllowedAction[];
  denied: DeniedAction[];
}

/** Evaluates every candidate target against `validateTransition`, splitting allow/deny. */
export function transitionOptions(from: ApplicationStatus, role: Role): TransitionOptions {
  const allowed: AllowedAction[] = [];
  const denied: DeniedAction[] = [];
  for (const target of CANDIDATE_TARGETS) {
    if (target === from) {
      continue;
    }
    const result = validateTransition(from, target, role);
    if (result.ok) {
      allowed.push({ target, label: actionLabel(target), buttonClass: actionButtonClass(target) });
    } else {
      denied.push({ target, reason: result.reason ?? 'not an edge in the state machine' });
    }
  }
  return { allowed, denied };
}
