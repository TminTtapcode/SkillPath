# Plan: P6 — Deterministic Today Planner and Visual Roadmap

## Status

`DONE — owner-approved Phase 6 slice implemented and validated on 2026-09-23`

## Objective and user story

An authenticated learner with an active goal can explicitly generate a small, time-fit
Today plan from the published graph, observed knowledge state, due reviews, and curated
task variants. The learner can see why each task was selected, start the assigned work,
and inspect a visual roadmap that uses the same versioned planning snapshot. The
planner never treats self-reported activity as mastery and never decides goal completion.

Phase 6 creates an initial, deterministic plan and permits a guarded explicit revision
of wholly unstarted planner assignments. Evidence-triggered replanning, missed days,
time overrides, and evaluated task completion remain Phase 7 work.

## Authoritative references

- `AGENTS.md`, `DEVELOPMENT_RULES.md`, `PROJECT_CONTEXT.md`
- ADRs `0001-modular-monolith`, `0002-mysql-flyway-workbench`,
  `0003-deterministic-planner`, `0004-ai-boundary`, `0006-web-session-authentication`,
  `0007-goal-daily-budget-v2`
- `docs/architecture/{SYSTEM_ARCHITECTURE,MODULE_BOUNDARIES,DATABASE_DESIGN}.md`
- `docs/domain/{PLANNER_ALGORITHM_V1,KNOWLEDGE_GRAPH,USER_KNOWLEDGE_STATE,LEARNING_TASK_MODEL,ADAPTIVE_LOOP}.md`
- `docs/api/API_CONTRACT.md`, `docs/api/openapi-v1.yaml`
- `docs/product/{MVP_SCOPE,USER_JOURNEYS}.md`
- `docs/development/TESTING_STRATEGY.md`, `docs/research/OPEN_SOURCE_ADOPTION.md`
- `docs/plans/{PHASE_ROADMAP,PHASE_5_LEARNING_SYSTEM}.md`

## Discovery baseline (before implementation)

- Baseline is clean `main` at `9c1d410` (same as `origin/main` at discovery).
  The committed Phase 5 validation records 26 backend unit/architecture and 21 MySQL
  integration tests, 13 frontend tests, lint/build, clean/upgrade Flyway checks, and
  local Compose health. This turn reran frontend lint and Vitest: exit 0, 4
  files/13 tests; `docker compose config --quiet`: exit 0 with the existing
  Docker config-file permission warning. The PowerShell `npm` shim was blocked
  by execution policy;
  `npm.cmd` succeeded. Full backend tests were not rerun for this design-only change.
- V7 publishes a 17-node, 23-relation Java Backend graph. V12/V13 provide
  `knowledge-state-v1` and due reviews; V14–V16 provide bilingual task content and
  execution. No planner package, Today endpoint, roadmap endpoint, or planner tables
  exist. Frontend routes expose `/goal`, `/knowledge`, and learner-selected `/learning`.
- Phase 5 seeds exactly one 30-minute sequence (10/15/5 minutes) mapped to node
  `1001` only. It is not a personalized plan. `LearningQueries` exposes sequences
  and assignments but not standalone active variants, mappings, difficulty, or a
  planner assignment command. `learning_sessions.sequence_id` is non-null and only
  one session per goal may be active; planner-owned 1–3 item plans cannot be stored
  as-is.
- `GoalQueries` omits target date, daily minutes, and timezone. Knowledge has no
  full immutable planning graph contract. Progress and Review have no bounded,
  same-as-of planner query contract. Planner must not import their repositories or
  derive truth from HTTP DTOs.
- `user_knowledge` is unique by `(user_id, knowledge_node_id)` and carries a graph
  version; evidence reads currently key by user/node, not graph version. A future
  graph publication cannot be silently combined with old projections. Phase 6 must
  reject incompatible versions until an explicit graph/state migration policy exists.
- Current task evaluation is `SELF_REPORT`/`NONE`, not failure or mastery evidence.
  Phase 3 diagnostic has no task-variant failure history. Recent-variant-failure
  and misconception signals may be absent; absence must be represented as zero/not
  observed, never invented from engagement.

### Context conflicts resolved by owner approval

