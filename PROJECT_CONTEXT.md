# Project Context

## Product

- Working name: **SkillPath**
- Category: adaptive learning planner
- Core promise: give the learner a small, achievable plan for today and recalculate the route from observed performance
- Initial audience: students and junior developers pursuing Java Backend Internship readiness
- Product horizon: expand to multiple IT specializations after validating the first curriculum; Java Backend is the initial track, not a permanent domain boundary
- Product metaphor: Google Maps for learning — show the next move, reroute when reality changes

## Current phase

**Phase 3 — Diagnostic Assessment and Concept Evidence completed locally.** The
repository now delivers the authenticated eight-question Java Backend diagnostic,
deterministic objective evidence, assessment-owned idempotency, and a durable Phase 4
handoff. The cross-cutting L10 localization milestone is also completed locally: the
current learner journey and project-authored content support Vietnamese and English.
Validation evidence is recorded in `docs/plans/PHASE_3_ASSESSMENT.md` and
`docs/plans/LOCALIZATION_V1.md`.

**Phase 4 — Progress and Review is completed locally.** Assessment evidence is consumed
through a leased/retryable local outbox, stored in an append-only ledger, and projected
by `knowledge-state-v1`. Learners can inspect localized effective knowledge state;
mastered concepts receive deterministic `review-interval-v1` schedules. Phase 4 does
not create a Today plan—those authorities remain in later phases.

**Phase 5 — Learning system P5.0–P5.5 is implemented and committed (`9c1d410`).**
A learner with an active Java Backend goal can explicitly select a bilingual, versioned 30-minute
learn/practice/recall sequence, resume it, and record task transitions. Activity is
self-reported; it creates no assessment evidence, mastery change, review update, or
personalized Today plan. Validation and remaining risks are in
`docs/plans/PHASE_5_LEARNING_SYSTEM.md`.

**Phase 6 — Planner and visual roadmap is completed locally.** Explicit Today
generation/read/revision and a version-pinned, paged roadmap are implemented.
The deterministic planner uses four-root curated short variants, one
`projectionAsOf`, immutable snapshots and superseding revisions. Planner reaches
Goal, Knowledge, Progress, Review, and Learning only through application contracts.
The visual map uses dependency-free SVG plus a semantic keyboard-accessible list;
the same-fixture React Flow/Cytoscape.js spike retained this approach without adding
a dependency. Automatic evidence-driven replan, time overrides, and goal completion
remain outside Phase 6. Validation and launch risks are tracked in
`docs/plans/PHASE_6_PLANNER.md` and `docs/research/PHASE_6_VISUAL_MAP_SPIKE.md`.

**Phase 7 — Adaptive loop is completed.** V21–V24 established the
objective task-check primitive and ordered Evidence → Progress → Review → durable
Planner request chain. V25 and the final P7 implementations add a
`planner-v2` request executor, immutable carry-forward task links, audited
day-local time overrides, explicit missed-day refresh, and bilingual
pending/stale UI states. Rollout gates are enabled by default for both
the task-check endpoint and the replan-worker background polling. The golden
path from registration through objective check evidence and roadmap revision
has passed regression. This does not claim that Goal can auto-complete or
practical skill has been evaluated — AI hardening happens in Phase 8.

## Confirmed decisions

- Build a web MVP first; no native mobile app in MVP.
- Use a modular monolith, not microservices.
- Backend: Java 21 and Spring Boot 3.x.
- Frontend: React + TypeScript + Vite.
- Database: MySQL 8.4 LTS.
- MySQL Workbench is a local GUI/inspection tool, not migration authority.
- Flyway owns schema evolution.
- Planner v1 is deterministic and explainable.
- The primary experience remains Today, supported by a visual goal map showing progress, prerequisites, blocked concepts, and the reason for the next task.
- Curriculum and graph contracts must support additional IT specializations without introducing subject-specific rules into the planner core.
- Open-source projects are inputs to bounded research/spikes, not architecture or domain authorities. Forking an existing tutor is not the default strategy.
- AI is an assistant behind validated contracts, not the product architect or final decision maker.
- Diagnostic evidence is observational, not authoritative mastery: Assessment records
  what an attempt demonstrated, Knowledge State estimates what the learner currently
  knows, and Planner decides what the learner should do next.
