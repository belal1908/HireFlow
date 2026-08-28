# Handoff: ApplyTrack — Job Application Pipeline Tracker (Angular frontend)

## Overview
ApplyTrack is a role-aware recruitment pipeline tracker. Three roles (CANDIDATE, RECRUITER, ADMIN) see different scopes of the same data, and every stage change passes through one state machine that decides allow/deny from `(currentStatus, targetStatus, actorRole)`. The UI's job is to make that authorization boundary *visible* without ever being the thing that enforces it.

Backend contract this UI is built against (Spring Boot + Postgres):

```
POST   /api/auth/register | /api/auth/login | /api/auth/refresh
GET    /api/postings                 POST/PATCH /api/postings[/{id}]   (ADMIN)
POST   /api/applications             (CANDIDATE)
GET    /api/applications/mine        (CANDIDATE)
GET    /api/applications             (RECRUITER/ADMIN, filterable)
PATCH  /api/applications/{id}/status { targetStatus, note? }
GET    /api/applications/{id}/events
```

## About the Design Files
`ApplyTrack.dc.html` in this bundle is a **design reference created in HTML** — a prototype showing intended look and behavior. It is not production code to copy. The task is to **recreate these designs in the target codebase** (per the PRD: Angular 17+ standalone components, route guards, `HttpInterceptor`) using that project's established patterns. If no frontend exists yet, scaffold it with the Angular CLI and implement the designs there.

Open the HTML file in a browser to click through it: role tabs top-right switch role, rows open the detail panel, action buttons open the confirm sheet, "Inspect permissions" reveals denied transitions.

`reference/*.png` are the user's original visual references for the shell (sidebar, header, cream/navy palette).

## Fidelity
**High-fidelity.** Colors, type, spacing, radii, and interactions below are final. Recreate pixel-close using the codebase's own component primitives (Angular Material, Tailwind, or plain SCSS — whichever the project already uses). Icons in the prototype are geometric placeholders; substitute the project's real icon set (Lucide/Material) at the same sizes.

---

## Design Tokens

### Color
| Token | Hex | Use |
|---|---|---|
| `bg` | `#f2efe8` | Page background (warm cream) |
| `surface` | `#fbfaf7` | Cards, table, inputs container |
| `ink` | `#152232` | Text, sidebar, primary buttons, dark panels |
| `accent` | `#f9c46b` | Active nav, logo mark, kickers on dark, selected row tint |
| `border` | `rgba(21,34,50,.08)` | Card borders, row dividers |
| `border-strong` | `rgba(21,34,50,.16)` | Input borders |
| `ink-60` | `rgba(21,34,50,.62)` | Body copy |
| `ink-50` | `rgba(21,34,50,.5)` | Mono labels |
| On dark | `#fff`, `rgba(255,255,255,.65)`, `rgba(255,255,255,.07)` borders, `rgba(255,255,255,.05)` fills |

### Status colors (one per enum value)
`APPLIED #64748b` · `SCREENING #0284c7` · `INTERVIEW #d97706` · `OFFER #7c3aed` · `ACCEPTED #16a34a` · `REJECTED #dc2626` · `WITHDRAWN #78716c`

Status chip: `background: <color>1a`, `border: 1px solid <color>44`, `color: <color>`, radius `999px`, padding `5px 10px`, mono 10.5px/600, letter-spacing `.09em`, uppercase enum text, `white-space: nowrap`.

### Typography
- **UI + headings**: Plus Jakarta Sans (400/500/600/700).
- **Labels, enums, code, counts**: IBM Plex Mono (400/500/600).
- Page title: 46px / line-height 1.05 / 700 / letter-spacing −.032em.
- Card title: 25px / 700 / −.025em. Panel title: 21–22px / 700 / −.022em.
- Section kicker: mono 10.5px / letter-spacing .13em / `ink-50` / uppercase.
- Breadcrumb: mono 11px / .16em / `rgba(21,34,50,.45)`.
- Body: 14–15.5px, line-height 1.55–1.65. Table cell title 14.5px/700; secondary 12.5–13px.
- Stat number: 40px / 700 / −.03em / line-height 1.
- Use `text-wrap: pretty` on paragraphs and long headings.