1. **CONTEXT CONFLICT — goal completion authority.** Planner v1 fallback says return
   `GOAL_COMPLETED` when all nodes are mastered; `ADAPTIVE_LOOP.md` assigns the final
   transition to Goal with additional confidence/application/misconception checks.
   Approved resolution: planner returns `GOAL_COMPLETION_CANDIDATE` without mutating
   Goal or claiming completion. The algorithm text is reconciled in this change.
2. **CONTEXT CONFLICT — fallback availability.** Planner v1 offers a short
   micro-assessment when no task fits; no repeatable micro-assessment task/evaluator
   exists. Approved Phase 6 behavior: return `NO_SAFE_RECOMMENDATION` with
   `NO_TIME_FIT_VARIANT` and suggest more time; do not reuse the one-baseline
   diagnostic or fabricate a task. A true micro-assessment requires a later approved
   assessment/content design.
3. **CONTEXT CONFLICT — Phase 6 revisions versus Phase 7 reroute.** The roadmap
   lists revisions in Phase 6 but assigns evidence-triggered replan, missed days,
   and time override to Phase 7. Approved Phase 6 slice supports only explicit,
   idempotent revision while every planner task is unstarted; Phase 7 extends this to
   preserve completed/in-progress tasks and react to events.
4. `PROJECT_CONTEXT.md` listed review/commit Phase 5 after `9c1d410` was committed;
   this stale next-work entry was corrected during design.

## Owner-approved decisions and hard locks

The owner confirmed the decisions below on 2026-09-23. These three constraints are
normative for every Phase 6 checkpoint:

1. **One `projectionAsOf` for the whole snapshot.** Capture one server-side instant
   before reading dynamic inputs; Progress effective state, Review due state,
   misconception state, candidate signals, plan explanations, and roadmap overlays
   must all be derived as of that same instant. Pin graph, catalog, and policy
   versions in the snapshot. A module must not substitute its own clock read or
   silently mix a later projection into the decision.
2. **Immutable, superseding revisions.** Once persisted, a planning snapshot,
   decision content, candidate breakdown, and plan revision content cannot be
   rewritten. A permitted replan creates a new revision linked to the superseded
   revision; only the current/superseded pointer or lifecycle marker may transition
   atomically. Historical revisions and task audit remain readable.
3. **Application contracts only.** Planner reads or commands Goal, Knowledge,
   Progress, Review, and Learning exclusively through their public application
   contracts. It must never import or query another module's repository, entity,
   persistence adapter, or controller, including through a cross-module SQL join.

1. `planner-v1` keeps the published candidate, hard-prerequisite, weighted-score,
   bonus, tie-break, 100-candidate limit, 1–3-item, and zero-tolerance rules.
   Java Backend remains seed data, never a planner branch.
2. The planning day is `LocalDate` in the goal's stored IANA timezone, and the
   budget is its stored `defaultDailyMinutes`; no client-supplied budget or timezone
   in Phase 6. An explicit button creates a plan; a GET never does.
3. Unobserved state is effective mastery/confidence `0`, no due review, no active
   misconception, and no recent evaluated failure. Incompatible nonempty progress
   versions fail closed rather than being treated as unobserved.
4. Phase 6 supports only active, curated `LEARN`, `PRACTICE`, and `RECALL` variants.
   `DIAGNOSTIC`, `QUICK_VERIFY`, `REMEDIAL`, and evaluated tasks are ineligible until
   real compatible content/evaluation contracts exist. Due review may use `RECALL`;
   a weak-confidence concept may use an eligible practice task, but never claim an
   objective verification took place.
5. Versioned ROI prior: `LEARN=0.30`, `PRACTICE=0.35`, `RECALL=0.20`;
   `expectedMasteryGain = gap × typePrior × (6 - difficulty) / 5`,
   `unlockValue = 0.5 + 0.5 × prerequisiteValue`, and
   `normalizedEffort = max(estimatedMinutes / availableMinutes, 0.1)`.
   `learningROI` uses the specified formula and clamps to `[0,1]`. These are ranking
   priors, not measured mastery gains. `REVIEW_MAX_DAYS=30`; high-severity
   misconception means severity `>=0.8` and bonus is
   `15 × severity × confidence`; overdue review `>14` days adds `10`.
   All decimal arithmetic uses scale 4/HALF_UP, scores scale 2, and the exact
   published `<0.01` tie rule. Any change requires a new policy version.