- New learner registration and goal setup default to the IANA timezone
  `Asia/Ho_Chi_Minh`; existing stored profile/goal timezones are not silently rewritten.
- Supported presentation locales are `vi-VN` and `en`. New browsers default to
  Vietnamese, the browser-only selection is persisted in `localStorage`, and unsupported
  request languages fall back to canonical English.
- The four core IP areas are Knowledge Graph, Assessment Model, User Knowledge State, and Planner Algorithm.
- Dockerize local infrastructure first. Containerize backend/frontend after the first end-to-end vertical slice runs locally.
- Redis, queues, Kubernetes, microservices, social features, marketplace, voice tutor, and complex gamification are outside MVP.

## MVP learning loop

1. User defines a goal, target date, and daily time budget.
2. User completes a short diagnostic.
3. System creates an evidence-backed knowledge state.
4. Planner chooses one small set of tasks fitting today's time.
5. User learns, practices, and recalls.
6. Evaluation produces concept-level evidence.
7. Knowledge state and review schedule update.
8. Planner generates the next plan.

## Current documentation status

- Product vision and MVP scope: defined.
- System architecture/module boundaries: defined for MVP.
- MySQL logical data model and migration rules: defined; Phase 1 identity/session/goal,
  Phase 2 knowledge graph V6/V7 and Phase 3 assessment V8/V9 migrations are
  implemented; V10/V11 add localization overlays; V12/V13 add outbox leasing, the
  evidence ledger/projection, misconceptions, and review schedules.
  V14–V16 add the curated Learning catalog, bilingual resource/step seed, immutable
  execution snapshot, receipts, and lifecycle audit. Clean and historical upgrade
  paths are tested on MySQL 8.4.
- Core domain specifications: defined at v1 design level.
- Learning task execution: implemented for learner-selected and planner-assigned,
  self-reported study; task evaluation remains at v1 design level.
- Learner-facing visual goal-map/read-model direction: defined for MVP with accessibility and progressive-disclosure constraints.
- Open-source adoption register, license/provenance workflow, completed Phase 6
  visual-map spike, and post-MVP algorithm evaluation path: documented.
- API contract: Phase 1 identity/goal, Phase 2 published graph/admin lifecycle,
  Phase 3 diagnostic/evidence, Phase 5 Learning, and Phase 6 Today/roadmap endpoints are published in
  `docs/api/openapi-v1.yaml`;
  localized reads document `Accept-Language`/`Content-Language`; frontend TypeScript
  types are generated and checked for drift.
- AI boundary: defined.
- Development, testing, and Docker strategies: defined.

## Known open decisions

- Final public brand/domain and trademark availability.
- AI provider/model and cost limits.
- Pedagogical review and expansion of the initial project-authored Java Backend graph;
  learning-resource licensing remains a later content decision.
- Which IT specialization follows Java Backend and the evidence required to prioritize it.
- Any later graph-library adoption beyond the approved dependency-free Phase 6 renderer.
- Whether later review data justifies a versioned move from interval policy v1 to FSRS.
- Hosting provider and production observability stack.
- Production hosting, observability, backup/restore, immutable image digests, and SBOM automation.
- Flyway 11.7.2 (managed by Spring Boot 3.5.16) warns that MySQL 8.4 is newer than its
  most recently tested MySQL line; clean and repeat migrations pass against MySQL 8.4,
  but upgrades require rechecking this compatibility.

## Phase 1 security decision

The web MVP uses a server-managed secure HTTP-only cookie session. Non-local cookies
must use `Secure` and an explicit `SameSite` policy; state-changing requests require
CSRF protection. Authentication rotates the session and logout invalidates it
server-side. A token-based browser flow requires deployment evidence and an approved
plan or ADR change.

## Implemented Phase 1 surface

- Spring Boot 3.5.16 modular monolith on Java 21; React 19/Vite 8 SPA on Node 24.
- MySQL 8.4 and Flyway V1–V5; Hibernate validates and never creates schema.
- Cookie session auth, CSRF, BCrypt cost 12, login lockout, persistent session registry,
  five-session cap, logout revocation, and ownership derived from the principal.
