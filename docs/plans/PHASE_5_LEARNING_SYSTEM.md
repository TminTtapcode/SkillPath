# Plan: P5 — Curated Learning and Task Execution

## Status

`DRAFT — discover/design on 2026-09-23; owner approval required before implementation`

## Objective and user story

Give a learner with an active goal a bounded, bilingual learn → practice → recall
experience that they can explicitly choose, leave, resume, and finish. The system
records what they did, without presenting the sequence as an adaptive recommendation
or treating a completion checkbox as proof of mastery.

Phase 5 supplies a versioned task catalog and an execution surface for Phase 6's
planner. Phase 6, not Learning, will select personalized tasks and publish a real
Today plan. Phase 7 will close the evaluation → state → replan loop.

## Authoritative references

- `AGENTS.md`, `DEVELOPMENT_RULES.md`, `PROJECT_CONTEXT.md`
- ADR-0001/0002/0003/0004/0006
- `docs/product/MVP_SCOPE.md`, `docs/product/USER_JOURNEYS.md`
- `docs/architecture/SYSTEM_ARCHITECTURE.md`, `MODULE_BOUNDARIES.md`, `DATABASE_DESIGN.md`
- `docs/domain/LEARNING_TASK_MODEL.md`, `PLANNER_ALGORITHM_V1.md`,
  `USER_KNOWLEDGE_STATE.md`, `ASSESSMENT_MODEL.md`, `ADAPTIVE_LOOP.md`
- `docs/api/API_CONTRACT.md`, `docs/api/openapi-v1.yaml`
- `docs/development/TESTING_STRATEGY.md`, `docs/research/OPEN_SOURCE_ADOPTION.md`
- `docs/plans/PHASE_ROADMAP.md`, `PHASE_4_PROGRESS_REVIEW.md`

## Current-state evidence

- The last committed baseline is `1a03544`; Phase 3, localization, and Phase 4 are
  implemented but remain uncommitted in a large working tree. Preserve those changes;
  accept/commit a baseline before Phase 5 implementation as `PROJECT_CONTEXT.md`
  already requires.
- Flyway V1–V13 exists. Phase 4 provides an evidence ledger, knowledge projection,
  review schedules, and leased outbox dispatch. There is no `learning` package,
  learning schema, task assignment, or learning frontend route.
- `GoalQueries` exposes an owned active goal and a lock operation. Knowledge exposes
  published graph/node summaries. Progress and Review currently expose learner HTTP
  reads, but no dedicated immutable Learning/Planner query contracts. The assessment
  API is diagnostic-only; no task evaluation or review-taking API exists.
- The current `/goal` screen is a diagnostic card and Knowledge State link, not a
  Today plan. The frontend already has React Query, generated API types, and
  Vietnamese/English localization. Goal daily budget is 30–180 minutes.
- `git diff --check` and `docker compose config --quiet` passed; Compose emitted the
  existing inaccessible local Docker-config warning. Frontend lint, 3 Vitest files / 8
  tests, and production build (245 modules) passed on 2026-09-23. Backend offline
  `clean verify` passed with 24 unit/architecture and 18 MySQL integration tests,
  including clean V1–V13 and V11–V13 upgrade paths. The initial sandboxed attempt
  could not read the local Maven cache; the successful rerun had cache access. The
  existing Flyway 11.7.2/MySQL 8.4 compatibility warning remains.

### Context conflicts requiring explicit approval

1. **CONTEXT CONFLICT — Phase 5 Today vs Planner authority.** The roadmap says Phase 5
   delivers a Today task UI, while the learning/planner specs say Planner chooses
   variants and composes the 1–3-item Today plan in Phase 6. Phase 5 will show a
   learner-selected study session on the current command center, explicitly *not* an
   adaptive Today plan. `GET /learning/today` and planner decisions wait for Phase 6.
