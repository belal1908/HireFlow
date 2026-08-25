/**
 * Minimal ambient typing for the `animejs` v3 default export. @types/animejs pulled in an
 * unrelated, broken dependency chain when installed in this environment (npm's own arborist
 * tripped on a package it resolved), so this hand-written declaration is used instead — it
 * covers exactly the surface this project actually calls (see shared/animation/flash-element.ts).
 */
declare module 'animejs' {
  interface AnimeKeyframe {
    value: string | number;
    duration?: number;
    delay?: number;
    easing?: string;
  }

  interface AnimeParams {
    targets: Element | Element[] | NodeListOf<Element> | string | null;
    easing?: string;
    duration?: number;
    delay?: number;
    [property: string]: unknown;
  }

  function anime(params: AnimeParams): unknown;

  export default anime;
}