6. For sparse content, a valid 1-item plan is preferable to filler. A bounded
   project-authored bilingual root/prerequisite content pack should cover the four
   current graph roots with independently complete short variants (at most 20
   minutes), while leaving uncovered nodes visibly unavailable. This is not a claim
   of full 17-node curriculum coverage; no external copied content is introduced.
7. A learner-selected active session blocks new planner assignment. It is neither
   silently abandoned nor relabeled as Today. An active planner session is resumed.
   Explicit revision is allowed only if every old planner task is `ASSIGNED`; the
   Learning module atomically supersedes that session with an audit trail.
8. The visual roadmap is read-only and must have a semantic list/table fallback.
   No graph dependency is approved by this plan alone. Run the documented React
   Flow versus Cytoscape.js spike on the same fixture, record exact revision,
   licensing, accessibility, layout, performance, and bundle evidence, then obtain
   separate owner approval before adding either dependency. A dependency-free
   accessible SVG/HTML renderer is permissible if the spike supports it.

## Scope

### In scope

- Planner-owned deterministic candidate generation, prerequisite gate, signals,
  ranking, variant selection, explanations, alternatives, bounded snapshots,
  decisions, daily plan, and unstarted-only explicit revisions.
- Narrow immutable application contracts in Goal, Knowledge, Progress, Review, and
  Learning; planner commands validated by Learning, never repository cross-imports.
- Additive Flyway schema and curated bilingual short variants.
- Authenticated Today generation/read and roadmap read APIs; frontend Today and
  accessible visual/list roadmap, with English/Vietnamese presentation.
- Version/integrity, ownership, concurrency, retry, migration, architecture,
  accessibility, and deterministic replay tests.

### Out of scope

- Evidence-triggered automatic replan, task-end mini-assessment, time override,
  missed-day expiry, and partial/in-progress plan preservation (Phase 7).
- Goal completion transition or certification claim.
- AI-generated ranking/tasks, external resources, new curriculum family, learner
  graph editing, and a full 17-node content authoring program.
- New graph-rendering dependency before the spike and separate approval.

## Design

### P6.0 — Contract and policy lock

The resolutions and policy constants above are approved. Record fixed fixtures for a
fresh learner, a diagnostic learner, a due review, an all-blocked frontier, a
20-minute budget, and incompatible graph versions. Preserve the source-to-target
meaning of `PREREQUISITE`; only active edges with strength `>=0.8` gate targets.

Gate: owner approval recorded; no open authority conflict or undocumented formula.

### P6.1 — Snapshot contracts and schema

- Extend Goal's ownership-checked query with goal ID/template, target date,
  `defaultDailyMinutes`, timezone, status; retain its lock for serialized plan
  generation. Knowledge returns a bounded immutable published goal subgraph with
  node statuses, relevance, required mastery, terminal flags, active edges, stable
  topology, and graph version.
- Progress returns all requested goal-node projections at one explicit server
  `projectionAsOf`, with effective mastery, confidence, evidence count, status,
  policy version, graph compatibility, and a stable snapshot digest. Review returns
  due schedule state for the same bounded node set/time plus digest. Misconceptions
  return only active severity/confidence, not private answer content. A repeatable-read
  transaction or equivalent immutable snapshot must prevent a mixed-version plan.
  All dynamic reads accept the single captured `projectionAsOf`; no downstream
  service obtains a replacement instant from its own clock.
- Learning exposes active standalone template versions and mappings, bilingual
  immutable content, difficulty, duration, variant group, declared sequence metadata,
  recent evaluated variant failures (empty until supported), active session summary,
  and `assignPlannerPlan`/`supersedeUnstartedPlannerPlan` commands. Learning validates
  template status, graph, owner, source, duration, and session uniqueness; Planner
  chooses the IDs, not Learning's repositories. Planner cannot import another
  module's repository/entity/persistence/controller or perform a cross-module SQL
  join. The existing learner-selected start command must distinguish an active
  planner session and return a clear conflict rather than returning that session
  as though it matched the requested sequence.
- `V17` creates `planner_policies`, `planning_snapshots`, `planner_decisions`,
  `planner_decision_candidates`, `daily_plans`, `daily_plan_items`, and planner
  idempotency receipts. Persist canonical input/hash, `projectionAsOf`, graph,
  progress/review digests, policy, candidate counts/order, selected/blocked top
  candidates/signals/reasons, learning day/timezone/budget, status, revision,
  supersession, ordered task links, and timestamps. Unique current plan per
  user/goal/day, unique revision number, unique receipt key per user, and stable
  decision/task linkage enforce retry safety. Keep raw answers out of snapshots.
  Snapshot and decision payloads are append-only; a revision inserts new rows and
  an atomic supersession link, never overwriting the earlier ranking/explanation.