- Public goal-template list; authenticated idempotent active-goal create/read.
- Multi-stage non-root images, Compose app profile, pinned-action CI, run/test/audit scripts.
- SkillPath Design System (UI UX Pro Max): Minimalist Technical Swiss (Border-First)
  with Inter + JetBrains Mono typography, Slate neutrals, 8pt spatial grid, and 4–8px
  radii. Includes 5 pedagogical knowledge state tokens (Unreached, Learning, Review Due,
  Mastered, Blocked), 15.8:1 text contrast, accessible form error associations, and
  a Today Command Center layout with high-value task preview.

## Frontend design system baseline

- Architecture: Border-first, zero-gradient, zero-glassmorphism technical interface.
- Typography: Inter for UI; JetBrains Mono for syntax, IDs, and tabular figures.
- Tokens: Centralized CSS variables in `src/shared/styles/global.css` (`--sp-*`).
- Semantic Knowledge States: Teal 700 (`#0f766e`) for Mastered, Blue 600 (`#1d4ed8`) for Learning,
  Amber 700 (`#b45309`) for Review Due, Rose 700 (`#be123c`) for Blocked.
- Accessibility (WCAG 2.2 AA): 42–44px minimum touch targets, `aria-describedby` field error
  association, `aria-invalid` flags, `onBlur` validation, and `@media (prefers-reduced-motion)`.
- Screen Surface:
  - Header: Workstation navigation shell with track indicator and daily countdown.
  - Auth: Accessible Login and Register forms with field-level live descriptions.
  - Goal Setup: Track selection cards with topic tags and segmented study-minute pills.
  - Active Goal: "Today Command Center" displaying today's high-value task, daily budget,
    and active track specifications.

## Implemented Phase 2 surface

- Flyway V6 graph/version/node/relation/goal-mapping/lifecycle-audit schema and V7
  project-authored `JAVA_BACKEND` v1 seed: 17 nodes and 23 relations.
- Pure deterministic validation, cycle paths, stable topology, direct/transitive
  traversal, and non-persisted prerequisite-frontier fixtures.
- Public, published-only node/prerequisite/dependent and bounded goal-graph queries with
  version stamps and opaque cursors.
- `CURATOR`/`ADMIN` validation and publication with CSRF, principal-derived audit actor,
  idempotent replay, atomic retire/publish/audit, and one-winner concurrency behavior.
- Goal-owned HTTP adapter delegates through the public knowledge application contract;
  no cross-module persistence import or new graph dependency was introduced.

## Implemented Phase 3 surface

- Flyway V8 assessment schema and V9 project-authored eight-question Java Backend
  diagnostic seed with immutable question versions and graph-version mappings.
- Seven-day create/resume flow pinned to the active goal, published graph, question
  snapshot, and `assessment-objective-v1` policy; concurrent starts serialize through
  the public goal contract and converge on one session.
- Deterministic single/multiple-choice scoring, bounded `RECOGNITION`/`UNDERSTANDING`
  evidence, idempotent sequential attempts, and atomic attempt/evidence/completion/
  outbox writes.
- Ownership-concealed diagnostic APIs, answer-key secrecy, CSRF/session enforcement,
  and learner UI for start/resume, questions, expiry, recovery, and evidence results.
- Pending `AssessmentEvidenceCreated` outbox rows are the intentional Phase 4 handoff;
  Phase 3 does not calculate mastery, prerequisite satisfaction, or a Today plan.

## Implemented localization surface

- Dependency-free typed Vietnamese/English UI dictionaries cover every current learner
  route, validation/async state, evidence boundary, and accessibility label. The header
  selector persists only the allowlisted locale and synchronizes the HTML `lang` value.
- The central API client sends `Accept-Language`; locale-aware goal, public knowledge,
  diagnostic-question, and result reads return `Content-Language`.
- Flyway V10 owns translation overlays and V11 seeds one goal template, all 17 graph
  nodes, and all eight diagnostic question versions in Vietnamese.
- English stays canonical. Translation overlays preserve graph/question/option IDs;
  scoring, idempotency, evidence, outbox payloads, ownership, and authorization remain
  locale-independent. A mid-question language switch preserves the selected option IDs.