### Spacing / shape
Page padding `40px 44px 72px`, max-width `1500px`. Card padding 22–26px. Grid gaps 20–22px. Radii: cards/panels `14px`, dark hero panel `16px`, buttons/inputs `10px`, small buttons `9px`, chips/pills `999px`. Only one shadow in the system: the modal, `0 30px 70px rgba(15,25,38,.32)`. Cards use borders, not shadows.

---

## Screens / Views

### 1. Login (`/login`)
Full-height two-pane: left flexible cream pane centered, form max-width 390px; right pane `width: 44%`, `#152232`, padding 60px, content bottom-aligned.

- Left: logo lockup (34px amber circle with a 11px ink ring inside, 19px/700 "ApplyTrack"), `h1` "Sign in" (38px), sub-copy, EMAIL + PASSWORD fields (mono label above, `#fbfaf7` fill, `border-strong`, radius 10px, padding 14/15), a demo role picker (3 equal buttons, selected = ink fill/white text), full-width ink "Sign in" button (padding 16px, 15px/600), footer row with "Create a candidate account" link and mono `NO RESET FLOW` note.
- Right: mono amber kicker `JWT · 15 MIN ACCESS · ROTATED REFRESH`, 31px/700 white statement, 14.5px muted paragraph (max-width 420px).

In production, replace the demo role picker with a real password field; role comes from the JWT claim.

### 2. App shell (all authenticated routes)
- **Sidebar**: `width: 258px`, `#152232`, sticky full height. Logo block padding `24px`, bottom border `rgba(255,255,255,.07)`. Mono `WORKSPACE` label. Nav items: `13px 14px` padding, radius 10px, 14.5px/600, gap 12px, with a 16px rounded-square placeholder icon (2px border) and a 5px trailing dot; **active** = `#f9c46b` background, `#152232` text; inactive text `rgba(255,255,255,.72)`. Items: Overview, Applications, State machine, Job postings, Settings. Bottom: a note card (`rgba(255,255,255,.05)` fill, radius 12px) reading "Every transition is checked against role and ownership before the UI ever sees it.", then the user row — 34px avatar circle (CANDIDATE `#f9c46b`, RECRUITER `#7dd3fc`, ADMIN `#c4b5fd`), name 13px/600 white, role 11.5px `rgba(255,255,255,.45)`.
- **Header**: padding `20px 44px`, bottom border `rgba(21,34,50,.07)`. Left: 7px green dot `#16a34a` + "Workspace synced" 13px. Right: an "Inspect permissions" toggle pill (mono 10.5px/.1em; on = `rgba(21,34,50,.08)` fill + `rgba(21,34,50,.75)` border) and a role segmented control in a `rgba(21,34,50,.06)` pill with 3px padding — active segment ink fill/white text, radius 999px.

The role control is a demo affordance. In production it does not exist; role is read from the token.

### 3. Overview (`/`)
Breadcrumb (role-dependent: `CANDIDATE WORKSPACE / AUG 26`, `RECRUITER DESK / …`, `ADMIN CONSOLE / …`), 46px title, sub-copy, and a right-aligned ink CTA button (`14px 20px`, 14.5px/600, trailing ↗).

