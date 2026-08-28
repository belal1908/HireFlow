# HireFlow

![build](https://github.com/belal1908/HireFlow/actions/workflows/build.yml/badge.svg)

**Live demo**: [hireflow-frontend-zg69.onrender.com](https://hireflow-frontend-zg69.onrender.com)
(backend: [hireflow-backend-c3mv.onrender.com](https://hireflow-backend-c3mv.onrender.com)) — free
tiers throughout (Render web service + static site, Neon Postgres). The backend's free web service
spins down after 15 minutes idle, so the **first request after a while can take 30-50s to wake it
up** — a known, expected tradeoff of a free deploy, not a bug. Log in as `admin@hireflow.demo` /
`DemoAdmin2026!` to see the ADMIN view, or register a new account to see the CANDIDATE flow.

A role-aware recruiting pipeline for small teams: candidates apply, recruiters move applications
through a strictly-enforced state machine, admins manage postings, and every status change is
written to an append-only audit log. Built to exercise real Spring Security (JWT + method-level
`@PreAuthorize`, ownership checks, refresh-token rotation) and a genuinely tested state machine —
not another CRUD demo.

It's all here: a working Spring Boot backend, an Angular frontend that consumes it, a Playwright
e2e suite that exercises all three roles (positive and negative cases) against the real running
stack, a full-stack Docker Compose setup, a GitHub Actions CI workflow, and a real free-tier live
deploy. See "End-to-end tests", "Full stack via Docker Compose", "Live deploy", and "Continuous
integration" below for what's actually been verified.

## Tech stack

**Backend**
- Java 21, Spring Boot 3.5 (Web, Data JPA, Security, Validation)
- PostgreSQL (Testcontainers for integration tests, Docker Compose for local dev)
- JWT access + refresh tokens via `jjwt`, BCrypt password hashing
- JUnit 5, Spring Security Test, Testcontainers
- Maven, Lombok

**Frontend**
- Angular 18+, standalone components throughout (no NgModules)
- A custom design system ("ApplyTrack" — warm cream/ink-navy/amber palette, Plus Jakarta Sans +
  IBM Plex Mono, CSS custom properties as the single source of truth) — **not** Angular Material's
  default M3 theming. Angular Material is still used underneath real form controls
  (`MatFormField`/`MatInput`/`MatSelect`) and `MatPaginator`, restyled via global overrides; the
  applications list, postings grid, sidebar/header shell, stepper, state-machine graph, and modals
  are plain custom components. See "Frontend" below for the full account of this redesign.
- Functional route guards (`CanActivateFn`) and a functional `HttpInterceptorFn` for auth
- RxJS for the token-refresh coordination; no state-management library — not needed at this scope
- anime.js v4, retained in the codebase (`shared/animation/motion.ts`) but not used by the
  redesigned pages — the current design calls for near-zero motion (see "Motion" below)
- Karma/Jasmine for unit tests (the scaffolded default)

## Project layout

```
com.hireflow
├── user           entity/repository/dto for User + Role
├── auth           register/login/refresh — controller, service, RefreshToken entity+repo, DTOs
├── posting        JobPosting — controller, service, repository, DTOs
├── application
│   ├── transition   TransitionValidator + TransitionResult + ApplicationStatus (framework-free)
│   ├── entity        Application, ApplicationEvent (audit log)
│   ├── controller/service/repository/dto
├── security        JWT filter/service, SecurityConfig, CustomUserDetails, SecurityUtils
└── common          GlobalExceptionHandler, ApiError, domain exceptions
```

## The state machine

```
APPLIED → SCREENING → INTERVIEW → OFFER → ACCEPTED   (forward: RECRUITER only, one step at a time)
                                        ↘ REJECTED     (from any non-terminal state: RECRUITER only)
   WITHDRAWN   (from any non-terminal state: CANDIDATE only, owner only)
   ACCEPTED    (from OFFER only: CANDIDATE only, owner only)

Terminal states: ACCEPTED, REJECTED, WITHDRAWN — no transitions out, ever, for anyone (including ADMIN).
```

`TransitionValidator` (`com.hireflow.application.transition`) is a plain Java class with **no
Spring/JPA imports** — a pure function `(currentStatus, targetStatus, actorRole) -> TransitionResult`,
where `TransitionResult` carries `allowed` plus a human-readable denial `reason`. It is wired into
Spring as a bean via a separate `@Configuration` class so the validator itself stays framework-free
and trivially unit-testable.

It's the most heavily tested piece of the project:
- `TransitionValidatorExhaustiveTest` checks **all 147** combinations of (7 statuses × 7 statuses ×
  3 roles) against a hand-written set of the 12 legal transitions — written independently of the
  implementation, not derived from it, so the test can't become a tautology.
- `TransitionValidatorPositiveTest` — one explicit test per legal transition.
- `TransitionValidatorNegativeTest` — wrong role, wrong starting state, skipped steps, backward
  moves, terminal states, self-transitions, and null inputs.

ADMIN has no transition rights at all under this model — every ADMIN attempt is denied by the
validator (see `adminIsNeverAllowedAnyTransition`).

## API summary

| Method | Path | Access | Notes |
|---|---|---|---|
| POST | `/api/auth/register` | public | Candidate self-registration; role is always forced to `CANDIDATE` server-side, regardless of anything the client sends |
| POST | `/api/auth/login` | public | Returns `{ accessToken, refreshToken }` |
| POST | `/api/auth/refresh` | public | Rotates the refresh token; the old one is immediately revoked (replay fails) |
| POST | `/api/auth/password-reset/request` | public | Body: `{ email }`. Always 200; see "Password reset" below for why the token is in the response body |
| POST | `/api/auth/password-reset/confirm` | public | Body: `{ token, newPassword }`. Single-use, TTL-bound; also revokes every active refresh token for that account |
| POST | `/api/auth/email-verification/request` | public | Body: `{ email }`. Always 200; null token if unknown email *or* already verified — see "Email verification" below |
| POST | `/api/auth/email-verification/confirm` | public | Body: `{ token }`. Single-use, TTL-bound (24h default) |
| GET | `/api/postings` | authenticated | ADMIN sees OPEN + CLOSED; everyone else sees OPEN only |
| POST | `/api/postings` | ADMIN | Creates a posting (starts OPEN) |
| PATCH | `/api/postings/{id}` | ADMIN | Partial update (title/description/status) |
| POST | `/api/applications` | CANDIDATE | Apply to a posting; creates in `APPLIED`; unique per (candidate, posting); 409 on duplicate or closed posting |
| GET | `/api/applications/mine` | CANDIDATE | Own applications only |
| GET | `/api/applications` | RECRUITER, ADMIN | All applications; filter with `?postingId=` and/or `?status=` |
| PATCH | `/api/applications/{id}/status` | authenticated, gated by `TransitionValidator` | Body: `{ targetStatus, note? }`. Ownership (a CANDIDATE acting on someone else's application) is a 403; a transition the validator denies (wrong role for that target, wrong state, skip, terminal) is a 400 — see "Design notes" below |
| GET | `/api/applications/{id}/events` | RECRUITER, ADMIN, or the owning CANDIDATE | Full audit trail for one application |
| POST | `/api/admin/users` | ADMIN | Creates a RECRUITER or ADMIN account. Body: `{ email, password, role }`; `role: CANDIDATE` is rejected with 400 (that path is `POST /api/auth/register`) |
| GET | `/api/admin/users` | ADMIN | Lists all users (any role); optional `?role=` filter |

Every non-public endpoint is gated with `@PreAuthorize` (role and/or `isAuthenticated()`), with
ownership enforced in the service layer where the role alone isn't enough to decide access (a
CANDIDATE must never read or act on another candidate's `Application`). This has dedicated,
passing tests: `candidateCannotTransitionAnotherCandidatesApplication_returns403` and
`candidateCannotViewAnotherCandidatesApplicationEvents_returns403` in `ApplicationIntegrationTest`.

### Design note: 403 vs 400 on the status endpoint

`PATCH /api/applications/{id}/status` accepts a request from any authenticated user and asks
`TransitionValidator` whether that specific (current status, target status, role) triple is legal.
A denial — whether the *cause* is a wrong role, a skipped step, a terminal state, or anything else
— is surfaced as **400 Bad Request** with the validator's own reason string in the body, since the
validator is standing in for `@PreAuthorize` here — it's the single source of truth for "is this
role-gated transition allowed." Ownership violations (acting on *someone else's* application) are a
separate concern from the state machine and are enforced first, as **403 Forbidden**. Every other
role-gated endpoint (postings, apply, list-all, mine) uses `@PreAuthorize(hasRole(...))` directly
and returns a plain 403 on the wrong role, before the request body is even looked at.

### Admin user management

`POST /api/admin/users` and `GET /api/admin/users` (`com.hireflow.admin` —
`AdminUserController`/`AdminUserService`) close a real bootstrap gap: the first RECRUITER/ADMIN
accounts no longer need a hand-written `UPDATE users SET role = ...` — an existing ADMIN can create
them through the API or the `/admin/users` screen instead. Scope is deliberately narrow, matching
the gap it closes:

- **Create only the two roles self-registration can't produce.** `role: CANDIDATE` is rejected
  with 400 — `POST /api/auth/register` already owns that path, and creating a second way to make
  a `CANDIDATE` account would just be two sources of truth for the same thing.
- **Validation mirrors `AuthService#register`** (normalized/lowercased email, `@Email`, password
  `@Size(min = 8, max = 100)`, BCrypt hash, 409 on a duplicate email) but does **not** call into
  `AuthService` directly, since that method's whole contract is "always forces CANDIDATE" — the
  opposite of what this endpoint needs.
- **Create + list only.** No role-change, deactivate, or delete endpoints. A user has exactly one
  role, assigned at creation, with no self-service role changes — that's about a user changing
  *their own* role, and doesn't imply an admin should be able to change *anyone's* role after the
  fact, so that was left out rather than guessed at.
- `GET /api/admin/users` supports an optional `?role=` filter (`UserRepository#findByRole`) so an
  admin can check who already has RECRUITER/ADMIN access before creating a duplicate account.

`AdminUserIntegrationTest` (15 tests, same `AbstractIntegrationTest`/Testcontainers-Postgres base
as the other integration suites) covers: an ADMIN creating a RECRUITER account, that account
showing up in both the unfiltered and `?role=`-filtered list, and — chained in the same test —
that account actually logging in and successfully calling a RECRUITER-gated endpoint
(`GET /api/applications`), proving the created account is real, not just a 201 response. Also
covered: creating an ADMIN account; CANDIDATE and RECRUITER callers get 403; unauthenticated gets
401; `role: CANDIDATE` gets 400; missing/invalid role gets 400; duplicate email (including one
already taken by a self-registered CANDIDATE) gets 409; invalid email and short password get 400.

**Bootstrapping the very first admin.** `POST /api/admin/users` itself requires an existing ADMIN
caller, which leaves an unavoidable chicken-and-egg gap for the first one. `AdminBootstrapRunner`
(`com.hireflow.admin`, a `CommandLineRunner`) closes it: on startup, if both `ADMIN_BOOTSTRAP_EMAIL`
and `ADMIN_BOOTSTRAP_PASSWORD` are set (see `.env.example`) **and** no ADMIN account exists yet, it
creates one with a BCrypt-hashed password. It's deliberately narrow — opt-in (unset by default, so
existing setups are unaffected), and it never overwrites an existing admin's password or runs again
once one ADMIN exists, so restarting a running deployment with the vars still set is harmless. If
you'd rather not set env vars, the escape hatch documented for this project's own test seeding still
works: register a CANDIDATE normally, then `UPDATE users SET role = 'ADMIN' WHERE email = '...';`
directly in Postgres. Covered by `AdminBootstrapRunnerTest` (unit-level, no Spring context — mirrors
`RateLimitingFilterTest`'s style): both vars unset, only one set, an ADMIN already existing, the
target email already belonging to a non-admin account, and the success path (normalizes/lowercases
the email, sets role ADMIN, and the stored hash actually verifies against the raw password).

### Password reset

Closes another of this README's own documented gaps ("No password reset ... registration and
login only"), with one deliberate simplification stated plainly rather than hidden.

`POST /api/auth/password-reset/request` (body: `{ email }`) generates a single-use,
time-limited token (`hireflow.password-reset.token-ttl-minutes`, default 30, env
`PASSWORD_RESET_TOKEN_TTL_MINUTES`) and — **this is the simplification** — returns it directly in
the response body (`resetToken`, `expiresAt`) instead of emailing it. There is no SMTP
infrastructure anywhere in this project (no `spring-boot-starter-mail`, no mail config), and this
is the "dev-style" tradeoff, chosen explicitly over adding that infrastructure. **This makes the
endpoint categorically unsafe for a real deployment**: proving
you control the target inbox is the entire security property a password reset flow exists to
provide, and handing the token straight back in the response — to whoever called the endpoint, not
whoever owns the email — provides none of it. A real deployment must replace this with an actual
email send and drop the token from the response before this endpoint could ever be exposed publicly.
The endpoint still always returns 200 rather than 404 for an unrecognized email (mirroring how a
real email-based flow would behave, to keep the shape honest even though the token's presence in
this dev version already reveals whether the account exists — a real version, without the token in
the body, would not have that particular leak).

`POST /api/auth/password-reset/confirm` (body: `{ token, newPassword }`) is otherwise built exactly
like a real one: the token is only ever stored as a SHA-256 hash (same technique as refresh
tokens — see `RefreshToken`/`PasswordResetToken`), is single-use (`used` flips to `true` the
instant it's redeemed, independent of TTL), and — since a password reset is exactly the moment an
account's existing sessions might be the actual problem (leaked credentials) — every refresh token
the account currently holds is revoked at the same time, not just the password changed. Both
endpoints are covered by `RateLimitingFilter` (the request side is an enumeration/spam target, the
confirm side is a token-guessing target).

Covered by 12 new cases in `AuthIntegrationTest`: token issued for an existing account vs. null for
an unknown one; invalid request email → 400; a valid confirm actually changes the password (old
password stops working, new one works); confirm revokes existing refresh tokens (proven by
attempting to use one afterward and getting 401, not just asserting a DB flag); garbage/unknown
token → 400; reusing an already-redeemed token → 400; an expired token → 400 (the test backdates
`expiresAt` directly via the repository — there's no other way to fast-forward 30 minutes in a
test); a too-short new password → 400, with an explicit check that a token rejected by validation
*before* the service method ever ran was not incorrectly marked used.

**Frontend.** `ForgotPasswordComponent` (`/forgot-password`) and `ResetPasswordComponent`
(`/reset-password?token=...`) wire up the two endpoints; the login page's "Forgot password?" link
(previously a static "No reset flow" label) now points at the first one. The dev-mode token isn't
hidden from the user or buried in devtools — `ForgotPasswordComponent` shows it in an explicit
amber "DEV MODE — NO EMAIL SERVER CONFIGURED" callout with a one-click link straight into
`ResetPasswordComponent` (which pre-fills the token from the `?token=` query param, though the
field stays editable). Verified manually end-to-end in a real browser against the running dev
stack: register → forgot password → dev-mode token shown → follow the link → set a new password →
old password rejected on login → new password succeeds.

### Email verification

Closes the README's last remaining self-contained gap ("No email verification"). Structurally
identical to password reset (a separate `EmailVerificationToken` table, same SHA-256-hash /
single-use / TTL-bound shape) and carries the exact same dev-mode tradeoff: no SMTP infrastructure
exists in this project, so `POST /api/auth/email-verification/request` (body `{ email }`) returns
the raw token directly in the response instead of emailing it — **not production-safe** for the
same reason password reset isn't (see that section above for the full explanation; it applies here
verbatim). The one difference: this endpoint collapses *two* "nothing to do" cases into the same
null-token response — no account for that email, **or** the account is already verified — so it
never reveals either an account's existence or its verification status to an unauthenticated
caller. `POST /api/auth/email-verification/confirm` (body `{ token }`) marks the account verified;
token TTL defaults to 24h (`hireflow.email-verification.token-ttl-hours`,
`EMAIL_VERIFICATION_TOKEN_TTL_HOURS`) — longer than password reset's 30 minutes, since a
verification link carries none of the same urgency as an in-progress password reset.

`User.emailVerified` defaults to `false` for self-registration (`AuthService#register`) but `true`
for accounts an ADMIN creates (`AdminUserService#createUser`) and for the bootstrap admin
(`AdminBootstrapRunner`) — both are vouched for by an existing admin already, not self-service, so
there's nothing to prove. The flag rides along as an `emailVerified` claim on the access-token JWT
(`JwtService#generateAccessToken`) rather than a separate `/me` endpoint, consistent with how the
frontend already treats the JWT as the single source of truth for `id`/`email`/`role` — see
`core/models/user.model.ts`'s existing comment on `DecodedAccessToken`. This does **not** gate
login or any other action; it's informational only, matching how most real products treat it (nag,
don't block) and — just as importantly — keeping this change from touching a single `@PreAuthorize`
rule or the existing e2e suite's assumption that registration is immediately followed by full
access.

**Frontend.** No new route: the prompt lives inline in the `/settings` Profile card (unlike
password reset, this flow doesn't need a separate confirm page reached via a link, since the user
requesting it is already authenticated) — a VERIFIED/NOT VERIFIED pill, and for an unverified
account, a "Send verification token" button that reveals the same dev-mode callout pattern as
`ForgotPasswordComponent`, with the confirm button right there since the token is already known
client-side. Confirming calls `authService.refreshAccessToken()` immediately after, so the pill
flips to VERIFIED without a full re-login — otherwise the stale JWT from before verification would
keep showing NOT VERIFIED until the access token happened to expire on its own.

Covered by 10 new cases across `AuthIntegrationTest`: a self-registered account's JWT claim starts
`false`; an admin-created account's starts `true` (created via the real `POST /api/admin/users`
path, then logged in, to prove the claim end-to-end rather than asserting a DB column); request for
an existing unverified account returns a token; unknown email and already-verified account both
return a null token; invalid request email → 400; a valid confirm flips the claim on the *next*
login; garbage/unknown token, reusing an already-used token, and an expired token (backdated via
the repository, same technique as password reset) all → 400. Verified manually end-to-end in a
real browser too: register → Settings shows NOT VERIFIED → send token → dev-mode callout → confirm
→ pill flips to VERIFIED with no page reload.

### Device-bound refresh tokens

Closes the last of this README's "Known gaps": refresh tokens were hashed but otherwise
unconstrained — a leaked token would work from anywhere, forever, until it expired or was rotated.
`RefreshToken` now also records `userAgent` and `issuedIp` at issuance (`AuthService#issueTokenPair`),
and `POST /api/auth/refresh` checks both — but treats them very differently:

- **User-Agent is enforced.** A refresh request whose `User-Agent` header doesn't match the one
  recorded at login/issuance is rejected with 401, **and the token is revoked immediately** — not
  just denied. A mismatch means this specific token is already compromised from the legitimate
  holder's perspective too (either it leaked, or something odd is going on), so the fix is to burn
  it and force a real re-login, not to let the "wrong" caller retry. This is deliberately treated
  with the same severity as replaying an already-rotated token (see `AuthService#refresh`).
- **IP is recorded but explicitly NOT enforced.** A legitimate client's IP changes constantly —
  mobile networks switching towers, VPNs, ISP-level rotation — far more often than its User-Agent
  does. Hard-blocking on IP would lock out real users more often than it would stop an attacker, so
  a mismatch is only logged (`AuthService#refresh`'s WARN line), not rejected. This is the
  documented tradeoff, not an oversight.
- **Neither is a strong security boundary.** A `User-Agent` header is just a string the client
  sends — trivial to spoof for anyone who already has the token. What this actually raises is the
  bar against *casual* replay (a stolen token pasted into a different browser without also copying
  headers), not against a targeted attacker. It's defense-in-depth on top of hashing + rotation,
  not a replacement for either.
- **Backward compatible with existing rows.** `userAgent`/`issuedIp` are nullable at the database
  level (`ddl-auto: update` can't backfill a `NOT NULL` column onto a populated table), but the
  application always sets both when creating a token, and treats a `null` (a token issued before
  this feature existed) as an automatic User-Agent mismatch on its next refresh — it fails closed,
  forcing a fresh login, rather than silently trusting an unbound legacy token.

**A real bug this surfaced while building it**: the mismatch branch revokes the token and then
throws `BadCredentialsException` to return 401 — but `@Transactional`'s default behavior is to roll
back the whole method on any unchecked exception, which was silently undoing that revocation the
instant the exception propagated (caught by `refresh_withDifferentUserAgent_returns401_andRevokesTheToken`,
which found the token could still be reused a second time from the original device — the write
just never survived to be committed). Fixed with `@Transactional(noRollbackFor = BadCredentialsException.class)`
on `refresh()`.

Covered by 3 new cases in `AuthIntegrationTest`: mismatched User-Agent → 401, and the token is
provably dead afterward (a *second* refresh attempt, this time with the *correct* original
User-Agent, also gets 401 — proving revocation actually persisted, not just asserting a flag);
matching User-Agent → succeeds; and a refresh from a different IP than the one recorded at login
succeeds (proving IP is genuinely not enforced, not just untested).

### Résumé storage

`POST/GET /api/applications/{id}/resume` let a candidate attach a PDF to their own application
and let recruiters/admins read it back. Only *metadata* (original filename, size, content type,
upload timestamp) lives on the `Application` row; the bytes go to disk under
`hireflow.resume.storage-dir` (default `./data/resumes`, gitignored), not into a BLOB column, so
the database stays small and downloads stream rather than being loaded into memory.

Two deliberate choices worth calling out:

- **The client's filename never touches the filesystem path.** Every file is written under a
  freshly generated UUID; the original name is kept purely as display metadata and for the
  `Content-Disposition` header on download. A filename like `../../etc/passwd` therefore cannot
  influence where anything lands. `ResumeStorageService` re-checks the resolved path is still
  inside the storage directory as a second line of defence.
- **PDF is verified by magic bytes, not just the declared content type.** A client can claim any
  `Content-Type`; the first bytes of the upload are checked against `%PDF-` as well. Oversized
  uploads (>5MB by default, `RESUME_MAX_SIZE_BYTES`) are rejected with 400.

**Known limitations** (single-instance local disk, stated plainly rather than implied):
`docker-compose.yml` mounts a named volume at `/app/data/resumes` so files survive the backend
container being recreated — but this is still per-instance storage. Multiple horizontally-scaled
backend replicas would each see only their own uploads; that needs shared storage (NFS, S3, or
similar), which is out of scope here. The Docker image also has to create that directory and hand
it to the non-root runtime user at build time: the app calls `Files.createDirectories()` during
startup, and when `/app` was left root-owned the container exited before Tomcat ever bound a port.

## Logging & request correlation

Closes (partially — see "Known gaps" below) the README's own long-standing "no structured
logging/observability yet" gap. Two pieces:

- **Request correlation.** `RequestIdFilter` (`com.hireflow.common.logging`, first in the filter
  chain — ahead of `RateLimitingFilter` and `JwtAuthenticationFilter`, see `SecurityConfig`) assigns
  a fresh UUID to every request, puts it in the SLF4J MDC under `requestId`, and echoes it back as
  the `X-Request-Id` response header. `logging.pattern.console` (`application.yml`) renders
  `%X{requestId}` into every log line, so every line emitted while handling one request — across
  every class, not just the controller — can be grepped out of the log by that one ID, and a client
  bug report ("it broke, here's the response header") maps straight back to server-side log lines.
- **Key security/business events**, at the points that had no signal at all before this: login
  success/failure and refresh-token replay attempts (`AuthService`) — reused/rotated refresh tokens
  in particular are the actual replay-attack signal, not just "expired"; rate-limit trips
  (`RateLimitingFilter`); rejected JWTs, at DEBUG since an expired token on an idle tab is routine,
  not a security event (`JwtAuthenticationFilter`); elevated-privilege account creation, with which
  admin created it (`AdminUserService`); and — the one that mattered most, since it was a genuine
  blind spot — **unexpected exceptions**, which `GlobalExceptionHandler`'s catch-all previously
  turned into a generic 500 with zero trace of what actually happened. Every other branch in that
  class maps an already-meaningful domain exception straight to its status and deliberately does
  *not* log (that would just be noise); the catch-all is the only one that does, and
  `GlobalExceptionHandlerTest` is a regression guard for that log line existing at all, using a
  Logback `ListAppender` attached directly to the class's logger.

Application-level status transitions (`Application`/`ApplicationEvent`) are **not** duplicated here
— they already have a durable, queryable audit trail in Postgres (see "The state machine" above),
and a second copy in the log stream would just be redundant.

## Frontend

`frontend/` is a standalone-components Angular app. The original goal was to demonstrate three
specific Angular patterns well, not to be a design showcase — that's still true of the patterns
themselves, which the ApplyTrack redesign below left untouched:

- **`AuthService`** (`core/services/auth.service.ts`) — holds the access + refresh tokens in
  memory only (never `localStorage`), so an XSS payload can't read a token that was never written
  to storage. The trade-off, stated plainly: a full page reload loses the session. A production
  system would move the refresh token into a backend-set `httpOnly` cookie instead; that's a
  backend change that wasn't worth making for this project's scope. `refreshAccessToken()` shares one in-flight
  `/api/auth/refresh` call (`shareReplay(1)`) across any number of concurrent 401s, since the
  backend burns each refresh token on use — without sharing, N simultaneous 401s would rotate the
  token N times and invalidate all but the last.
- **`authInterceptor`** (`core/interceptors/auth.interceptor.ts`) — a functional
  `HttpInterceptorFn` that attaches `Authorization: Bearer <token>` to every non-auth request, and
  on a 401 does exactly one silent refresh-and-retry via the shared call above; if the refresh
  itself fails, it logs out and redirects to `/login` instead of looping.
- **`authGuard` / `roleGuard(...roles)`** (`core/guards/`) — functional `CanActivateFn`s. Not
  logged in → redirect to `/login`; logged in with the wrong role → redirect to `/forbidden`. This
  is a real navigation-blocking guard, not hidden UI — a direct URL hit by a wrong-role user never
  activates the component. Both branches are unit-tested (`role.guard.spec.ts`,
  `auth.guard.spec.ts`).

### Design system (redesign)

The frontend was rebuilt to match a high-fidelity design commissioned under the working name
"ApplyTrack" — a new visual design system **and** a restructured page layout. This replaces the
earlier Material-default (M3 "Azure/Blue" prebuilt theme) frontend entirely; nothing in the app
still renders with Material's stock colors.

**Design tokens** — CSS custom properties in `src/styles.css`, one source of truth every component
reads from rather than hardcoding hex values (the same approach `StatusBadgeComponent` already
used, extended app-wide):

| Token | Value | Use |
|---|---|---|
| `--bg` | `#f2efe8` | Page background (warm cream) |
| `--surface` | `#fbfaf7` | Cards, list rows, inputs |
| `--ink` | `#152232` | Text, sidebar, primary buttons, dark panels |
| `--accent` | `#f9c46b` | Active nav, logo mark, selected-row tint |
| Status colors | `#64748b`/`#0284c7`/`#d97706`/`#7c3aed`/`#16a34a`/`#dc2626`/`#78716c` | one per `ApplicationStatus` enum value (APPLIED → WITHDRAWN) |
| Fonts | Plus Jakarta Sans (400–800) + IBM Plex Mono (400–600) | UI/headings + labels/enums/code |
| Radii | 14px cards, 16px dark hero panels, 10px buttons/inputs, 999px pills | — |
| Shadow | `0 30px 70px rgba(15,25,38,.32)` | the **only** shadow in the system — the transition confirm-sheet modal; everything else uses a border, never elevation |

**Angular Material** stays wired in, but only where it's still the right tool: `MatFormField`/
`MatInput` (login/register/settings-admin text fields), `MatSelect` (role dropdowns), and
`MatPaginator` (Postings, `/admin/users`) — all restyled via global `.mat-mdc-*` overrides in
`styles.css` rather than left in M3 defaults. Everywhere Material's opinionated shape/elevation
system fought the design more than it helped — the applications list, the postings grid, the
sidebar/header shell, the 5-step pipeline stepper, the state-machine graph, the transition
confirm-sheet and posting-editor modals — those are plain custom components instead, the same
pattern `StatusBadgeComponent`/`PipelineProgressComponent` already used successfully pre-redesign.

**Route / page structure.** The old per-role page split is gone; every authenticated route now
renders role-adaptive content instead of being a separate page per role:

| Route | What it is | Role behavior |
|---|---|---|
| `/login`, `/register` | Restyled two-pane cream/navy auth screens | unchanged auth logic; no demo role picker — role comes from the real JWT |
| `/forgot-password`, `/reset-password` | **New** — password reset request/confirm, same two-pane auth layout | public; see "Password reset" above, including the explicit dev-mode token callout |
| `/` | **New** — Overview dashboard: stat row, pipeline-shape bars, a role-specific nudge panel, recent-activity log | copy, stats, and nudge content are role-conditional |
| `/applications` | Merges the old `MyApplicationsComponent` + `ApplicationsDashboardComponent` | CANDIDATE sees only their own (`GET /api/applications/mine`); RECRUITER/ADMIN see all, with search/status-filter. Master-detail: list + a sticky detail panel with the stepper, an "available to you as `<ROLE>`" action row, an optional "Inspect permissions" denied-transitions panel, and the audit trail. Actions open a transition confirm-sheet (note field, optional) before issuing the real `PATCH` — the pre-redesign UI fired transitions immediately on click, this one doesn't. |
| `/state-machine` | **New** — a visual graph of the happy path + terminal branches, mirroring `TransitionValidator` | node counts reflect the current role's visible-application scope |
| `/postings` | Merges the old candidate browse view + admin manage view | ADMIN gets create/edit/close; CANDIDATE gets Apply; RECRUITER gets "View pipeline" (deep-links into `/applications?postingId=`). Non-admins get a dashed "Call POST /api/postings as `<ROLE>` →" button that issues a **real** API request and renders the genuine 403 response — not a simulated one. |
| `/settings` | **New**, plus the new home for admin user management | Profile card (read-only) + a Transition matrix card whose ALLOW/DENY cells are computed live from the same ported validator logic, not hardcoded. ADMIN gets an additional card linking to `/admin/users` (still guarded, restyled, unchanged logic). |

`roleGuard('ADMIN')` now protects exactly one route, `/admin/users` — the only page that's still
genuinely role-exclusive; everywhere else, `authGuard` alone is enough and the component itself
decides what to render for the current role. The old `homeRedirectGuard` (which sent `/` to a
per-role landing page) is gone, since `/` is a real page now.

**Client-side transition validator.** `shared/transition/transition-validator.ts` is a faithful
TypeScript port of the backend's `TransitionValidator` (same terminal-state check, same
forward-path table, same per-target role rule), used only to decide which action buttons render
and what the "Inspect permissions" panel lists as denied-with-reason. It is explicitly **not** the
authority — every transition still goes through the real `PATCH /api/applications/{id}/status`,
and the server's own validator makes the actual call independently. The Settings page's transition
matrix and the State-machine page's graph both read from this same module, so there's one place
the client-side rule lives, not three copies.

**Known, documented gaps against the design's mockup** — HireFlow's real data model doesn't match
the design's fictional multi-employer job-board scenario in a couple of places, handled honestly
rather than faked:
- Postings have no separate "company" field (title + description only) — the design's "role /
  company" list column becomes "posting title / truncated description."
- `RECRUITER` has no API path to resolve a candidate's email from their id (only
  `GET /api/admin/users`, which is ADMIN-only) — RECRUITER views fall back to `Candidate #<id>`,
  same as the pre-redesign dashboard; `ADMIN` resolves real emails via that endpoint.
- Applications search/status-filter run client-side over a generously-sized single fetch rather
  than a true server-side free-text search — the real API only exposes `postingId`/`status`
  filters, no text search parameter.

### Motion

The design brief is explicit and deliberately restrained: "none beyond default hover color
changes... at most a 120ms background-color transition on rows and buttons." The redesigned pages
honor that — list rows and buttons get a plain CSS `transition: background-color 120ms ease`
(`--transition-fast` in `styles.css`), and that's it.

`shared/animation/motion.ts` (anime.js v4) — the staggered list reveal, the kanban card-advance
slide, and the row-flash-on-change helpers described in earlier revisions of this README — is kept
in the codebase (still exported, still `prefers-reduced-motion`-aware) but is **not called by any
of the redesigned pages**. This is a deliberate reconciliation, not an oversight: the design spec's
restraint is a real, stated decision, and the motion module remains as a documented, working piece
of the project's history rather than being deleted. `PipelineProgressComponent` (the 5-step
stepper) was rewritten to plain CSS with no JS-driven fill animation, in keeping with the same
brief.

### Responsiveness

Below ~1180px (the design's documented breakpoint): the sidebar collapses to icons only, the
Overview stat row goes 2×2, the Applications master-detail grid collapses to one column with the
detail panel becoming a full-screen drawer (opened by selecting a row, closed with a "‹ Back to
list" button), and the Postings grid drops to a single column. Verified live at 1024px width in an
actual browser (see "Frontend verified end-to-end" below), not just written and assumed correct.

### Running the frontend

```bash
cd frontend
npm install
ng serve
```

Serves on `http://localhost:4200` and calls the API at `http://localhost:8080`
(`src/environments/environment.development.ts`). The backend's `SecurityConfig` has a CORS policy
scoped to exactly this origin (see "Backend changes for the frontend" below).

### Frontend tests

```bash
cd frontend
ng test --watch=false --browsers=ChromeHeadless
```

**Verified**: 14/14 tests pass, including the guard specs (not-logged-in → `/login`, wrong role →
`/forbidden`, correct role → allowed) and the interceptor spec. Re-verified 14/14 (unchanged) after
the ApplyTrack redesign — the redesign changed templates/styles and route wiring, not the units
under test (guards, interceptor, `AppComponent`), so the count and coverage held; the new pages
themselves were instead verified live in a browser and via the rewritten e2e suite (below).

*Environment note:* the plain `ChromeHeadless` launcher fails to capture in this sandbox
(`karma.conf.js` documents this and ships a `ChromeHeadlessNoSandbox` custom launcher for it,
which is what was actually used to produce the 14/14 result above: `ng test --watch=false
--browsers=ChromeHeadlessNoSandbox`). Not a project requirement on a normal machine.

### Build config note: anime.js and Vite prebundling

`angular.json` excludes `animejs` from the dev server's Vite prebundling. This is not
cargo-culting: anime.js v4 ships both CJS and ESM, and its `exports` map lists the CJS build as
the `default` condition, so the dev server resolved it to CJS and served it as an ES module. The
result was `does not provide an export named 'animate'` at *runtime* only — `ng build` and
`tsc --noEmit` both passed cleanly, because the production esbuild pipeline resolves it correctly.
The visible symptom was unrelated-looking: a component threw during construction, route activation
aborted, and the e2e suite failed with "element not found" on a page that had silently refused to
navigate. Excluding it from prebundling hands it to the same esbuild path production already used.

### Backend changes for the frontend

One backend file changed to support local frontend development: `SecurityConfig.java` gained a
`CorsConfigurationSource` bean scoped to `http://localhost:4200` (GET/POST/PATCH/PUT/DELETE/OPTIONS,
`Authorization` + `Content-Type` headers only, `allowCredentials` left at its default `false` since
auth is a header, not a cookie). No other backend file changed. The full backend suite was
re-verified after this change — still **247/247 passing**.

### Frontend verified end-to-end (not just "it compiles")

With Postgres up, the real backend running (`mvn spring-boot:run`), and `ng serve` running, the
ApplyTrack-redesigned frontend was exercised in an actual browser against the real API — not
mocked — as all three roles:

- **CANDIDATE**: registered through the real form → landed on Overview (candidate copy: "Your
  search, in focus," role-correct stats/pipeline/nudge) → Job postings → Apply → button correctly
  flips to "Already applied" → the real 403 demo button (`Call POST /api/postings as CANDIDATE →`)
  produces the genuine backend response, not a canned one → Applications shows the application
  with a live stepper and audit trail → toggled "Inspect permissions" and confirmed the denied
  panel lists the correct reasons (ported validator) for every illegal target → opened the
  transition confirm-sheet on Withdraw, confirmed with a note, watched the real `PATCH` land and
  the audit trail update with that note → Overview's activity log picked up the same event with
  "You" as the actor and the note text.
- **RECRUITER**: logged in → Overview shows the recruiter-specific nudge ("Offers are the
  candidate's call") and real in-scope counts → Applications shows every application (candidate
  emails correctly fall back to `Candidate #<id>`, since RECRUITER has no API path to resolve
  them) → selected a row, advanced it one step through the confirm sheet with a note, watched the
  stepper/status/audit-trail update in place → Job postings → "View pipeline" correctly deep-links
  into Applications filtered to that posting (dismissable chip) → the same real 403 demo as
  CANDIDATE → Settings' transition matrix renders the correct ALLOW/DENY grid for RECRUITER.
- **ADMIN**: Overview shows "The whole org, one view" and the admin-only nudge → Job postings shows
  "+ New posting" instead of the 403 demo → created a posting through the new modal, watched it
  appear, closed it (chip flips to CLOSED, Reopen appears) → attempting `/admin/users` as
  RECRUITER/CANDIDATE (direct navigation, fresh session) correctly redirects through
  `/login?redirect=...` to `/forbidden`; as ADMIN, Settings' "Manage users" card links straight
  in → created a RECRUITER account through the restyled form, it appeared in the table immediately.
- **Responsive**: resized to 1024px (below the design's ~1180px breakpoint) and confirmed the
  sidebar collapses to icons-only and the Applications detail panel becomes a full-screen drawer
  with a working "‹ Back to list" control.
- No console errors, no CORS failures, throughout.

Not exercised in this pass (documented as a gap, not claimed): the interceptor's refresh-on-401
path wasn't triggered live (access tokens are short-lived but not expired within a manual test
session) — its behavior is covered instead by `auth.interceptor.spec.ts` against a mocked 401. An
actual PDF résumé upload/download wasn't exercised live in this pass either (no sample file in this
environment) — the upload/download UI itself was confirmed present and wired to the same
`ApplicationService.uploadResume()`/`downloadResume()` calls the pre-redesign UI used, unchanged.

## Running it

### Prerequisites
- JDK 21+ (developed/tested against JDK 25, targeting release 21 bytecode)
- Maven 3.9+
- Node.js + npm (for the frontend)
- Docker (for local Postgres via Compose, and for the Testcontainers integration tests)

### 1. Configure environment

```bash
cp .env.example .env
# edit .env if you want non-default values; the defaults work for local dev
```

`.env` is git-ignored. `application.yml` reads everything from environment variables (`DB_URL`,
`DB_USERNAME`, `DB_PASSWORD`, `JWT_SECRET`, `JWT_ACCESS_TTL_MINUTES`, `JWT_REFRESH_TTL_DAYS`,
`SERVER_PORT`) — nothing is hardcoded in source. `mvn spring-boot:run` does **not** load `.env`
automatically (no dotenv plugin is wired in); export the variables into your shell first, e.g.:

```bash
set -a && source .env && set +a
```

*Local port note:* `docker-compose.yml` maps Postgres to host port **5433**, not the default 5432
— on a machine that already has another Postgres (e.g. a Homebrew service) bound to 5432, mapping
to the same port would silently connect the app to the *wrong* database instead of failing loudly
(exactly what happened once while building this: the app connected to an unrelated local Postgres
and failed with "role hireflow does not exist"). `DB_HOST_PORT` in `.env` controls this if you need
a different port.

### 2. Start Postgres

```bash
docker compose up -d
```

**Running without Docker/Colima.** On macOS, Docker Desktop isn't required — this project runs
under [Colima](https://github.com/abiosoft/colima) instead (a lighter Docker CLI-compatible VM) —
but Colima itself isn't free: its VM idles at real CPU/memory even doing nothing, and if it
crashes uncleanly the VM process can be left running and consuming that CPU even after `colima
stop` reports success (worth checking `ps aux | grep Virtualization` if things feel slow).

Not every workflow here actually needs a container runtime:

| Needs Docker/Colima | Doesn't |
|---|---|
| `mvn test` (Testcontainers integration tests) | Backend unit tests (`TransitionValidator` etc.) |
| `docker compose --profile full up` (containerized stack) | `ng test`, `ng build` |
| | `mvn spring-boot:run` + `ng serve` + e2e — **if** Postgres comes from somewhere else |

For that last row, point at a native Postgres instead of the Colima-hosted container:

```bash
# one-time setup, against whatever Postgres you already have (Homebrew, etc.):
psql -d postgres -c "CREATE ROLE hireflow LOGIN PASSWORD 'hireflow';"
createdb -O hireflow hireflow

cp .env.native.example .env   # points DB_URL at localhost:5432 instead of the Colima container
```

Then `mvn spring-boot:run`, `ng serve`, and the e2e suite (`DB_HOST_PORT=5432 npx playwright test`
— it seeds RECRUITER/ADMIN test accounts via direct SQL, so it needs to know which port to
connect to) all work with Colima never started. Switch back to `.env.example` before running
`mvn test` or the full compose profile — those still need the real thing.

### 3. Run the app

```bash
mvn spring-boot:run
```

The API is now listening on `http://localhost:8080`.

### 4. Run the tests

```bash
mvn test
```

## Verified test results

`mvn verify` was actually run in this environment (Docker available via a Colima daemon) and all
**326 tests passed, 0 failures, 0 errors**:

- `TransitionValidatorExhaustiveTest` — 149/149 (full 7×7×3 cross-product)
- `TransitionValidatorNegativeTest` — 35/35
- `ApplicationIntegrationTest` — 30/30 (Testcontainers Postgres)
- `AuthIntegrationTest` — 35/35 (Testcontainers Postgres — includes password reset, device-bound refresh tokens, email verification)
- `AdminUserIntegrationTest` — 16/16 (Testcontainers Postgres)
- `ResumeIntegrationTest` — 16/16 (Testcontainers Postgres)
- `TransitionValidatorPositiveTest` — 12/12
- `PostingIntegrationTest` — 11/11 (Testcontainers Postgres)
- `RateLimitingFilterTest` — 8/8 (unit, no Spring context)
- `AdminBootstrapRunnerTest` — 6/6 (unit, no Spring context)
- `TokenBucketRateLimiterTest` — 4/4 (unit, no Spring context)
- `RequestIdFilterTest` — 3/3 (unit, no Spring context)
- `GlobalExceptionHandlerTest` — 1/1 (unit, no Spring context)

The integration tests use **real PostgreSQL via Testcontainers** (`postgres:16-alpine`), not H2 —
`AbstractIntegrationTest` boots the full Spring context (`@SpringBootTest` + `@AutoConfigureMockMvc`)
against a container, so the real Spring Security filter chain, real `@PreAuthorize` checks, and
real Hibernate/Postgres SQL all run in every test.

Every endpoint has both a positive test (allowed case succeeds) and negative tests (wrong role →
403, invalid transition → 400, missing/invalid auth → 401, not found → 404, duplicate/conflicting
state → 409), including an explicit proof that a candidate cannot touch another candidate's
application.

Beyond `mvn test`, the full stack was also manually smoke-tested end-to-end against a real
`docker compose`-style Postgres and `mvn spring-boot:run`: register → login → refresh-token
rotation and replay-rejection → create a posting as ADMIN → apply as CANDIDATE → an invalid
self-advance attempt correctly rejected with 400 → a valid RECRUITER transition → a skipped-step
attempt correctly rejected with 400 → the audit trail correctly showing both events. All observed
behavior matched what's documented above.

*Environment note:* this sandbox's Testcontainers/Docker setup needed `DOCKER_HOST` pointed at the
local Colima socket and `TESTCONTAINERS_RYUK_DISABLED=true` (Ryuk's bind-mount doesn't work through
Colima's VM) to run at all — that's an environment quirk, not a project requirement. On a standard
Docker Desktop setup, `mvn test` should work with no extra environment variables.

## End-to-end tests

`e2e/` (top-level, sibling to `frontend/` — kept separate from the Angular CLI's own Karma/Jasmine
tooling rather than nested under `frontend/e2e/`, since it's a different test runner, a different
`node_modules`, and it exercises the running stack rather than the Angular app in isolation) is a
Playwright (TypeScript) suite. It drives the real UI in a real Chromium instance against whatever
stack you already have running — either local dev (`mvn spring-boot:run` + `ng serve`, frontend on
`:4200`, backend on `:8080`) or the docker-compose "full" profile below, which publishes the same
two ports — plus, for the negative authorization proofs, it calls the backend directly with
Playwright's `request` context (no browser involved at all).

**What it covers**, matching the role/state-machine scope (`e2e/tests/`) — rewritten for the
ApplyTrack redesign's unified routes and selectors (`utils/ui.ts`'s `loginViaUi` now clicks
"Sign in", not "Log in"; rows are targeted by `#app-row-{id}`/`#posting-card-{id}` instead of text
matches on a `<tr>`), while keeping the same scenarios the pre-redesign suite covered:

- `candidate.positive.spec.ts` — register through the real form → browse open postings (`/postings`)
  → apply → see it in Applications (`/applications`, unified route — CANDIDATE sees only their own)
  → (a seeded recruiter advances it APPLIED→SCREENING→INTERVIEW→OFFER via direct API calls) → open
  the transition confirm-sheet and accept the offer for real (the pre-redesign UI fired the
  transition immediately on click; this one requires opening and confirming a modal first).
- `recruiter.positive.spec.ts` — log in → Applications shows every application → advance one
  (APPLIED→SCREENING) through the confirm sheet → reject a *different* one the same way → its
  audit trail is already visible in the detail panel for whichever row is selected (no more
  per-row toggle) — confirm both events are there. A second test exercises the real 403 demo on
  `/postings` as a non-admin.
- `admin.positive.spec.ts` — log in → Job postings (`/postings`) → create a posting through the new
  modal → see it appear → close it.
- `guards.negative.spec.ts` — the old per-role pages (`/admin/postings`, `/recruiter/applications`)
  are gone; the one route that's still genuinely role-exclusive is `/admin/users`. A CANDIDATE and
  a RECRUITER each hitting it directly by URL are blocked by `roleGuard('ADMIN')`, asserted on the
  resulting URL/page content (not on a button being hidden). Also covers an unauthenticated visitor
  hitting a guarded route.
- `api-security.negative.spec.ts` — **unchanged** from before the redesign (no UI selectors, no
  route dependency — it drives the backend directly via Playwright's `request` context): a
  CANDIDATE cannot `PATCH` or `GET /events` on another candidate's application (**403**, ownership
  — enforced in `ApplicationService`); a CANDIDATE attempting a RECRUITER-only transition on their
  *own* application gets **400**, not 403 (`TransitionValidator`'s domain — see "403 vs 400"
  above); an unauthenticated request to a protected endpoint gets **401**; and a non-ADMIN calling
  `PATCH /api/postings/{id}` directly gets **403**.

**A real finding from writing these tests**: `AuthService` keeps tokens in memory only (see
"Frontend" above), so a *hard* browser navigation — `page.goto()`/`page.reload()` in Playwright,
exactly like typing a URL and hitting enter in a real browser — always drops the session first.
The guard negative-tests account for this deliberately: they land on the target URL
unauthenticated (guard redirects to `/login?redirect=<url>`), log in there, and let
`LoginComponent`'s own post-login redirect send them back — which is when `roleGuard`'s *role*
check (not just its logged-in check) actually fires. The positive candidate flow avoids
`page.goto()`/`page.reload()` after login entirely, using the real nav links instead, for the same
reason.

**Seeding**: a CANDIDATE is always created through `POST /api/auth/register` (the only
self-service path). RECRUITER/ADMIN test accounts are created by registering a CANDIDATE and then
promoting it directly in Postgres (`e2e/utils/db.ts`, the same `UPDATE users SET role = ...`
approach documented above), then logging in again so the JWT actually carries the new role.

**Running it locally**:

```bash
# with the stack already running (either local dev, or `docker compose --profile full up`):
cd e2e
npm install
npx playwright install chromium   # first time only
npx playwright test
```

**Verified**: run against the local dev stack (`mvn spring-boot:run` + `ng serve`, native Postgres)
after the ApplyTrack redesign, all **12/12 tests passed** (11 scenarios plus the new real-403-demo
test added for the redesigned Postings page) on a clean single run.

**The proof that this suite is real, not decorative**, was performed once, against the
pre-redesign version of this suite, and wasn't repeated here since `api-security.negative.spec.ts`
(the spec that proof exercises) is byte-for-byte unchanged by the redesign: with the suite green,
`@PreAuthorize("hasRole('ADMIN')")` was commented out on `PostingController#update`
(`PATCH /api/postings/{id}`), the backend was restarted, and the suite was re-run. Result: **1 test
failed** — `api-security.negative.spec.ts › a non-ADMIN cannot PATCH a posting directly (403)` —
because the weakened endpoint returned **200** (and actually closed the posting) for a CANDIDATE
token instead of denying it, while every other test still passed unaffected. This is also what
motivated that specific test's existence: none of the other tests touch `PATCH /api/postings/{id}`
as anything other than an ADMIN, so without it, weakening that one annotation would have gone
completely unnoticed by the suite. The annotation was then restored (confirmed via `git diff` showing no
changes) and the backend restarted; the suite was re-run once more and all 11 tests (the suite's
size at the time this proof was performed) passed again. That proof's validity carries forward
unchanged to the current 12-test suite, since the spec it exercises wasn't touched.

## Full stack via Docker Compose

`docker-compose.yml` now defines two workflows, controlled by Compose's `profiles` mechanism, so
the existing local-dev workflow keeps working unmodified alongside a new full-container one:

- **`docker compose up -d`** — unchanged from Weeks 1–2: starts **Postgres only**, for
  `mvn spring-boot:run` + `ng serve` local dev. `backend`/`frontend` are gated behind
  `profiles: ["full"]`, so a bare `docker compose up -d` never touches them.
- **`docker compose --profile full up`** (add `-d --build` to run detached and force a fresh
  image build) — brings up **Postgres + backend + frontend** as containers: the whole stack in one
  command.

Two new Dockerfiles, both multi-stage:

- `Dockerfile` (backend) — `maven:3.9-eclipse-temurin-21` builds the jar, then a slim
  `eclipse-temurin:21-jre-alpine` runtime stage runs it as a non-root user.
- `frontend/Dockerfile` — `node:20-alpine` runs `npm ci && npm run build` (Angular's
  `defaultConfiguration` is `production`, so no `--configuration` flag is needed), then
  `nginx:1.27-alpine` serves the static output via `frontend/nginx.conf` (static files +
  `try_files ... /index.html` SPA fallback — nothing else).

**The env var wiring that's easy to get wrong**, spelled out because it's a genuinely easy mistake
to make: the `backend` container reaches Postgres over the *internal Docker network*, so its
`DB_URL` is `jdbc:postgresql://postgres:5432/hireflow` (the Compose **service name**, container
port), never `localhost` and never the host-published `DB_HOST_PORT`. The `frontend` container is
different: nginx just serves static files, but the *browser* is what actually calls the API —
and a browser cannot resolve `postgres` or `backend` as hostnames, those only exist inside the
Docker network. So the Angular build's `apiUrl` (`environment.ts`, baked in at `npm run build`
time) is `http://localhost:8080` — the **host-published** port the `backend` container exposes via
`ports: ["${BACKEND_HOST_PORT:-8080}:8080"]` — not `http://backend:8080`. Both host ports
(`BACKEND_HOST_PORT` defaulting to 8080, `FRONTEND_HOST_PORT` defaulting to 4200) are configurable
in `.env`, same pattern as the existing `DB_HOST_PORT`.

**Verified in this environment** (pre-dating the ApplyTrack redesign — not re-run since, because
the redesign work was frontend-only and used native-Postgres/`ng serve`/`mvn spring-boot:run`
rather than Docker/Colima): `docker compose --profile
full up -d --build` was run against the already-running `hireflow-postgres` container (Compose
recognized it as satisfying the `postgres` service and left it alone rather than recreating it).
Both new images built successfully and both containers reached a running state. With the local
`mvn`/`ng serve` processes stopped first (so the containers could bind the same host ports), the
containerized stack was exercised through an actual browser at `http://localhost:4200`: register →
browse the open posting list → apply → see it in the applications list with status APPLIED, all
against the containerized backend on `http://localhost:8080`, with no console errors. After
verifying, `docker compose stop backend frontend` was run, leaving `postgres` running exactly as it
was found — host ports 8080/4200 are free again. The containerized frontend image will pick up the
redesign automatically on its next `--build` (nothing about the Dockerfile/nginx setup changed),
but that rebuild itself hasn't been re-verified since the redesign.

## Live deploy

Three free-tier services, none sharing a host with the local Docker Compose setup above:

- **Neon** — serverless Postgres, free tier, no expiry.
- **Render** (web service) — the Spring Boot backend, built from the root `Dockerfile` (the same
  one Docker Compose uses locally). Free web services spin down after 15 minutes idle and cold-start
  on the next request (~30-50s) — a real, known limitation of the free tier, not something worth
  papering over.
- **Render** (static site) — the Angular frontend, built directly from `frontend/` with
  `npm install && npm run build` (no Docker image here — a static site doesn't need one).

**Why this needed real code changes, not just deploy config.** Two things were hardcoded to
`localhost` and had to become configurable:

- CORS (`SecurityConfig#corsConfigurationSource`) was hardcoded to `http://localhost:4200`. Now
  reads `hireflow.cors.allowed-origins` (`CORS_ALLOWED_ORIGINS`, comma-separated), defaulting to
  `http://localhost:4200` so local dev is untouched.
- The frontend's `apiUrl` was hardcoded to `http://localhost:8080` in `environment.ts`, used for
  *both* `ng serve` and `ng build`. Fixing this meant giving each of the frontend's three real
  build contexts its own environment file, wired through `angular.json`'s `fileReplacements`:
  `environment.development.ts` (`ng serve`, unchanged), `environment.docker-compose.ts`
  (`frontend/Dockerfile`'s build, still `localhost:8080` — the *browser* calls the API, not the
  nginx container, and can't resolve Docker-internal service names either way), and the new
  `environment.production.ts` (Render's static-site build, the real deployed backend URL). Getting
  this wrong would have meant either the live frontend silently calling `localhost:8080` from a
  visitor's own machine, or the local Docker Compose flow silently pointed at the live deploy.

**The chicken-and-egg problem worth naming**: the backend needs to know the frontend's exact URL
(for CORS) and the frontend needs to know the backend's exact URL (baked in at build time) - but
Render only assigns each service's real URL (`<name>-<random-suffix>.onrender.com`) once it's
created, and that suffix isn't predictable in advance. Resolved by creating the frontend service
first (its URL, once assigned, never needs to change again), then creating the backend with that
frontend URL already in its `CORS_ALLOWED_ORIGINS`, then committing the backend's now-known URL
into `environment.production.ts` - one push after that point covers both sides.

**A real bug this surfaced**: a hard navigation to any client-side route other than `/` (e.g.
`/register`, refreshing on `/settings`) returned a literal 404. Locally this is a non-issue - nginx
already handles it (`try_files $uri $uri/ /index.html` in `nginx.conf`) - but Render's static-site
host doesn't run nginx and doesn't do this by default. The first fix attempted was a Netlify-style
`frontend/public/_redirects` file (`/*  /index.html  200`), on the assumption Render auto-detects
it the way Netlify does - it doesn't; the file gets served as a literal static asset, not read as
routing config, so the 404 persisted even after that deploy went live. The actual fix is a rewrite
rule configured directly on the service (Render dashboard → Redirects/Rewrites, or the equivalent
in a `render.yaml`, neither of which the CLI used to create these services exposes): `/*` →
`/index.html` as a **Rewrite** (not a Redirect - a redirect would change the URL in the address
bar; a rewrite serves `index.html`'s content while leaving the requested path alone, which is what
lets Angular's router take over). Applied instantly, no rebuild needed. The `_redirects` file was
left in place since it's harmless and is the correct mechanism for a Netlify-style host, but it is
not what makes routing work on this deploy.

**Known limitations of this specific deploy**, stated plainly rather than hidden:
- `ADMIN_BOOTSTRAP_EMAIL`/`ADMIN_BOOTSTRAP_PASSWORD` are set so a reviewer can see the ADMIN and
  RECRUITER views without needing database access - `admin@hireflow.demo` / `DemoAdmin2026!`. This
  is a demo-only credential for a portfolio deploy with no real data in it, not a pattern to copy
  for an actual production bootstrap.
- Résumé storage (`hireflow.resume.storage-dir`) writes to the backend container's local
  filesystem, which is ephemeral on Render's free tier (no persistent disk on the free plan) - so
  the résumé upload feature will lose stored files on every redeploy/restart. Acceptable for a
  free-tier demo, not for anything real.

## Continuous integration

`.github/workflows/build.yml`, styled after the sibling LogSentry project's simple single-purpose
workflow (`checkout` → `setup` → run the real command, no extra layers), triggering on push/PR to
`main`. Three jobs:

- **`backend`** — JDK 21 (Temurin), `mvn -B verify` (runs the full 326-test suite, including the
  Testcontainers integration tests — `ubuntu-latest` runners ship Docker natively, so none of this
  local machine's Colima/`DOCKER_HOST`/Ryuk workarounds are needed there).
- **`frontend`** — Node 20, `npm ci`, then `ng test --watch=false --browsers=ChromeHeadlessNoSandbox`
  (`karma.conf.js` already defaults to that launcher; `CHROME_BIN` is resolved on the runner via
  `command -v google-chrome-stable` et al., since `karma.conf.js`'s own `CHROME_BIN` fallback is a
  macOS-only path that would break on a Linux runner), then `ng build` to confirm the production
  build succeeds.
- **`e2e`** (`needs: [backend, frontend]`) — brings up the full stack with
  `docker compose --profile full up -d --build`, polls both `:8080` and `:4200` with `curl` until
  they respond, runs `npx playwright test` from `e2e/`, uploads the HTML report as a build
  artifact, then tears the stack down (`docker compose --profile full down -v`) — `if: always()`
  on the teardown/upload steps so a failed run still cleans up and still leaves a report to
  inspect.

**This is genuinely running, not just wired up** — the repo is pushed to GitHub
(`belal1908/HireFlow`) and every commit on `main` triggers a real run on GitHub's own runners, not
just this local environment. The badge at the top of this README reflects the actual current state
of `main`. `mvn -B verify` (326/326), `ng test` (14/14), `ng build`, and `npx playwright test`
(12/12) all pass there the same as they do locally.

## Known gaps

Deliberately left out for now:

- **Password reset and email verification both exist but aren't production-safe** (see those
  sections above): there's no SMTP infrastructure in this project, so both tokens are returned
  directly in their API responses instead of being emailed — a real deployment needs an actual
  email send wired in first. Email verification is also informational-only by design: it doesn't
  gate login or any action, it just tracks and displays whether the account has proven control of
  its email.
- **No multi-tenancy.** Single organization; all RECRUITER/ADMIN users see the whole pipeline.
- **No soft-delete / posting archival** beyond the `OPEN`/`CLOSED` status.
- **Device binding (User-Agent) is a coarse, spoofable signal, not a strong security boundary.**
  See "Device-bound refresh tokens" above — it stops a leaked token being casually replayed from a
  different client, not a determined attacker who copies the header too.
- **No metrics or distributed tracing.** Request-correlated event logging exists (see "Logging &
  request correlation" above), but there's no Micrometer/Prometheus/OpenTelemetry integration, no
  dashboards, and no alerting — log lines have to be read, not queried as metrics.