- `V18` adds nullable `learning_sessions.sequence_id` only for source `PLANNER` with
  a source/sequence CHECK and an indexed FK from `learning_tasks.planner_decision_id`
  to planner decisions. Existing learner-selected history stays unchanged; session
  reads and task commands must handle a planner session without a sequence ID.
- `V19` adds project-authored bilingual short variants for four graph roots using
  new immutable resource/template versions; no applied migration is edited.

Gate: clean V1–V19 and upgrade V16–V19 on MySQL 8.4, Hibernate/schema checks,
invalid-source/duplicate-current/foreign-key checks, and bounded query tests.

### P6.2 — Pure `planner-v1` policy

Implement pure Java policy with immutable input/output and injected clock value:

1. Generate candidates from unmet goal nodes, due reviews, active misconceptions,
   and blocking prerequisites. Remove archived/out-of-graph/no-active-template nodes.
   Deduplicate by `(node, reason)`, stable pre-limit order, then limit to 100.
2. Check hard prerequisites using **effective** mastery `>=0.75`, except an audited
   waiver (none created in Phase 6). A review of a prerequisite itself remains
   eligible. Never use `RELATED`/`PART_OF`/`APPLIED_IN` to lock.
3. Calculate seven normalized signals and the exact v1 weighted formula. Apply
   documented bonuses and clamp final score to `[0,100]`. In absence of a valid
   misconception or evaluated-failure source, those signals/overrides are zero.
4. Reject over-budget task variants without declared complete checkpoint; never
   truncate text. Rank by score and published tie-break; choose up to three distinct
   versions, reranking against remaining minutes without assuming state changes.
5. Preserve declared pedagogical sequence constraints; each standalone selected
   variant must be complete. Record top three alternatives and blocked IDs with
   reason codes. If no safe variant exists, return a typed no-plan outcome.

Fallback order is due review if all required nodes are estimated mastered, then
`GOAL_COMPLETION_CANDIDATE` (not a Goal mutation); closest eligible prerequisite
if the frontier is blocked; otherwise `NO_SAFE_RECOMMENDATION` with `NO_CONTENT`,
`NO_TIME_FIT_VARIANT`, or `INCOMPATIBLE_SNAPSHOT` as appropriate. No fake
micro-assessment.

Gate: fixed decimal/rounding, zero denominators, topology, gating, stable-limit,
tie, bonus, 20/30/60-minute, sparse-catalog, and replay unit fixtures.

### P6.3 — Transactional generation and guarded revision

`POST /api/v1/learning/today/generate` requires authentication, CSRF, and
`Idempotency-Key`; its body is empty. Resolve user from the principal, lock the owned
active goal, derive local learning day from server time and stored goal timezone,
and build one compatible snapshot. The same key/hash returns the saved outcome;
same key/different command conflicts. If a current plan exists, return it without
changing assignments. If a learner-selected session is active, return
`409 ACTIVE_LEARNING_SESSION` with a link to it. Persist snapshot, decisions,
plan/items, Learning planner session/tasks, and receipt in one transaction. A
concurrent request converges on the unique current plan and cannot double-assign.

`POST /api/v1/learning/today/revise` is an explicit, separately idempotent command.
It may supersede only a planner plan whose tasks are all `ASSIGNED`; Learning closes
the old session with recorded task transitions in the same transaction. If any task
started/completed or a learner-selected session is active, return a documented 409
and preserve all work. The new revision links to the old immutable revision and
becomes current atomically; neither old snapshot nor old decision is rewritten.
Phase 7 will extend revision policy for partial plans.

Gate: same-key replay, key mismatch, concurrent generation/revision, transaction
rollback, manual-session conflict, started-task guard, and exact row counts.

### P6.4 — Today read/API/UI

`GET /api/v1/learning/today` is a side-effect-free owner-scoped read. Return the
current local-day plan (or explicit `NOT_GENERATED`/no-safe state), ordered tasks,
remaining minutes, human-readable reasons and alternatives, version stamps, and
links to the existing Learning session/task flow. The active manual session is shown
separately and never passed off as a recommendation. POST returns `201` for a new
plan and `200` for replay/existing plan or a safe no-plan outcome. No client field
may set score, state, task authority, goal ID, or planner decision.