2. **CONTEXT CONFLICT — mandatory planner decision ID.** `LEARNING_TASK_MODEL.md`
   describes every assigned task with `planner_decision_id`, but no planner exists.
   Proposed resolution: a task has exactly one provenance: `LEARNER_SELECTED` with a
   null planner decision, or future `PLANNER` with a non-null decision. The database
   enforces the combination. This changes the learning specification only after owner
   approval.
3. **CONTEXT CONFLICT — practice/recall vs evidence.** The roadmap's executable sequence
   arrives before task-specific assessment. Phase 5 self-reported practice and recall
   are engagement facts, not Assessment evidence. No mastery, confidence, review
   interval, prerequisite, or goal state changes. Evaluated mini-assessments require a
   later approved Assessment contract; the UI must not imply otherwise.
4. The shared outbox dispatcher marks unknown event types failed after retries. Phase 5
   will not emit an unconsumed `LearningTaskCompleted` event. It records immutable
   learning lifecycle audit rows; Phase 6/7 can add a registered, versioned handoff.

## Decisions proposed for owner approval

Approval of this plan accepts these bounded Phase 5 choices:

1. The first catalog has one project-authored, bilingual 30-minute foundational
   sequence for the published Java Backend graph: 10-minute learn, 15-minute practice,
   5-minute recall. It targets a root concept with no hard prerequisites. Exact
   pedagogical text and seed fixtures are reviewed before release. IDs and semantics
   are language-independent; English is canonical, Vietnamese is an overlay.
2. A learner explicitly opens and starts the curated sequence. This is not a ranked
   recommendation, diagnostic-based placement, or automatic task assignment. One
   active learning session per goal is allowed; repeat after completion requires an
   explicit new start. No timer runs merely because a page is open.
3. Session/task content, resource references, localized presentation version, sequence
   order, and graph version are pinned when assigned. Locale may change labels during
   viewing without changing task IDs, checklist IDs, completion input, or history.
4. `LEARNER_SELECTED` and future `PLANNER` are mutually exclusive assignment sources.
   The latter remains an internal Learning application command for Phase 6 and cannot
   be forged by a browser request.
5. Phase 5 task evaluation mode is `SELF_REPORT` or `NONE`. Completion requires the
   declared checklist and bounded actual minutes, but creates no Assessment attempt
   or Evidence. Raw free text, uploaded code, external link tracking, and AI are absent.
6. All task mutations require `Idempotency-Key`. Same key and canonical payload
   replays the original result; changed payload conflicts. Versioned status
   transitions and audit rows are committed in one transaction.
7. No external content or new Maven/npm dependency is adopted. Resources are
   project-authored, stored in the versioned catalog with provenance and license
   metadata. Adding an external link/content later requires exact source/license,
   bounded section, maintenance, and approval review.

## Scope

### In scope

- Learning-owned versioned resources, exact knowledge-resource mappings, task
  templates/variants, declared sequence, translations, assignment snapshots, sessions,
  lifecycle, idempotency, and append-only lifecycle audit.
- One Java Backend foundation sequence; catalog and schema remain curriculum-neutral.
- Learner catalog, explicit start/resume, task detail, start, complete, skip, blocked,
  resume-from-blocked, and abandon flows.
- A learner-selected study-session UI linked from `/goal`, with all current locales,
  accessibility, and loading/empty/error/recovery states.
- Narrow immutable application contracts for goal/knowledge lookups and future Planner
  catalog/assignment integration; no cross-module persistence imports.
- Flyway, OpenAPI/generated types, migration, unit, integration, security, frontend,
  architecture, and regression tests.

### Out of scope

- Personalized Today plan, candidate ranking, prerequisite gating for plan selection,
  time override, plan revision, or recommendation reasons (Phase 6/7).
- Task-specific Assessment, objective/rubric/code grading, answer-key exposure,
  learning completion evidence, review submission, or mastery updates.
- AI, external course ingestion, paid/video resources, curator UI, analytics, and the
  visual goal map.
- Auto-expiry or automatic repetition/remedial selection without a versioned policy.

## Design and checkpoints

### P5.0 — Contract lock and accepted baseline

