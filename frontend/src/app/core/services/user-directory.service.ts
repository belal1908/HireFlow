import { Injectable } from '@angular/core';
import { Observable, map, of, shareReplay, tap } from 'rxjs';
import { AuthService } from './auth.service';
import { UserService } from './user.service';

/**
 * Best-effort user-id -> email lookup, used to show a real candidate email instead of a bare
 * numeric id in the Applications list, the Overview activity log, and audit-trail actor lines.
 *
 * This only works for ADMIN: `GET /api/admin/users` (the only endpoint that can list users by
 * id) is `@PreAuthorize("hasRole('ADMIN')")`, so a RECRUITER has no API path to resolve another
 * user's email at all — the design's mockup shows candidate emails everywhere, but HireFlow's
 * real contract doesn't expose that to RECRUITER. Rather than fake it, RECRUITER views fall back
 * to "Candidate #<id>" / "User #<id>", which is what the API actually gives that role (this
 * mirrors the pre-redesign recruiter dashboard's existing behavior). Documented as a known,
 * intentional gap in the redesign's final report rather than worked around.
 */
@Injectable({ providedIn: 'root' })
export class UserDirectoryService {
  private readonly byId = new Map<number, string>();
  private loaded$: Observable<void> | null = null;

  constructor(
    private authService: AuthService,
    private userService: UserService
  ) {}

  /** Loads (once, cached) the full user list if the current user is ADMIN; a no-op otherwise. */
  ensureLoaded(): Observable<void> {
    if (!this.authService.hasRole('ADMIN')) {
      return of(undefined);
    }
    if (!this.loaded$) {
      this.loaded$ = this.userService.list(undefined, 0, 1000).pipe(
        tap((page) => page.content.forEach((u) => this.byId.set(u.id, u.email))),
        map(() => undefined),
        shareReplay(1)
      );
    }
    return this.loaded$;
  }

  emailFor(userId: number): string | null {
    return this.byId.get(userId) ?? null;
  }
}