Add a Today route/card in the existing learner shell, bilingual labels, explicit
Generate/Revise actions, loading/empty/conflict/no-safe/expired-auth/error/success
states, reason explanations, budget, and accessible task order. Reuse the existing
Learning execution UI rather than duplicating task lifecycle logic. Preserve input
and retry key after an ambiguous command result; refetch on conflict.

Gate: OpenAPI/types synchronized; auth 401, CSRF 403, ownership concealment,
parameter bounds, response secrecy, no GET writes, and frontend interaction tests.

### P6.5 — Version-consistent roadmap and visualization spike

`GET /api/v1/roadmap` returns a bounded published goal neighborhood with
`graphVersionId`, `knowledgeStatePolicyVersion`, progress/review snapshot digests,
planner policy/plan/revision, `projectionAsOf`, nodes/edges, cursor, mutually
exclusive `knowledgeStatus`, and independent `current`/`ready`/`blocked` overlays.
Each blocked node includes prerequisite IDs/reasons. With an active plan, the map
uses that plan's pinned snapshot for its plan overlays and shows a stale badge if a
fresh state/review/graph digest differs; without a plan it uses one fresh snapshot.
The browser must not merge independently fetched versions or label a stale plan
current.
No browser endpoint changes mastery or prerequisite status.

Use the same 17-node and a larger bounded fixture to spike React Flow and
Cytoscape.js as specified in `OPEN_SOURCE_ADOPTION.md`. Compare keyboard navigation,
screen-reader list equivalence, mobile layout, stable placement, progressive
disclosure, rendering time, bundle size, and maintainability. Record exact versions
and licenses, then request owner approval for any dependency. Ship a synchronized
semantic list/table alongside the visual diagram with accessible node/edge labels,
focus states, legends that do not rely on color, and empty/error/stale states.

Gate: version consistency, graph direction, blocked explanations, cursor/limit,
keyboard/semantic fallback, narrow viewport, and no cross-owner leakage.

### P6.6 — Audit, docs, handoff

Run backend/SQL/architecture/API/security, frontend, generated-type, Compose,
dependency/license, and secret checks. Audit imports, plan snapshot determinism,
idempotency, timezone/DST boundaries, catalog retirement, query bounds, and
partial-transaction rollback. Update domain/API/database/module/testing docs,
roadmap, `PROJECT_CONTEXT.md`, and this plan with exact validation results and
remaining Phase 7 work. Mark `DONE` only after applicable gates pass.

## Expected files

- New `backend/src/main/java/com/skillpath/planner/{api,application,domain,infrastructure/persistence}/**`
  and planner tests/architecture fixtures.
- Narrow contracts/implementations under `goal.application`,
  `knowledge.application`, `progress.application`, `review.application`, and
  `learning.application`; no cross-module persistence imports.
- `backend/src/main/resources/db/migration/V17__*.sql` through `V19__*.sql`;
  owner-approved Goal budget V20; MySQL clean/upgrade and Planner flow integration tests.
- `frontend/src/features/planner/**`, a roadmap feature, `frontend/src/app/App.tsx`,
  the Active Goal entry point, i18n strings, shared API client/types, and styles.
- OpenAPI/API contract; planner, adaptive-loop, Learning, graph, database,
  boundaries, testing, roadmap, and project-context docs. New dependencies or a
  schema/policy deviation beyond this plan require further owner review.

## Tests

- Pure policy: prerequisite direction/threshold, missing evidence, due review,
  misconception bounds, zero denominator, ROI/rounding, tie delta, stable
  pre-limit ordering, no repeat, sequence integrity, budget, identical replay,
  and one `projectionAsOf` propagated through every dynamic input.
- MySQL: V1–V19 clean and V16–V19 upgrade, schema checks, same-day uniqueness,
  active-session collision, FK/ownership, canonical receipt replay, atomic rollback,
  concurrent start/revise, immutable historical revision/snapshot payloads,
  graph-version incompatibility, and catalog retirement.
- API/security: 401/403, concealed cross-owner reads, GET purity, forged score/
  authority rejection, no raw evidence/answer leakage, stale snapshot behavior.
