import anime from 'animejs';

/**
 * One small, deliberately restrained use of anime.js: briefly highlight a table row after its
 * status changes (a successful transition/withdraw/accept), so the update reads as "this row
 * just changed" instead of silently re-rendering. Not used anywhere else — this app is about
 * the Angular patterns (guards/interceptor/standalone components), not a motion showcase.
 * No-ops safely if the element isn't in the DOM (e.g. during unit tests without a real render).
 */
export function flashElement(elementId: string): void {
  const el = document.getElementById(elementId);
  if (!el) {
    return;
  }
  anime({
    targets: el,
    backgroundColor: [
      { value: 'rgba(36, 84, 255, 0.18)', duration: 1 },
      { value: 'rgba(36, 84, 255, 0)', duration: 900 }
    ],
    easing: 'easeOutQuad'
  });
}