- Approve the four context resolutions and seven decisions above. Commit or explicitly
  accept the Phase 3/localization/Phase 4 baseline before implementing Phase 5.
- Update `LEARNING_TASK_MODEL.md` to distinguish learner-selected execution from
  Planner-assigned tasks, and `API_CONTRACT.md` to keep `/learning/today` reserved for
  Phase 6. Do not alter planner formula, prerequisite rules, or review policy.
- Add a read-only knowledge contract for graph-compatible root-node validation and
  a narrow goal contract for current owned goal; do not query other modules' tables.

Gate: no unresolved authority conflict, no new dependency, explicit owner approval.

### P5.1 — Catalog and bilingual seed

Add forward-only `V14__create_learning_catalog.sql`:

- `resources` / `resource_versions`: immutable key/version, type, canonical internal
  content reference, project provenance, license, provider, status, difficulty, and
  estimated minutes.
- `resource_version_translations`: locale-specific title and bounded body. English
  canonical content stays on the resource version; Vietnamese is an overlay.
- `knowledge_resources`: exact graph/node/version and bounded section/purpose mapping.
- `task_templates` / `task_template_versions`: stable key, version, activity type,
  difficulty, estimated/min/max minutes, evaluation mode, status, variant group,
  sequence key/position, resource version references, immutable instructions and
  checklist IDs.
- `task_template_translations`: localized title/instructions/checklist labels only.
- `task_template_knowledge`: at least one `PRIMARY` mapping per active version, valid
  graph/node FK, dimension and weight checks.
- `learning_sequences` / `learning_sequence_items`: immutable ordered template
  versions with uniqueness on `(sequence, position)` and no duplicated template
  version.

Add `V15__seed_java_backend_learning.sql` with exactly one 30-minute three-step
sequence. Seed is authored by SkillPath, not copied from external tutorials. Domain
validation enforces complete checklist IDs, ordered activity types, durations summing
to 30, active version compatibility, and project-content provenance. Retired catalog
versions remain readable for existing sessions but cannot be newly assigned.

Gate: clean V1–V15 and V13–V15 upgrade on MySQL 8.4; FK, unique, status, range,
translation, mapping, and seed-integrity tests pass.

### P5.2 — Assignment, lifecycle, and idempotency

Add `V16__create_learning_execution.sql`:

- `learning_sessions`: owner, active goal, sequence/version, graph version,
  `LEARNER_SELECTED`/`PLANNER` source, status, started/completed timestamps,
  optimistic version, and generated unique active-goal key.
- `learning_tasks`: session, owner, goal, pinned template version and position,
  nullable planner decision ID constrained by source, payload snapshot, planned/actual
  minutes, status, assigned/started/completed timestamps, optimistic version;
  unique session position and one task per sequence item.
- `learning_task_events`: append-only actor-scoped status transition with task ID,
  from/to, safe reason code, event time, and unique command ID; no raw answer content.
- `learning_command_receipts`: owner-scoped unique idempotency key, command/payload
  hash, resource/outcome, and timestamp; bounded retention policy must be decided
  before production cleanup. A different command/payload cannot reuse a key.

Application invariants:

- Authenticated principal supplies user ID; goal must still be owned and active.
- Explicit start snapshots the three template versions atomically. Concurrent starts
  converge on one active session; different-key duplicates do not create tasks.
- `ASSIGNED → IN_PROGRESS/SKIPPED`; `IN_PROGRESS → COMPLETED/ABANDONED/BLOCKED`;
  `BLOCKED → IN_PROGRESS`. Terminal tasks never mutate. Completing all three
  accepted steps closes the session once. A blocked step pauses the sequence.
- Sequence order is enforced for start/complete; later steps cannot be skipped ahead
  to manufacture completion. If a learner skips or abandons one step, the session ends
  `STOPPED`, not `COMPLETED`; a new explicit session is needed to retry.
- Completion checklist IDs are server-defined; actual minutes are bounded and only
  engagement metadata. Learning cannot persist a score/evidence/mastery value.