- **Stat row**: 4-column grid, gap 20. Each card: mono label + an 18px rounded-square accent outline top-right, 40px value, 12.5px hint. Metrics: scope count, active-in-motion, interviews, offers — labels and hints change per role.
- **Second row**: `1.55fr 1fr` grid.
  - *Pipeline shape* card: kicker + "Where things stand" + an amber `rgba(249,196,107,.35)` pill with the active count. Four rows (Applied/Screening/Interview/Offer): status dot + label + mono zero-padded count, then a 5px track `rgba(21,34,50,.09)` with a status-colored fill scaled to the max stage count. Footer above a top border: "Terminal outcomes" and a mono green summary `n accepted · n rejected · n withdrawn`.
  - *Nudge* panel: `#152232`, radius 14, amber mono kicker, 25px white title, muted paragraph, and a full-width `rgba(255,255,255,.08)` button with a trailing ›. All copy is role-specific (candidate: an offer awaits you; recruiter: accepting is the candidate's call; admin: you cannot move candidates).
- **Activity log** card: kicker `ACTIVITY LOG · APPEND-ONLY`, "Recent movement", "Full audit trail ↗" text button. Rows: 30px amber-tinted circle, actor in 700, muted verb, subject, from-chip → to-chip, note 13px muted, mono date right-aligned, `1px` bottom divider, `18px 0` padding.

### 4. Applications (`/applications`)
Header block plus a right-side pill "N records in view". Filter bar: `#fbfaf7` card, radius 14, padding `12px 16px` — search icon, borderless input ("Search role, company, or candidate"), 1px×24px divider, then three mono segment buttons All / Active / Closed (selected = `rgba(21,34,50,.1)`).

Body grid `1.5fr 1fr`, gap 22, items start.

- **List** card: header row grid `1.4fr 1fr .8fr 26px`, gap 14, padding `14px 22px`, mono column labels ROLE / COMPANY, CANDIDATE, STATUS. Rows use the same grid, padding `18px 22px`, bottom divider, `cursor: pointer`; selected row `rgba(249,196,107,.16)`; add a hover `rgba(21,34,50,.04)`. Cells: title 14.5/700 + company 12.5 muted; candidate email 13px `rgba(21,34,50,.68)`; status chip; a `›` at 16px `rgba(21,34,50,.35)`.
- **Empty state** (search miss / no records): centered, 52px dashed rounded square, 20px/700 title, 13.5px body (max-width 320px), ink CTA. Copy varies: no match on a query → "Clear search"; candidate with nothing → "Browse open roles"; recruiter all-terminal → "Show all records".
- **Detail panel** (sticky, `top: 24px`): mono application id, 22px title, `company · candidate` 13.5px muted, status chip top-right.
  - *Stepper*: 5 equal columns (APP/SCR/INT/OFF/ACC), each a 5px full-width bar plus mono 9.5px label; bars up to and including the current stage take their status color, later ones `rgba(21,34,50,.12)`; terminal statuses light nothing.
  - *Actions*: mono label `AVAILABLE TO YOU AS <ROLE>`, then a wrapping row of buttons — Advance to <stage> / Accept offer / Reject / Withdraw. Styles: advance = ink fill, accept = `#16a34a`, reject = `rgba(220,38,38,.1)` fill with `#b91c1c` text, withdraw = `rgba(120,113,108,.14)` with `#57534e`. If none are legal, render a sentence instead: terminal → "…No role can move this application again — including ADMIN."; otherwise → "…the buttons are absent because the API would return 403."
  - *Denied panel* (only when Inspect is on): dashed `rgba(21,34,50,.18)` box on `rgba(21,34,50,.05)`, label `DENIED BY TransitionValidator`, one line per illegal target: mono red `403` + `→ TARGET · reason`.
  - *Audit trail*: label `AUDIT TRAIL · N EVENTS`, then a timeline — 14px-wide rail column per event with a 10px dot (hollow `#fbfaf7` fill with a 2.5px status-colored border for in-motion targets; solid fill for terminal ones) and a 2px `rgba(21,34,50,.14)` connector that is transparent on the last item. Content: mono `FROM → TO` (target colored, 600), then `**actor** · ROLE · date` 13px, then the note 12.5px muted.
  - Unselected: centered 46px rounded placeholder, "Select an application", 13.5px helper.

### 5. Transition confirm sheet (modal)
Fixed overlay `rgba(15,25,38,.5)`, centered, padding 40px, z-index 40. Card max-width 480px, `#fbfaf7`, radius 16, padding 30, the system's one shadow.
Content: mono `PATCH /api/applications/{id}/status`, 25px title (`role · company`), a row of from-chip → to-chip with a right-aligned mono `ACTOR: <ROLE>`, then the note field — mono label `NOTE (OPTIONAL · WRITTEN TO THE AUDIT LOG)`, textarea min-height 88px, radius 10, white fill, placeholder "Why this move? Future you will want to know." Footer: outline "Cancel" plus a flexible confirm button whose label and color match the target (Reject `#b91c1c`, Accept `#16a34a`, Withdraw `#57534e`, else ink).
On confirm: `PATCH` with `{ targetStatus, note }`, then refetch the application and its events. Empty note is stored as "No note recorded." in the prototype — decide server-side whether to send `null` instead.

### 6. State machine (`/state-machine`)
Breadcrumb `WORKSPACE / TransitionValidator`, 46px title "The state machine", sub-copy containing the signature in mono, and a right-aligned legend (3 rows: 22×4px swatch + mono label) — amber = recruiter-owned edge, `#7dd3fc` = candidate-owned edge, `rgba(255,255,255,.3)` = no edge for admin.

Main panel: `#152232`, radius 16, padding `44px 40px 38px`.
- Header row: mono `HAPPY PATH` left, mono amber `LIT: <STATUS>` right (mirrors the currently selected application).
- Happy path: 5 equal cells APPLIED → SCREENING → INTERVIEW → OFFER → ACCEPTED. Each cell = a 34px edge zone above the node (2px owner-colored line at 55% opacity + mono 9px owner label; hidden on the first node) and the node itself: radius 12, padding `18px 16px`, `rgba(255,255,255,.05)` fill / `rgba(255,255,255,.1)` border, or when lit `rgba(249,196,107,.16)` / `#f9c46b`; inside, a 9px status dot, mono 11.5/600 enum name, mono 10.5 zero-padded live count. Right margin 12px except the last.
- Terminal band: dashed top border, mono `FROM ANY NON-TERMINAL STATE`, two dashed cards (REJECTED / WITHDRAWN) each with status dot, mono name, an owner pill outlined in the owner color, and a 13.5px explanatory line.
- Footer strip: mono red `TERMINAL` + a sentence stating that terminal states have zero outbound edges, admin included.

Below the panel: 3 cream fact cards — STATES 7, LEGAL EDGES 6, ROLES THAT CAN MOVE WORK 2.

### 7. Job postings (`/postings`)
Title and sub-copy swap by role (admin: "Job postings" / manage; others: "Find your next place" / open roles).
- ADMIN gets a `+ New posting` ink button. Non-admins get a dashed button "Call POST /api/postings as <ROLE> →" which renders the **403 panel**: `#152232`, radius 16, padding 34 — mono 56px `403` in `#fca5a5`, a 23px white headline "Forbidden — and not because the button was hidden.", a paragraph explaining both layers refused independently, mono amber `@PreAuthorize("hasRole('ADMIN')")`, and a Dismiss button. (In the real app this panel is the error state of the request, plus the route-guard redirect target.)
- Posting grid: 2 columns, gap 20. Card: 19px/700 title, 13px meta (`city · type · stack`), OPEN/CLOSED chip (open = green tint, closed = neutral grey), 13.5px description, then a footer above a top border with a mono stat (`N applications` for recruiter/admin, "Posted this month" for candidates) and role-dependent actions: admin → Edit + Close/Reopen; candidate on an open role → ink "Apply"; recruiter → outline "View pipeline".

### 8. Settings (`/settings`)
Breadcrumb `WORKSPACE / ACCESS & PREFERENCES`, 46px "Settings". Grid `1fr 1.3fr`, gap 22.
- **Profile** card: 21px title, helper, read-only NAME and EMAIL fields (white fill, `border-strong`), then a `rgba(21,34,50,.05)` block: "Assigned role" 14/700 + "Set at creation. No self-service changes." and an amber mono role pill.
- **Transition matrix** card: header row (mono column labels TRANSITION / CANDIDATE / RECRUITER / ADMIN) on grid `1.5fr repeat(3,1fr)`, then 7 zebra rows (`rgba(21,34,50,.035)` on odds, radius 8, padding `12px 4px`): the transition in mono 11.5, and ALLOW (`#16a34a`) / DENY (`rgba(21,34,50,.35)`) per role. Rows: the three forward edges (recruiter), `OFFER → ACCEPTED` (candidate), `* → REJECTED` (recruiter), `* → WITHDRAWN` (candidate), `TERMINAL → *` (nobody). Footer sentence restates the terminal rule.

---

## Interactions & Behavior
- **Role switch** (demo only): resets the selected application and re-scopes every list, stat, and copy string.
- **Row click** → selects the application, populating stepper, actions, denied list, and audit trail.
- **Action click** → opens the confirm sheet; Confirm issues the PATCH and prepends the new event to the trail; Cancel discards the note.
- **Inspect permissions** toggle → shows/hides the denied-transitions panel. Default on in the prototype; default off in production, or expose it only in a demo build.
- **Search** filters on role + company + candidate, case-insensitive; **All/Active/Closed** filters on terminal vs non-terminal. Server-side both, per the API's filter params.
- **403 demo** → replaces nothing; the panel appears above the posting grid until dismissed.
- Transitions/animations: none beyond default hover color changes. Keep it that way — add at most a 120ms background-color transition on rows and buttons.
- **Loading**: the prototype has none. Add skeletons matching row height (58px) and card shapes; never a spinner over the whole shell.
- **Error**: reuse the 403 panel treatment for authorization errors; a cream card with red mono label for 400s from the validator (e.g. "not an edge in the state machine").
- **Responsive**: designed at 1440–1500px. Below ~1180px collapse the Applications grid to one column (detail panel becomes a drawer/route), stat row to 2×2, postings to a single column, and the sidebar to icons only.

## State Management
Frontend state needed: `authed`/token pair, current `user` (`{name, email, role}` from the JWT), `page` (routing), `applications[]`, `selectedApplicationId`, `query`, `statusFilter`, `inspectPermissions`, `pendingTransition {id, from, to, title}`, `pendingNote`, `forbiddenBannerVisible`.

Fetching: `GET /api/applications/mine` for candidates, `GET /api/applications` for recruiter/admin; `GET /api/applications/{id}/events` on selection; `PATCH …/status` on confirm, then refetch. Token attach + silent refresh live in the `HttpInterceptor`; per-role route guards protect `/postings` management and the recruiter/admin lists.

**Mirror the validator on the client for enablement only.** Pure function `validate(from, to, role) → {ok, reason}`:
```
if (from is terminal)            deny "<from> is terminal — no outbound transitions for any role"
if (to == ACCEPTED)              from == OFFER ? (role == CANDIDATE ? allow : deny "only the candidate may accept an offer")
                                              : deny "ACCEPTED is reachable only from OFFER"
if (to == WITHDRAWN)             role == CANDIDATE ? allow : deny "withdrawal is candidate-only"
if (to == REJECTED)              role == RECRUITER ? allow : deny "rejection is recruiter-only"
if (forward[from] == to)         role == RECRUITER ? allow : deny "forward progression is recruiter-only"
otherwise                        deny "not an edge in the state machine"
```
`forward = {APPLIED: SCREENING, SCREENING: INTERVIEW, INTERVIEW: OFFER}`; `terminal = {ACCEPTED, REJECTED, WITHDRAWN}`. This is a UX affordance only — the server's `TransitionValidator` remains the authority and must be tested independently.

## Assets
No images. Google Fonts: Plus Jakarta Sans + IBM Plex Mono (self-host in production). Icons in the prototype are CSS placeholders (rounded squares, dots, `›`, `↗`, `⌕`) — swap for the project's icon library at 16–18px.

## Files
- `ApplyTrack.dc.html` — the interactive prototype (all 8 views, role switching, live transitions).
- `reference/01-overview.png`, `02-applications.png`, `03-postings.png`, `04-settings.png` — original visual references for the shell.
