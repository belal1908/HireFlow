# HireFlow

![build](https://github.com/belal1908/HireFlow/actions/workflows/build.yml/badge.svg)

*The badge above points at `belal1908/HireFlow` on GitHub Actions. It will only render/go green
once this repository is actually pushed there and the workflow has run — as of this commit that
hasn't happened yet, so treat the badge as "wired up, not yet live" rather than a live status.*

A role-aware recruiting pipeline for small teams: candidates apply, recruiters move applications
through a strictly-enforced state machine, admins manage postings, and every status change is
written to an append-only audit log. Built to exercise real Spring Security (JWT + method-level
`@PreAuthorize`, ownership checks, refresh-token rotation) and a genuinely tested state machine —
not another CRUD demo.

This covers the **Week 1 + Week 2 + Week 3 milestones**: a working Spring Boot backend, an Angular
frontend that consumes it, a Playwright e2e suite that exercises all three roles (positive and
negative cases) against the real running stack, a full-stack Docker Compose setup, and a
GitHub Actions CI workflow. See "End-to-end tests", "Full stack via Docker Compose", and
"Continuous integration" below for what's actually been verified in this environment.

## Tech stack

**Backend**
- Java 21, Spring Boot 3.5 (Web, Data JPA, Security, Validation)
- PostgreSQL (Testcontainers for integration tests, Docker Compose for local dev)
- JWT access + refresh tokens via `jjwt`, BCrypt password hashing
- JUnit 5, Spring Security Test, Testcontainers
- Maven, Lombok

**Frontend**
- Angular 18+, standalone components throughout (no NgModules)
- Functional route guards (`CanActivateFn`) and a functional `HttpInterceptorFn` for auth
- RxJS for the token-refresh coordination; no state-management library — not needed at this scope
- anime.js v4 for motion that tracks the pipeline metaphor, centralised in one module (see below)
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
  3 roles) against a hand-written set of the 12 legal transitions taken directly from the PRD —
  not derived from the implementation, so it can't become a tautology.
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
validator is standing in for `@PreAuthorize` here (per the PRD: "role-gated transition via
TransitionValidator"). Ownership violations (acting on *someone else's* application) are a
separate concern from the state machine and are enforced first, as **403 Forbidden**. Every other
role-gated endpoint (postings, apply, list-all, mine) uses `@PreAuthorize(hasRole(...))` directly
and returns a plain 403 on the wrong role, before the request body is even looked at.

### Admin user management (PRD stretch goal)

Section 7 of the PRD lists one explicit stretch goal beyond core v1 scope: "Manage
recruiter/admin user accounts." `POST /api/admin/users` and `GET /api/admin/users`
(`com.hireflow.admin` — `AdminUserController`/`AdminUserService`) implement it, closing the gap
this README used to document (the first RECRUITER/ADMIN accounts no longer need a hand-written
`UPDATE users SET role = ...` — an existing ADMIN can create them through the API or the
`/admin/users` screen instead). Scope is deliberately narrow, matching the gap it closes:

- **Create only the two roles self-registration can't produce.** `role: CANDIDATE` is rejected
  with 400 — `POST /api/auth/register` already owns that path, and creating a second way to make
  a `CANDIDATE` account would just be two sources of truth for the same thing.
- **Validation mirrors `AuthService#register`** (normalized/lowercased email, `@Email`, password
  `@Size(min = 8, max = 100)`, BCrypt hash, 409 on a duplicate email) but does **not** call into
  `AuthService` directly, since that method's whole contract is "always forces CANDIDATE" — the
  opposite of what this endpoint needs.
- **Create + list only.** No role-change, deactivate, or delete endpoints. The PRD's own
  constraint ("A user has exactly one role, assigned at creation. No self-service role changes.")
  is about a user changing *their own* role; it doesn't ask for an admin to be able to change
  *anyone's* role after the fact, so that was left out rather than guessed at.
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

## Frontend

`frontend/` is a standalone-components Angular app. It exists to demonstrate three specific
Angular patterns the PRD calls out, not to be a design showcase:

- **`AuthService`** (`core/services/auth.service.ts`) — holds the access + refresh tokens in
  memory only (never `localStorage`), so an XSS payload can't read a token that was never written
  to storage. The trade-off, stated plainly: a full page reload loses the session. A production
  system would move the refresh token into a backend-set `httpOnly` cookie instead; that's a
  backend change out of scope for Week 2. `refreshAccessToken()` shares one in-flight
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

Feature areas, one route tree per role:
- `features/auth` — login, register (registration always creates a `CANDIDATE`; the form says so)
- `features/candidate` — browse open postings and apply; "My Applications" with Withdraw/Accept
  actions that only appear when legal for the current status
- `features/recruiter` — all applications, filterable by posting/status, with Advance/Reject
  actions and a per-application audit-trail view (`GET /api/applications/{id}/events`)
- `features/admin` — create/edit/close postings, reuses the recruiter's all-applications view;
  `users-admin` creates RECRUITER/ADMIN accounts (role selector deliberately excludes `CANDIDATE`)
  and lists existing users — the frontend half of the PRD's Section 7 stretch goal, backed by
  `core/services/user.service.ts` and `core/models/user.model.ts` (mirroring `PostingService`/
  `posting.model.ts`), routed at `/admin/users` behind `roleGuard('ADMIN')`

### Motion and responsiveness

Motion here is in service of one idea — an application *moves through a pipeline* — so it is used
where it makes a state change easier to follow and essentially nowhere else. The bar for adding an
animation was "does this clarify something", not "does this look lively"; this is a recruiting
tool, not a landing page.

Everything runs through `shared/animation/motion.ts` (anime.js v4), which owns the shared duration
and easing scale and, more importantly, checks `prefers-reduced-motion` in exactly one place
instead of leaving each call site to remember. Reduced motion removes the *movement*, never the
outcome — callers still land on the correct end state instantly. `styles.css` carries a CSS
backstop for the same preference, which also covers plain transitions and still applies if a
script fails to load.

What actually animates:

- **`PipelineProgressComponent`** — a stepper rendering APPLIED → SCREENING → INTERVIEW → OFFER →
  ACCEPTED with a track that fills to the current stage, shown on the candidate's applications and
  on each kanban card. This is the one piece of UI that makes the state machine — the point of the
  whole project — visible at a glance rather than as a single status word. The fill animates only
  when a status *changes* while you're watching; on first paint it's set directly, since twenty
  rows animating their pipelines at once would be noise.
- **Kanban card advance** — the card lifts and slides out of its column, then reappears in the new
  one. The recruiter's core action, made tangible.
- **Staggered list entrance** and a **row flash** on in-place status changes. The table gets the
  flash rather than a slide, because the row doesn't move — only its status cell changes, and
  animating movement that didn't happen would be a lie.
- **Audit trail reveal** — entries arrive oldest-to-newest, so a history reads as unfolding.

Deliberately *not* built: a rolling counter for the kanban column totals. It means driving an
element's `textContent` by hand while Angular is also binding it, and the card animation already
communicates that something moved. Left out rather than half-built.

**Responsiveness** is a first pass, and worth being honest about: before it the project had no
media queries at all, so the nav clipped on a phone and tables pushed the page sideways. Now the
nav wraps to two rows (no hamburger — there are only a handful of links, so a menu would add
interaction cost for no gain), tables scroll within their own container, and the kanban
snap-scrolls horizontally. The board deliberately still scrolls sideways on mobile rather than
stacking: a pipeline *is* a horizontal sequence, and stacking would destroy the left-to-right
reading that makes the view worth having.

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
adding the admin user-management screen — no new `.spec.ts` files were added alongside it, so the
count didn't move; the screen was instead verified live in a browser (below).

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
following was exercised in an actual browser against the real API — not mocked:

- Register a candidate → auto-login → lands on the candidate view, nav/role badge correct.
- Register two more accounts, promote one to `ADMIN` and one to `RECRUITER` directly in Postgres
  (`UPDATE users SET role = ... WHERE email = ...` — self-registration always forces `CANDIDATE`,
  by design; see "Known gaps" below), then re-login to pick up the new role in the JWT.
- Log in as `ADMIN` → create a job posting → it appears immediately.
- Log in as the `CANDIDATE` → the posting is visible on the open-postings list → apply → button
  correctly flips to a disabled "Already applied" → "My Applications" shows status `APPLIED` with
  a Withdraw action and (correctly) no Accept action, since Accept is only legal from `OFFER`.
- Log in as `RECRUITER` → applications table shows the application with the *correct* next legal
  action rendered as the button label ("Advance → SCREENING") → clicking it transitions the
  application and the button updates itself to "Advance → INTERVIEW" → "View events" shows the
  full audit trail with actor and timestamp for both the creation and the transition.
- No console errors, no CORS failures, throughout.

**Admin user management, verified separately and chained into a real login as the new account:**
with the same backend/frontend/Postgres running, logged in as an `ADMIN` (seeded via the same
`UPDATE users SET role = 'ADMIN' WHERE email = '...'` approach described above), opened
`/admin/users` from the "Manage Users" nav link, created a new account (email + password + role
`RECRUITER`, no `CANDIDATE` option present in the selector) through the form → it appeared
immediately at the top of the users table with the correct role and created-date. Logged out, then
logged back in **as that newly-created account** (not the seeding admin) — landed on the
recruiter's Applications dashboard with the `RECRUITER` role badge and role-appropriate nav
(no "Manage Postings"/"Manage Users" links), and the applications list loaded successfully,
confirming the role baked into the new account's JWT is real and not just a 201 response. No
console errors throughout.

Not exercised in this pass (documented as a gap, not claimed): the interceptor's refresh-on-401
path wasn't triggered live (access tokens are short-lived but not expired within a manual test
session) — its behavior is covered instead by `auth.interceptor.spec.ts` against a mocked 401.

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

