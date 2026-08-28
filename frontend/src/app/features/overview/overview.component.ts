import { Component, OnInit, computed, inject, signal } from '@angular/core';
import { Router } from '@angular/router';
import { forkJoin, map, of, switchMap } from 'rxjs';
import { ApplicationService } from '../../core/services/application.service';
import { PostingService } from '../../core/services/posting.service';
import { AuthService } from '../../core/services/auth.service';
import { UserDirectoryService } from '../../core/services/user-directory.service';
import { ApplicationEventResponse, ApplicationResponse, ApplicationStatus, TERMINAL_STATUSES } from '../../core/models/application.model';
import { PostingResponse } from '../../core/models/posting.model';
import { extractErrorMessage } from '../../core/utils/api-error.util';
import { StatusBadgeComponent } from '../../shared/status-badge/status-badge.component';

const PIPELINE_STAGES: ApplicationStatus[] = ['APPLIED', 'SCREENING', 'INTERVIEW', 'OFFER'];

interface ActivityRow {
  actor: string;
  subject: string;
  from: ApplicationStatus | null;
  to: ApplicationStatus;
  note: string;
  date: string;
}

/**
 * `/` — the Overview page. Did not exist before this redesign (see README section 3): a
 * role-conditional dashboard built from the same real endpoints the rest of the app uses
 * (`ApplicationService.scoped()`, `PostingService.titleMap()`), not new backend surface.
 *
 * Two adaptations from the design's prototype, both because HireFlow's real data model differs
 * from the design's fictional multi-employer job board:
 *  - No "company" field exists on a posting (title + description only), so the activity log's
 *    "subject" is the posting title alone.
 *  - "Recent movement" has no dedicated backend endpoint; it's built from the N most recently
 *    updated applications in scope, each application's own latest event (a handful of real
 *    `GET /api/applications/{id}/events` calls, not a new endpoint), sorted and capped at 4 —
 *    an honest, if not perfectly scalable, way to surface a real audit trail on this page.
 */
@Component({
  selector: 'app-overview',
  standalone: true,
  imports: [StatusBadgeComponent],
  templateUrl: './overview.component.html',
  styleUrl: './overview.component.css'
})
export class OverviewComponent implements OnInit {
  private readonly applicationService = inject(ApplicationService);
  private readonly postingService = inject(PostingService);
  private readonly userDirectory = inject(UserDirectoryService);
  private readonly router = inject(Router);
  readonly authService = inject(AuthService);

  readonly loading = signal(true);
  readonly errorMessage = signal<string | null>(null);
  readonly applications = signal<ApplicationResponse[]>([]);
  readonly postings = signal<Map<number, PostingResponse>>(new Map());
  readonly activity = signal<ActivityRow[]>([]);

  readonly role = computed(() => this.authService.currentUser()?.role ?? 'CANDIDATE');

  readonly stats = computed(() => {
    const apps = this.applications();
    const active = apps.filter((a) => !TERMINAL_STATUSES.has(a.status)).length;
    const interviews = apps.filter((a) => a.status === 'INTERVIEW').length;
    const offers = apps.filter((a) => a.status === 'OFFER').length;
    return { total: apps.length, active, interviews, offers };
  });

  readonly pipeline = computed(() => {
    const apps = this.applications();
    const counts = PIPELINE_STAGES.map((status) => apps.filter((a) => a.status === status).length);
    const max = Math.max(1, ...counts);
    return PIPELINE_STAGES.map((status, i) => ({
      status,
      label: status.charAt(0) + status.slice(1).toLowerCase(),
      count: counts[i],
      pad: String(counts[i]).padStart(2, '0'),
      percent: Math.round((counts[i] / max) * 100)
    }));
  });

  readonly terminalCounts = computed(() => {
    const apps = this.applications();
    return {
      accepted: apps.filter((a) => a.status === 'ACCEPTED').length,
      rejected: apps.filter((a) => a.status === 'REJECTED').length,
      withdrawn: apps.filter((a) => a.status === 'WITHDRAWN').length
    };
  });

  readonly crumb = computed(() => {
    const date = new Date().toLocaleDateString('en-US', { month: 'short', day: '2-digit' }).toUpperCase();
    switch (this.role()) {
      case 'RECRUITER':
        return `RECRUITER DESK / ${date}`;
      case 'ADMIN':
        return `ADMIN CONSOLE / ${date}`;
      default:
        return `CANDIDATE WORKSPACE / ${date}`;
    }
  });

  readonly heroTitle = computed(() => {
    switch (this.role()) {
      case 'RECRUITER':
        return 'Your desk, this week';
      case 'ADMIN':
        return 'The whole org, one view';
      default:
        return 'Your search, in focus';
    }
  });

  readonly heroSub = computed(() => {
    switch (this.role()) {
      case 'RECRUITER':
        return 'Every application on postings you manage, with the next legal move already resolved.';
      case 'ADMIN':
        return 'Postings, applications, and the audit trail behind every state change.';
      default:
        return 'A clear view of what is moving, what needs attention, and what comes next.';
    }
  });