- No outbox write until a registered consumer/contract exists; lifecycle audit is the
  durable handoff for Phase 6/7 query contracts.

Gate: pure state-transition fixtures, transaction/concurrency tests, idempotent replay,
and exact session/task/event row counts.

### P5.3 — Learner API and security

Proposed authenticated routes:

```http
GET  /api/v1/learning/sequences
GET  /api/v1/learning/sequences/{sequenceKey}
POST /api/v1/learning/sequences/{sequenceKey}/sessions
GET  /api/v1/learning/sessions/active
GET  /api/v1/learning/sessions/{sessionId}
POST /api/v1/learning/tasks/{taskId}/start
POST /api/v1/learning/tasks/{taskId}/complete
POST /api/v1/learning/tasks/{taskId}/skip
POST /api/v1/learning/tasks/{taskId}/blocked
POST /api/v1/learning/tasks/{taskId}/resume
POST /api/v1/learning/tasks/{taskId}/abandon
```

Commands require CSRF and `Idempotency-Key`; `complete` accepts bounded
`actualMinutes` and a list of declared `completedStepIds`, never trusted score,
evidence, user ID, planner decision, or status. Same key/hash returns `200` replay;
new accepted creation/transition returns `201`/`200` as documented in OpenAPI.
Missing/cross-owner session/task is concealed `404`; invalid sequence/selection is
`422`; stale transition/answered or key conflict is `409`; unauthenticated is `401`,
CSRF-invalid `403`. Localized GETs return `Content-Language`; locale never enters a
command hash.

Gate: OpenAPI/generated-type parity, ownership/CSRF, payload bounds, locale stability,
missing catalog and retired-version paths, and no private content leakage.

### P5.4 — Learner UI

- Add `/learning` for curated sequence discovery and `/learning/session/:id` for
  active/historical session execution. Link from Active Goal's command center under a
  clearly named **Self-selected study** section; keep the diagnostic and Knowledge
  State paths. Do not label the curated sequence “recommended for you” or show a
  fabricated personalized Today plan.
- Show one bounded task at a time, duration, exact resource section, checklist,
  remaining steps, and explicit LEARN/PRACTICE/RECALL labels. Preserve checklist
  state while a command is pending; retry with the same in-memory idempotency key and
  refetch on ambiguous reload/conflict.
- Handle loading, no active goal, unavailable catalog/graph, empty session, active,
  blocked, stopped, completed, session loss, network failure, and stale tab states.
  Do not increment knowledge estimates or review intervals in the UI.
- Vietnamese/English content and chrome, keyboard-operable controls, visible focus,
  accessible field errors, non-color status cues, mobile layout, and reduced motion.

Gate: frontend tests for start/resume/complete/skip/blocked/recovery, bilingual
content, accessibility assertions, and absence of mastery/plan claims.

### P5.5 — Audit, documentation, and handoff

- Run full backend verification, clean and upgrade migrations, API/security,
  architecture, frontend generation/format/lint/test/build, Compose, and diff/secret/
  provenance audit. Inspect lifecycle race and rollback paths.
- Update all affected domain, architecture, API, test, roadmap, and context documents.
  Mark this plan `DONE` only after applicable gates pass.
- Expose immutable Learning catalog/assignment views to Phase 6 through an application
  contract. Phase 6 owns actual `GET /learning/today`, deterministic selection and
  prerequisite/time-fit checks; Phase 7 connects task evaluation and replanning.

## Expected files

- New `backend/src/main/java/com/skillpath/learning/{api,application,domain,infrastructure/persistence}/**`.
- New Flyway `V14__create_learning_catalog.sql`, `V15__seed_java_backend_learning.sql`,
  `V16__create_learning_execution.sql`; matching unit and MySQL integration tests.
- Narrow contracts/implementations under `goal.application` and
  `knowledge.application`; `ArchitectureTest.java`.