`mvn test` was actually run in this environment (Docker available via a Colima daemon) and all
**262 tests passed, 0 failures, 0 errors** (247 from Weeks 1–2, plus 15 new for the admin
user-management endpoints):

- `TransitionValidatorExhaustiveTest` — 149/149 (full 7×7×3 cross-product)
- `TransitionValidatorPositiveTest` — 12/12
- `TransitionValidatorNegativeTest` — 35/35
- `AuthIntegrationTest` — 13/13 (Testcontainers Postgres)
- `PostingIntegrationTest` — 10/10 (Testcontainers Postgres)
- `ApplicationIntegrationTest` — 28/28 (Testcontainers Postgres)
- `AdminUserIntegrationTest` — 15/15 (Testcontainers Postgres)

The integration tests use **real PostgreSQL via Testcontainers** (`postgres:16-alpine`), not H2 —
`AbstractIntegrationTest` boots the full Spring context (`@SpringBootTest` + `@AutoConfigureMockMvc`)
against a container, so the real Spring Security filter chain, real `@PreAuthorize` checks, and
real Hibernate/Postgres SQL all run in every test.

Every endpoint has both a positive test (allowed case succeeds) and negative tests (wrong role →
403, invalid transition → 400, missing/invalid auth → 401, not found → 404, duplicate/conflicting
state → 409), including the explicit "candidate cannot touch another candidate's application"
proof required by the spec.

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