- Frontend: bilingual Today/map, generation and retry, manual-session conflict,
  no-safe/stale states, route integration, accessible graph/list equivalence, narrow
  viewport, and no self-report-to-mastery claim.
- Regression commands: backend Maven wrapper `clean verify`, frontend API type
  generation/check, format, lint, Vitest, build, `docker compose config --quiet`,
  and `scripts/audit.ps1`; record actual exits and warnings.

## Acceptance criteria

1. Given the same compatible goal/graph/state/review/catalog snapshot and policy,
   two runs return identical ordered decisions, scores, reasons, and alternatives.
2. Given a hard unmet prerequisite, no task on its dependent is recommended;
   an eligible prerequisite task may be chosen with an explanation.
3. Given 20 available minutes, no 45-minute task is assigned. A safe independent
   short variant may yield a one-item plan; no fitting variant yields an explicit
   no-safe outcome rather than invented content.
4. Given an authenticated learner explicitly generates Today, exactly one current
   local-day plan with 1–3 distinct, time-fit tasks and one active planner session is
   committed; retries/concurrent requests do not duplicate rows.
5. Given an active learner-selected session, generation never changes or disguises
   it; given started planner work, explicit revision never silently replaces it.
6. Given self-reported task completion, Progress/Review scores do not change and no
   new plan is automatically produced in Phase 6.
7. Given incompatible graph/state versions, planner fails closed and map reports
   staleness; it never combines them into a current decision.
8. Given the learner opens the roadmap, visible `current`/`ready`/`blocked` overlays
   and reasons derive from the same stamped snapshot as Today, with an equivalent
   keyboard-accessible semantic representation.
9. Given all required nodes appear mastered, Planner does not complete Goal; it
   returns only a completion-candidate signal for the future Goal policy.
10. Existing Phase 1–5 behavior and migrations remain green.
11. Given an explicit revision of an unstarted plan, the previous snapshot,
    decisions, reasons, and ordered items remain readable and unchanged; a new
    revision supersedes them atomically and uses exactly one new `projectionAsOf`.
12. Architecture checks prove Planner touches other modules only through public
    application contracts, never through their persistence types or SQL joins.

## Rollout and rollback

- Deploy additive V17–V19 before exposing Today/map routes. Preserve V1–V16 and
  existing sessions/history. Gate the new UI on API availability; no half-working
  Today claim. A no-safe outcome is expected for uncovered nodes.
- Deploy V20 before accepting new 20-minute Goals; older Goals remain unchanged.
- Existing learner-selected sessions are neither backfilled nor converted. New
  planner sessions use source `PLANNER` and pinned graph/template/policy versions.
- Rollback application code leaves additive planner tables inert; database repair is
  restore/forward-fix, never rewriting applied migrations. A later graph version
  needs explicit compatibility/migration work before planner assignment resumes.

## Risks and open decisions

- **20-minute Goal budget approved on 2026-09-23.** The 20-minute planner fixture
  can also represent the remaining budget inside a 30-minute day; Goal did not need
  to change for that fixture. The owner separately approved widening a learner's
  stored default budget from 30–180 to 20–180 minutes. V20 replaces the CHECK,
  and Goal API/domain/frontend validation follows `goal-daily-budget-v2` in
  ADR-0007. Existing goals and plans remain unchanged; Today still has no
  client-supplied budget override.
- The owner approved the conflict resolutions, ROI priors/bonus thresholds,
  root content-pack scope, manual-session rule, and unstarted-only revision limit
  on 2026-09-23. A graph-rendering dependency still requires separate approval.
- Four-root seed coverage does not enable the full Java Backend curriculum. Track
  uncovered-node and no-safe rates; author broader vetted content in later work.
- Phase 6 has no evaluated task-variant failures or active misconception severity
  query wired into planner inputs. Those two signals remain explicitly unobserved/zero;
  self-reported task outcomes are never substituted for either signal.
- `GOAL_COMPLETION_CANDIDATE` is not a completion policy. Phase 7/Goal must supply
  final confidence/application/misconception checks and task-derived evidence.
- The [visual-map spike](../research/PHASE_6_VISUAL_MAP_SPIKE.md) compared React Flow
  and Cytoscape.js on the same 50-node fixture. Phase 6 retains the dependency-free
  SVG/semantic-list renderer; adding either library still requires separate approval.
  Real-device and assistive-technology usability remain pre-public-launch checks.