## Implemented Phase 5 surface

- Flyway V14–V16 define immutable resource/template versions, the project-authored
  bilingual Java Backend sequence, goal-owned sessions and task snapshots, scoped
  idempotency receipts, and append-only transition audit.
- The Learning API exposes compatible catalog and session reads plus explicit
  start/resume/task commands. Server-side ownership, CSRF, order, checklist, graph
  compatibility, and lifecycle rules are enforced; retry and concurrent start are
  covered by MySQL integration tests.
- The learner UI has self-selected catalog/session routes, one step at a time,
  English/Vietnamese content, retry recovery, and clear no-mastery/no-Today-plan
  language. The Active Goal screen links to study without claiming planner output.
- An immutable Learning application query contract is available for Phase 6.
  Learning activity does not emit assessment evidence or mutate Knowledge State,
  Review, or Progress.

## Implemented Phase 6 surface

- Flyway V17–V19 add planner snapshots/decisions/revisions/receipts, permit planner
  sessions without a curated sequence ID, and seed three independently complete
  bilingual short variants alongside the existing programming root material.
- The owner approved `goal-daily-budget-v2`: Flyway V20, Goal validation, OpenAPI,
  and Goal Setup now accept stored 20–180 minute daily budgets. Existing goals are
  untouched; Today still takes no client budget override. The 20-minute policy
  fixture also remains meaningful as remaining time within a longer plan.
- The planner captures one server-side `projectionAsOf`, pins published graph and
  dynamic state/review/catalog inputs, and uses deterministic `planner-v1` ranking.
  A hard unmet prerequisite blocks a dependent task; an oversized variant is never
  assigned. No client-supplied budget, ranking, or Goal completion command exists.
- Today is generated only on explicit POST. Same-key replay and current-day
  uniqueness converge on one result. An explicit revision supersedes immutable
  history only while every planner task remains unstarted; manual sessions are not
  replaced. Historical plans remain owner-scoped and readable.
- The `/today` and `/roadmap` routes support English/Vietnamese UI. The roadmap uses
  a pinned plan snapshot with a stale badge and pairs dependency-free SVG with an
  semantic concept list. The planner graph remains bounded to 200 nodes, with
  stable, owner-scoped roadmap cursor pages and refusal to merge mismatched stamps.
- Final full backend `clean verify` passed 37 unit/architecture and 30 MySQL
  integration tests, including clean V1–V20 and historical upgrade paths,
  50/200-node paging, stale/revision/concurrency, and rollback fixtures. Frontend
  generation/format/lint, 21 tests, and production build passed. The owner approved
  dependency-metadata upload to npm registry; `scripts/audit.ps1` reported 0
  vulnerabilities. The visual-map spike retained the dependency-free renderer.

## Next work sequence

1. Continue the owner-approved Phase 7 implementation. The first checkpoint
   removes Review's direct `user_knowledge` write, composes the due date through
   a Review application query, adds an attempt-level event barrier, and implements
   a rollout-gated objective task check with bilingual UI. The pure adaptive
   budget rule and Learning-owned partial-task expiry/append contract are tested,
   but not yet connected to Planner. A durable Planner request is recorded after
   Review. V24 adds a leased, retryable Planner request worker that locks the
   owned Goal and calls a transactional executor. The worker is disabled by
   default pending the complete end-to-end gate; task checks also remain gated
   off. The current P7.4/7.5 working tree includes immutable partial-plan
   carry-forward, daily override, and missed-day refresh; targeted MySQL tests
   have passed. Complete a real diagnostic → objective task check → Review →
   revised Today/roadmap golden path, full regression/security audit, and a
   freshly rebuilt Compose browser smoke before enabling either gate or
   marking P7 done. Exact validation is tracked in the P7 plan.
2. Before public launch, conduct real-device and assistive-technology roadmap
   usability checks, pedagogical review of seeded content, privacy retention design,
   and Flyway/MySQL compatibility review. Do not infer full-curriculum coverage from
   the four-root content pack.

## Context maintenance

Update this file after each approved milestone. Keep it factual: completed, in progress, blockers, accepted decisions, known problems, and next approved task. Do not use it as a daily diary.