- `docs/api/openapi-v1.yaml`, generated `frontend/src/shared/api/schema.d.ts`, API
  client and tests; `frontend/src/features/learning/**`, `App.tsx`, `ActiveGoalPage.tsx`,
  i18n dictionaries, and shared styles only as needed.
- `docs/domain/LEARNING_TASK_MODEL.md`, `docs/domain/ADAPTIVE_LOOP.md`,
  `docs/architecture/{DATABASE_DESIGN,MODULE_BOUNDARIES,SYSTEM_ARCHITECTURE}.md`,
  `docs/api/API_CONTRACT.md`, `docs/development/TESTING_STRATEGY.md`,
  `docs/plans/PHASE_ROADMAP.md`, `PROJECT_CONTEXT.md`, and this plan.

Any new dependency, external curriculum content, task-generated evidence, planner
ranking, review completion, or migration outside this scope needs owner review.

## Tests and acceptance criteria

1. Given an active Java Backend goal, when the learner explicitly starts the seeded
   sequence, exactly one active session and three ordered, version-pinned tasks exist.
2. Given two concurrent starts or a repeated key/payload, no duplicate session/task
   appears; key reuse with different payload returns a safe conflict.
3. Given a learner leaves and returns, the same current position and persisted task
   state resume; a locale switch changes text only.
4. Given a valid step completion, exactly one transition/audit row is committed and
   replay returns the same outcome; concurrent duplicate commands converge.
5. Given a skipped/abandoned step, the session stops honestly and is not counted as
   a completed sequence. A blocked task resumes only through its declared transition.
6. Given reading, practice, or self-reported recall completion, Assessment attempt,
   Evidence ledger, Knowledge State, and review rows do not change.
7. Given malformed checklist/actual minutes, out-of-order transition, retired new
   catalog item, missing goal, expired auth, CSRF failure, or cross-owner ID, no partial
   task/event mutation or private existence leak occurs.
8. Given a new graph/catalog version, existing sessions continue from their pinned
   versions and newly started sessions use only currently compatible active versions.
9. Given Phase 6 is absent, the UI shows a self-selected session and does not call it
   a personalized plan, claim prerequisite readiness, or change mastery.
10. Existing Phase 1–4 and localization suites remain green.

## Rollout and rollback

- Accept a reviewable Phase 4 baseline first. Apply additive V14–V16 before enabling
  the Learning API/UI. Keep existing goal, diagnostic, and knowledge routes working.
- Feature entry appears only when the Learning API and seed are deployed. Older code
  safely ignores the additive tables. Existing pinned sessions remain readable if a
  catalog version is retired; corrections create a new immutable version/migration.
- Rollback application code leaves additive Learning tables unused. Database recovery
  is restore/forward-fix; never edit applied Flyway files or delete learner history.

## Risks and open decisions

- Owner must accept learner-selected study as the Phase 5 bridge; the roadmap phrase
  “Today task UI” must not be mistaken for Phase 6's personalized Today plan.
- A self-check is pedagogical practice but not reliable skill evidence. The full MVP
  adaptive-loop story will not pass until task-specific evaluation and replanning are
  implemented in later phases.
- The exact authored bilingual lesson and checklist require pedagogical review.
  External resource licensing/source metadata must be verified before any external
  content or links are added.
- A 30-minute seed matches the minimum configured daily budget, but user-selected
  sessions do not implement today's override or planner time-fit policy.
- Idempotency receipt retention, catalog/content moderation, and production data
  retention need explicit operational policy before a public launch.
- Flyway 11.7.2/MySQL 8.4 compatibility warning needs continued recheck.

## Validation results

Design only. No Phase 5 implementation or Phase 5 tests have run. Record exact
commands, exit codes, test counts, migration paths, warnings, and deviations here
before marking `DONE`.

## Documentation updates

On approval/implementation, reconcile the four context conflicts above in the
Learning Task model, API contract, architecture/database diagrams, testing strategy,
roadmap, and `PROJECT_CONTEXT.md` in the same change as behavior.