**What it covers**, per the PRD's role/state-machine scope (`e2e/tests/`):

- `candidate.positive.spec.ts` — register through the real form → browse open postings → apply →
  see it in "My Applications" → (a seeded recruiter advances it APPLIED→SCREENING→INTERVIEW→OFFER
  via direct API calls) → accept the offer.
- `recruiter.positive.spec.ts` — log in → see all applications → advance one (APPLIED→SCREENING)
  → reject a *different* one → open its audit trail and confirm both events are there.
- `admin.positive.spec.ts` — log in → create a posting → see it appear → close it.
- `guards.negative.spec.ts` — a CANDIDATE hitting `/admin/postings` or `/recruiter/applications`
  directly by URL is blocked by `roleGuard`, asserted on the resulting URL/page content (not on a
  button being hidden). Also covers an unauthenticated visitor hitting a guarded route.
- `api-security.negative.spec.ts` — the direct-API proofs, no UI involved: a CANDIDATE cannot
  `PATCH` or `GET /events` on another candidate's application (**403**, ownership — enforced in
  `ApplicationService`); a CANDIDATE attempting a RECRUITER-only transition on their *own*
  application gets **400**, not 403 (`TransitionValidator`'s domain — see "403 vs 400" above); an
  unauthenticated request to a protected endpoint gets **401**; and a non-ADMIN calling
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

**Verified**: run against the local dev stack (`mvn spring-boot:run` + `ng serve` + the existing
`hireflow-postgres` container) in this environment, all **11/11 tests passed**, confirmed stable
across three consecutive runs.

**The specific proof this suite is real, not decorative** (per the PRD's success criteria): with
the suite green, `@PreAuthorize("hasRole('ADMIN')")` was commented out on
`PostingController#update` (`PATCH /api/postings/{id}`), the backend was restarted, and the suite
was re-run. Result: **1 test failed** —
`api-security.negative.spec.ts › a non-ADMIN cannot PATCH a posting directly (403)` — because the
weakened endpoint returned **200** (and actually closed the posting) for a CANDIDATE token instead
of denying it, while the other 10 tests still passed unaffected. This is also what motivated that
specific test's existence: none of the other 10 tests touch `PATCH /api/postings/{id}` as anything
other than an ADMIN, so without it, weakening that one annotation would have gone completely
unnoticed by the suite. The annotation was then restored (confirmed via `git diff` showing no
changes) and the backend restarted; the suite was re-run once more and all **11/11 passed again**.

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

**The env var wiring that's easy to get wrong**, spelled out because the PRD specifically called
it out: the `backend` container reaches Postgres over the *internal Docker network*, so its
`DB_URL` is `jdbc:postgresql://postgres:5432/hireflow` (the Compose **service name**, container
port), never `localhost` and never the host-published `DB_HOST_PORT`. The `frontend` container is
different: nginx just serves static files, but the *browser* is what actually calls the API —
and a browser cannot resolve `postgres` or `backend` as hostnames, those only exist inside the
Docker network. So the Angular build's `apiUrl` (`environment.ts`, baked in at `npm run build`
time) is `http://localhost:8080` — the **host-published** port the `backend` container exposes via
`ports: ["${BACKEND_HOST_PORT:-8080}:8080"]` — not `http://backend:8080`. Both host ports
(`BACKEND_HOST_PORT` defaulting to 8080, `FRONTEND_HOST_PORT` defaulting to 4200) are configurable
in `.env`, same pattern as the existing `DB_HOST_PORT`.

**Verified in this environment**: `docker compose --profile full up -d --build` was run against
the already-running `hireflow-postgres` container (Compose recognized it as satisfying the
`postgres` service and left it alone rather than recreating it). Both new images built
successfully and both containers reached a running state. With the local `mvn`/`ng serve`
processes stopped first (so the containers could bind the same host ports), the containerized
stack was exercised through an actual browser at `http://localhost:4200`: register → land on the
candidate view → browse the open posting list → apply → see it in My Applications with status
APPLIED, all against the containerized backend on `http://localhost:8080`, with no console errors.
After verifying, `docker compose stop backend frontend` was run, leaving `postgres` running exactly
as it was found — host ports 8080/4200 are free again.

## Continuous integration

`.github/workflows/build.yml`, styled after the sibling LogSentry project's simple single-purpose
workflow (`checkout` → `setup` → run the real command, no extra layers), triggering on push/PR to
`main`. Three jobs:

- **`backend`** — JDK 21 (Temurin), `mvn -B verify` (runs the full 262-test suite, including the
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

**Not run in this environment** — there is no GitHub Actions runner available here, and the repo
hasn't been pushed to GitHub (out of scope for this task; see "Constraints"). What *was* verified
directly: `mvn -B verify` (BUILD SUCCESS, 262/262), `npm ci` + `ng test --watch=false
--browsers=ChromeHeadlessNoSandbox` (14/14), `ng build` (succeeds), and `npx playwright test`
(11/11) all actually run and pass in this environment using the same commands the workflow uses;
the YAML itself was parsed successfully (`YAML.load_file` via Ruby's Psych) to catch syntax
errors. The badge at the top of this README will only turn green once the workflow has actually
run on GitHub.

## Known gaps / non-goals

Explicitly out of scope for now:

- **No password reset / email verification.** Registration and login only.
- **No file uploads** (résumés, attachments).
- **No multi-tenancy.** Single organization; all RECRUITER/ADMIN users see the whole pipeline.
- **No pagination** on list endpoints (`/api/postings`, `/api/applications`) — fine at demo scale,
  would need it before this saw real traffic.
- **No rate limiting** on `/api/auth/*`.
- **No soft-delete / posting archival** beyond the `OPEN`/`CLOSED` status.
- **JWT refresh tokens are stored hashed (SHA-256) but not IP/device-bound** — rotation-on-use is
  the only replay defense currently in place.
- **No structured logging / observability** (metrics, tracing) yet.
