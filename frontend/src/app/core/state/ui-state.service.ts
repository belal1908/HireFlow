import { Injectable, signal } from '@angular/core';
import { ApplicationStatus } from '../models/application.model';

/**
 * Tiny app-wide UI state that isn't worth a full feature service:
 *  - the "Inspect permissions" toggle, which lives in the shell header (shared/shell) but is
 *    read by the Applications page to show/hide the denied-transitions panel;
 *  - `lastSelectedStatus`, the status of whichever application was last selected on the
 *    Applications page. The design's prototype is a single-page app with one shared `selected`
 *    application that the State Machine page's "LIT: <STATUS>" indicator mirrors live; this app
 *    is multi-route, so this one small piece of cross-page state reproduces that "mirrors the
 *    currently selected application" behavior without threading the whole application list
 *    through a shared store.
 *
 * Per the design README's Interactions section: "default on in the prototype; default off in
 * production, or expose it only in a demo build." This is the real app, so Inspect defaults off.
 */
@Injectable({ providedIn: 'root' })
export class UiStateService {
  readonly inspectPermissions = signal(false);
  readonly lastSelectedStatus = signal<ApplicationStatus | null>(null);

  toggleInspectPermissions(): void {
    this.inspectPermissions.update((v) => !v);
  }
}