- MySQL snapshot/revision integrity is verified by Phase 6 integration tests;
  production-scale latency is not benchmarked. Progress evidence is node-keyed
  today; do not merge graph versions without explicit migration design.
- Curated educational content requires pedagogical review before effectiveness
  claims. Flyway/MySQL compatibility warning and production data-retention policy
  remain open from earlier phases.

## Validation results

Implementation validation completed for the approved Phase 6 slice (2026-09-23):

- Full backend clean `verify` passed before the final Phase 6 precision/concurrency
  refinements: 30 unit/architecture tests and 23 MySQL integration tests, including
  clean V1–V19 and V16–V19 upgrade. A final clean targeted `verify` after those
  refinements passed 12 policy/architecture tests and 4 Phase 6 flow/migration
  tests (3 flow, 1 upgrade).
- After the owner-approved V20 Goal-budget change, full backend clean `verify`
  passed: 31 unit/architecture tests and 27 MySQL integration tests, including
  clean V1–V20, V19→V20 with an existing Goal, and the historical upgrade paths.
  Flyway still warns that this version has not declared MySQL 8.4 supported;
  test-container Hikari pools also logged closed connections as their containers
  stopped, without failing the build.
- The first targeted rerun exposed the existing Goal 30-minute minimum in a newly
  added 20-minute API fixture; a second found an unscoped test row-count assertion.
  Both test fixtures were corrected without changing product policy. The pure
  planner policy continues to test 20 minutes; HTTP flow tests use 30 minutes.
  The owner later approved widening Goal to 20 minutes separately. A targeted
  clean backend `verify` passed the Goal V19→V20 migration, 20-minute Today API
  flow, 19-minute API rejection, and Phase 6 regression tests.
- The pinned Today/roadmap timestamp assertion exposed Java nanosecond versus MySQL
  `DATETIME(6)` precision. Captured server time is now normalized to microseconds
  before snapshot calculation/persistence; the final MySQL assertion passes.
- Frontend OpenAPI generation, format, lint, 6 files/17 tests, and production build
  passed after the 20-minute Goal UI change. `docker compose config --quiet` passed with
  the pre-existing Docker config permission warning. `git diff --check` passed.
- The owner explicitly approved sending dependency names/versions to
  `https://registry.npmjs.org`. `scripts/audit.ps1` then exited 0;
  `npm audit --audit-level=high` reported 0 vulnerabilities and `git diff --check`
  passed. The earlier offline/network-approval failures did not indicate a
  vulnerability result.
- A full backend wrapper `clean verify` exited 0: 35 unit/architecture tests and
  30 MySQL integration tests. This includes clean V1–V20, historical upgrade paths,
  50/200-node roadmap paging, pinned/stale snapshot checks, concurrent revision,
  and a forced insert-failure rollback fixture that leaves no partial planner rows.
  Two subsequent policy regression fixtures verify that a mastered prerequisite is
  not an unlock candidate and that an empty catalog yields `NO_CONTENT`. The final
  full wrapper `clean verify` after these refinements exited 0: 37 unit/architecture
  and 30 MySQL integration tests, with no failures, errors, or skips. Flyway still warns
  that its version has not declared MySQL 8.4 support; Mockito
  reports a future-JDK dynamic-agent warning. Neither failed the build.
- OpenAPI TypeScript generation, frontend format, lint, 6 files/21 tests,
  production build, Compose configuration, `scripts/audit.ps1`, and `git diff --check`
  exited 0. Audit reported 0 npm vulnerabilities. Compose retained the pre-existing
  Docker config-file permission warning.
- The [visual-map spike](../research/PHASE_6_VISUAL_MAP_SPIKE.md) measured both
  libraries with the same 50-node/49-edge fixture and recorded exact versions,
  licensing, bundle size, render samples, accessibility and integration tradeoffs.
  No runtime dependency was added. Headless Chromium checked the built SVG/list UI
  at its effective 512 px narrow viewport; real-device/assistive-technology checks
  remain a launch risk, not a Phase 6 claim.

## Documentation updates

The planner fallback and Phase 6/7 revision boundary, API/OpenAPI, schema,
module boundaries, test strategy, roadmap, visual-map research, and
`PROJECT_CONTEXT.md` are synchronized with the implemented slice. Phase 7 still
owns evidence-driven replanning, partial-plan preservation, missed days, and
task-derived evaluation.