  readonly heroCta = computed(() => (this.role() === 'ADMIN' ? 'Review all applications' : 'View applications'));

  readonly statCards = computed(() => {
    const s = this.stats();
    const candidate = this.role() === 'CANDIDATE';
    return [
      { label: candidate ? 'MY APPLICATIONS' : 'IN SCOPE', value: s.total, hint: candidate ? 'Since you started tracking' : 'Visible under your role' },
      { label: 'ACTIVE IN MOTION', value: s.active, hint: 'Non-terminal states' },
      { label: 'INTERVIEWS', value: s.interviews, hint: 'Moments to prepare for' },
      { label: 'OFFERS', value: s.offers, hint: candidate ? 'Yours to accept' : 'Awaiting candidate decision' }
    ];
  });

  readonly nudge = computed(() => {
    const role = this.role();
    const s = this.stats();

    if (role === 'RECRUITER') {
      return {
        kicker: 'NEEDS YOUR MOVE',
        title: 'Offers are the candidate’s call.',
        body: 'You can advance and reject, but OFFER → ACCEPTED belongs to the candidate. That asymmetry is deliberate.',
        cta: 'Review active applications',
        route: '/applications'
      };
    }
    if (role === 'ADMIN') {
      return {
        kicker: 'ADMIN NOTE',
        title: 'You cannot move candidates.',
        body: 'Admin owns postings and users. Pipeline transitions stay with recruiters and candidates, so the audit trail always names a real actor.',
        cta: 'Open posting management',
        route: '/postings'
      };
    }
    if (s.offers > 0) {
      return {
        kicker: 'GOOD NEXT STEP',
        title: 'An offer is waiting on you.',
        body: 'Accepting is a transition only you can make — the recruiter cannot make it for you.',
        cta: 'Review active applications',
        route: '/applications'
      };
    }
    if (s.active > 0) {
      return {
        kicker: 'IN PROGRESS',
        title: 'Your pipeline is moving.',
        body: 'Nothing needs a decision from you right now — check back as recruiters advance your applications.',
        cta: 'Review active applications',
        route: '/applications'
      };
    }
    return {
      kicker: 'GET STARTED',
      title: 'Find your next place.',
      body: 'Browse open roles and apply — your first application starts in APPLIED.',
      cta: 'Browse open roles',
      route: '/postings'
    };
  });

  ngOnInit(): void {
    this.load();
  }

  goApplications(): void {
    this.router.navigateByUrl('/applications');
  }

  navigate(route: string): void {
    this.router.navigateByUrl(route);
  }

  postingTitle(jobPostingId: number): string {
    return this.postings().get(jobPostingId)?.title ?? `Posting #${jobPostingId}`;
  }

  private load(): void {
    this.loading.set(true);
    this.errorMessage.set(null);
    const role = this.role();

    forkJoin({
      applications: this.applicationService.scoped(role),
      postings: this.postingService.titleMap(),
      directory: this.userDirectory.ensureLoaded()
    })
      .pipe(
        switchMap(({ applications, postings }) => {
          this.applications.set(applications);
          this.postings.set(postings);

          const recent = [...applications].sort((a, b) => b.updatedAt.localeCompare(a.updatedAt)).slice(0, 5);
          if (recent.length === 0) {
            return of([] as ActivityRow[]);
          }
          return forkJoin(recent.map((app) => this.applicationService.events(app.id).pipe(map((events) => ({ app, events }))))).pipe(
            map((results) => this.buildActivity(results))
          );
        })
      )
      .subscribe({
        next: (activity) => {
          this.activity.set(activity);
          this.loading.set(false);
        },
        error: (err) => {
          this.errorMessage.set(extractErrorMessage(err));
          this.loading.set(false);
        }
      });
  }

  private buildActivity(results: Array<{ app: ApplicationResponse; events: ApplicationEventResponse[] }>): ActivityRow[] {
    const latestPerApp = results
      .map(({ app, events }) => {
        const latest = [...events].sort((a, b) => b.timestamp.localeCompare(a.timestamp))[0];
        return latest ? { app, event: latest } : null;
      })
      .filter((x): x is { app: ApplicationResponse; event: ApplicationEventResponse } => x !== null)
      .sort((a, b) => b.event.timestamp.localeCompare(a.event.timestamp))
      .slice(0, 4);

    return latestPerApp.map(({ app, event }) => ({
      actor: this.actorLabel(event.changedBy),
      subject: this.postingTitle(app.jobPostingId),
      from: event.fromStatus,
      to: event.toStatus,
      note: event.note || 'No note recorded.',
      date: new Date(event.timestamp).toLocaleDateString('en-US', { month: 'short', day: '2-digit' })
    }));
  }

  private actorLabel(userId: number): string {
    if (userId === this.authService.currentUser()?.id) {
      return 'You';
    }
    return this.userDirectory.emailFor(userId) ?? `User #${userId}`;
  }
}
