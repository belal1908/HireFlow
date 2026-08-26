import { animate, stagger, createTimeline, utils } from 'animejs';

/**
 * Shared motion vocabulary for HireFlow.
 *
 * The animation in this app is deliberately in service of one idea: an application *moves
 * through a pipeline*. So motion is used where it communicates that movement or a change of
 * state (a card advancing a stage, a pipeline filling in, a status flipping) and essentially
 * nowhere else. This is a recruiting tool, not a landing page - the bar for adding motion is
 * "does this make a state change easier to follow", not "does this look lively".
 *
 * Everything funnels through here so durations/easings stay consistent and, more importantly,
 * so `prefers-reduced-motion` is honored in exactly one place instead of being re-checked (and
 * eventually forgotten) at each call site.
 */

/** One shared scale, so unrelated components don't drift to slightly different timings. */
export const DURATION = {
  /** Badge flips, small acknowledgements. */
  quick: 180,
  /** The default for most UI motion. */
  base: 320,
  /** Card advancing between columns, pipeline drawing. */
  emphasis: 520
} as const;

export const EASE = {
  /** Decelerating - things arriving/settling. */
  out: 'outQuart',
  /** Accelerating - things leaving. */
  in: 'inQuad',
  /** Slight overshoot, used sparingly for "this landed" moments. */
  settle: 'outBack(1.4)'
} as const;

/**
 * True when the user (or their OS) has asked for reduced motion. Read live rather than cached:
 * macOS/Windows let this be toggled without a reload, and a cached value would strand the user
 * with the setting that happened to be active at boot.
 */
export function prefersReducedMotion(): boolean {
  return typeof window !== 'undefined'
    && typeof window.matchMedia === 'function'
    && window.matchMedia('(prefers-reduced-motion: reduce)').matches;
}

/**
 * Runs `fn` only when motion is welcome. When reduced motion is requested, `onReduced` runs
 * instead (if provided) so callers can still apply the *end state* instantly - the point is to
 * remove the movement, not the outcome.
 */
export function withMotion(fn: () => void, onReduced?: () => void): void {
  if (prefersReducedMotion()) {
    onReduced?.();
    return;
  }
  fn();
}

/**
 * Reveals a list (table rows, kanban cards, posting cards) with a short staggered rise. Called
 * after data loads so a dashboard resolves into place instead of snapping in - which also makes
 * it visually obvious *which* rows are new after a filter change.
 */
export function revealList(selector: string, options: { stagger?: number } = {}): void {
  const targets = utils.$(selector);
  if (!targets.length) {
    return;
  }

  withMotion(
    () => {
      animate(targets, {
        opacity: [0, 1],
        translateY: [8, 0],
        duration: DURATION.base,
        ease: EASE.out,
        delay: stagger(options.stagger ?? 26)
      });
    },
    // Reduced motion: make sure nothing is left stuck at opacity 0 from a previous run.
    () => utils.set(targets, { opacity: 1, translateY: 0 })
  );
}

/**
 * Marks a row/card that just changed status. Replaces the old `flashElement` helper - same
 * intent (draw the eye to the thing that changed), now sharing the timing scale above.
 */
export function flashElement(elementId: string): void {
  const el = document.getElementById(elementId);
  if (!el) {
    return;
  }

  withMotion(() => {
    animate(el, {
      backgroundColor: [
        { to: 'rgba(36, 84, 255, 0.18)', duration: 1 },
        { to: 'rgba(36, 84, 255, 0)', duration: 900 }
      ],
      ease: EASE.out
    });
  });
}

/**
 * The signature interaction: a kanban card advancing to the next stage. The card lifts and
 * slides toward its new column, then the caller re-renders and the card reappears there via
 * {@link revealList}. Resolves when the outgoing half is done so the caller can sequence the
 * data refresh against it.
 *
 * Deliberately not a true FLIP animation across columns: the columns re-render from new data
 * anyway, and a full measure/move/settle implementation would add real complexity for a
 * difference most users would never consciously notice.
 */
export function advanceCard(el: HTMLElement, direction: 'forward' | 'out' = 'forward'): Promise<void> {
  if (prefersReducedMotion()) {
    return Promise.resolve();
  }

  const tl = createTimeline({ defaults: { ease: EASE.in } });
  tl.add(el, {
    scale: [1, 1.03],
    duration: DURATION.quick,
    ease: EASE.out
  }).add(el, {
    translateX: direction === 'forward' ? 28 : 0,
    translateY: direction === 'forward' ? -6 : 10,
    opacity: [1, 0],
    scale: 0.97,
    duration: DURATION.base
  });

  return tl.then(() => undefined);
}

/**
 * Counts a number up/down in place (kanban column totals). Small touch, but when a card moves
 * between columns it makes both the source and destination counts visibly react, which is the
 * feedback that the move actually landed somewhere.
 */
export function animateCount(el: HTMLElement, from: number, to: number): void {
  if (from === to) {
    return;
  }

  withMotion(
    () => {
      const state = { value: from };
      animate(state, {
        value: to,
        duration: DURATION.base,
        ease: EASE.out,
        modifier: utils.round(0),
        onUpdate: () => {
          el.textContent = String(Math.round(state.value));
        }
      });
    },
    () => {
      el.textContent = String(to);
    }
  );
}
