# Plan: P7 — Evaluated Task Evidence and Adaptive Replanning

## Status

`DONE — P7.6 full regression, gate enablement, and documentation completed on 2026-09-23`

## Objective and user story

Close one reproducible learner loop: a learner completes a curated practice task and
its objective check; Assessment records what the check demonstrated; Progress and
Review process the evidence once; Planner revises Today from a compatible snapshot;
Learning preserves work already completed or in progress. A learner can also change
today's available minutes or return after a missed day without losing history or
being assigned more work than fits. No checkbox or self-report becomes mastery.

This is an evidence-driven MVP slice, not AI evaluation, proof of practical
application, full-curriculum coverage, or automatic Goal completion.

## Authoritative references

- `AGENTS.md`, `DEVELOPMENT_RULES.md`, `PROJECT_CONTEXT.md`
- ADRs 0001, 0002, 0003, 0004, 0006, 0007 in `docs/adr/`
- `docs/architecture/{SYSTEM_ARCHITECTURE,MODULE_BOUNDARIES,DATABASE_DESIGN}.md`
- `docs/domain/{ADAPTIVE_LOOP,ASSESSMENT_MODEL,USER_KNOWLEDGE_STATE,LEARNING_TASK_MODEL,PLANNER_ALGORITHM_V1,KNOWLEDGE_GRAPH}.md`
- `docs/api/{API_CONTRACT.md,openapi-v1.yaml}`
- `docs/product/{MVP_SCOPE,USER_JOURNEYS}.md`
- `docs/development/TESTING_STRATEGY.md`
- `docs/plans/{PHASE_ROADMAP,PHASE_5_LEARNING_SYSTEM,PHASE_6_PLANNER,PLAN_TEMPLATE}.md`

## Current-state evidence

- `PHASE_6_PLANNER.md` and the roadmap mark the **approved P6 slice `DONE`** on
  2026-09-23. Its recorded final backend wrapper `clean verify` passed 37
  unit/architecture and 30 MySQL integration tests; frontend generation, format,
  lint, 21 tests, build, Compose config, and audit passed. P6 was uncommitted at
  discovery and was subsequently committed/pushed as `6865011` before P7 coding.
  These recorded results are not
  a fresh Phase 7 backend run or proof that a running Docker bundle is current.
- This discovery reran `npm.cmd --prefix frontend run lint` (exit 0),
  `npm.cmd --prefix frontend run test -- --run` (exit 0; 6 files/21 tests), and
  `git diff --check` (exit 0) before this document was added. After adding the
  draft, Prettier checked this file (exit 0), and `docker compose config --quiet`
  exited 0 with the existing local Docker-config permission warning. Full backend
  and migration suites were identified, not rerun for design-only work.
- V1–V20 provide a one-baseline objective diagnostic, append-only evidence,
  `knowledge-state-v1`, Review schedules, leased outbox dispatch, Learning task
  history, and explicit `planner-v1` Today/revisions. P6 has one captured
  `projectionAsOf`, immutable snapshots, a 20–180 minute stored Goal budget, and
  no client-supplied daily override. P6 revision is allowed only when **all**
  planner tasks remain `ASSIGNED`.
- `LearningService.command` completes a task from checklist and minutes only;
  catalog `evaluation_mode` is constrained to `NONE`/`SELF_REPORT` by V14 and
  active planner variants filter to those modes. No task check, evaluated failure
  history, or task-derived evidence exists. Existing curated practice/recall steps
  are engagement, not assessment.
- `AssessmentService` starts one diagnostic baseline and scores only objective
  single/multiple choice. Its v1 policy permits only `RECOGNITION`/`UNDERSTANDING`
  evidence. Repeating the diagnostic cannot serve as a task check.
- Progress appends accepted Assessment evidence idempotently, then emits
  `KnowledgeStateChanged` only if stored mastery or acquisition status changed.
  Review consumes that event. This is narrower than the P7 requirement to consider
  every newly accepted evidence/check and the resulting due-state change.
- `KnowledgeStateChangedHandler` currently writes `user_knowledge.next_review_at`
  directly from Review's adapter. `user_knowledge` belongs to Progress; the P7
  orchestration must not add cross-module SQL and must resolve this existing
  ownership breach through a public contract or a read-time composition change.
- V17's `daily_plan_items` has unique `learning_task_id`/`decision_id`, and V16
  allows one active Learning session per goal. A partial revision cannot simply
  insert old task IDs into a new plan or create a second active session. A forward
  migration and Learning-owned append/expiry contract are required.
- The previously reported `localhost:5173` Phase 5-only screen was an older
  Docker-served frontend bundle, while the current source contains `/today` and
  `/roadmap`. Deployment freshness must be checked before P7 browser acceptance.

### Context conflicts resolved by owner approval and architecture lock

1. **CONTEXT CONFLICT — practical evidence and Goal completion.** MVP scope and
   Journeys 2/6 describe recall/application checks and eventually a completed
   Goal, but the approved objective evaluator cannot emit `RECALL` or
   `APPLICATION`; current tasks are self-report. Proposed P7: a new curated
   objective task check produces only `RECOGNITION`/`UNDERSTANDING` evidence,
   proving evidence → state → changed plan without claiming practical skill.
   The owner approved this bounded evidence slice. Goal completion remains a separate versioned Goal policy after reliable
   application checks exist. The roadmap's “MVP completion scenario” means the
   adaptive-loop scenario in `MVP_SCOPE.md`, not a `COMPLETED` Goal status.
2. **CONTEXT CONFLICT — micro-assessment fallback.** Journey 3 offers a short
   micro-assessment, but P6 explicitly rejected a fabricated one. P7 uses a
   compatible, versioned task check only where authored content exists; otherwise
   it returns `NO_SAFE_RECOMMENDATION`/`NO_TIME_FIT_VARIANT`, with no reused
   diagnostic baseline or synthetic assessment.
3. **CONTEXT CONFLICT — event and projection ownership.** Current Progress event
   gating and Review's direct Progress-table update conflict with the documented
   evidence → review → replan chain and module boundary. Resolution: Review stops
   writing `user_knowledge`; Progress composes `nextReviewAt` through a bounded
   Review application query on read. New Assessment events use a projected-attempt
   barrier before `EvidenceAccepted`; Review alone emits `PlanningInputsReady`
   after its own schedule transaction. The detailed compatibility lock follows.
4. **CONTEXT CONFLICT — partial-plan schema.** P6 immutable history and unique
   plan-item links prohibit copying an in-progress task into another revision.
   P7 must represent carry-forward separately and keep one Learning session per
   goal; it may not relax uniqueness by editing V17.

## Owner-approved decisions and architecture locks

The owner approved the bounded policies below on 2026-09-23. Any different answer
changes schema, API, tests, and possibly requires an ADR before implementation.

1. P7's first evaluated task uses project-authored bilingual objective questions
   mapped to one compatible task-template **version** and published graph version.
   A task check is not the Phase 3 diagnostic and cannot alter its one-baseline
   rule. Use the deterministic `assessment-objective-v1` scoring/evidence bounds;
   assign a distinct `task-check-objective-v1` evaluator/attempt policy for
   provenance. No free text, code execution, AI, or `APPLICATION`/`RECALL` evidence.
2. An evaluated task is completed through one authorized, idempotent task-check
   command that atomically stores the attempt, evidence/outbox handoff, and
   Learning engagement transition via a narrow Learning application contract.
   Wrong answers still complete the work but may lower the relevant estimate.
   A learner who leaves before submission resumes the pinned task/check. A
   self-report task remains evidence-free.
3. New accepted evidence produces a versioned durable projection event even when
   the numeric mastery/status is unchanged. Review processes that source exactly
   once, then emits a durable `PlanningInputsReady` event. Planner consumes that
   event only after Review's transaction commits; unknown event types are never
   deployed before their allowlisted consumers. Existing pending v1 events are
   handled compatibly and do not create duplicate schedules/replans.
4. Automatic replan is a durable, idempotent request, not a browser calculation.
   Trigger identity is `(owner module, event type/version, event ID)` or an
   equivalent stable command key. Coalesce multiple node events from one task
   check into one planning request using the attempt ID; serialize by owned goal.
   A retry cannot create another current revision or task assignment.
5. `adaptive-replan-v1` keeps completed tasks as historical outcomes, retains
   `IN_PROGRESS`/`BLOCKED` tasks without silently abandoning them, and expires
   only unstarted planner tasks selected for replacement. Carry-forward references
   old immutable decisions/task IDs; newly selected tasks receive new decisions
   in a new immutable revision. A blocked task may need explicit learner action
   before new work can be assigned. Manual sessions are never superseded.
6. For budget accounting, completed work consumes **reported actual minutes**
   bounded by the command; an in-progress task reserves its full estimated
   minutes because elapsed time is not currently tracked. Remaining available
   minutes is `max(0, todayBudget - completedActual - inProgressReserved)`.
   A 0-minute report remains possible but is visibly recorded, not treated as
   proof of zero effort. If the override is below already spent/reserved time,
   preserve work and assign nothing else; show an over-budget explanation.
7. Today's one-day override is an integer `1–180` minutes, scoped to the owned
   goal and goal-local `LocalDate`; it does not mutate the stored Goal budget.
   The latest explicit override replaces an earlier override with an audit row
   and its own idempotency receipt. Replan is requested after the override
   commits. The default returns on the next local day.
8. At a missed-day boundary, expire only prior-day `ASSIGNED` planner tasks,
   never reduce knowledge state, and never copy them forward mechanically.
   Retain active in-progress work across the boundary and reserve its time. A
   side-effect-free Today GET may report `REFRESH_REQUIRED`; an authenticated,
   CSRF-protected refresh command (called by the UI on return) creates the
   idempotent next-day plan. Review-due detection follows the same bounded
   refresh/scheduler contract, not a write hidden in GET.
9. Two distinct evaluated failures (score `<0.60`) for the same variant group
   and node make the identical version ineligible when a compatible alternate
   exists. This changes eligibility, so publish `planner-v2`; keep P6
   `planner-v1` snapshots replayable. Without an alternate, expose the repeated
   attempt and a no-safe/curation reason rather than inventing remediation.
10. No Goal-completion transition, mastery certification, or AI-generated
    ranking is included in P7. A separate Goal policy/ADR and application-level
    evidence content are prerequisites. This is an explicit change to the
    broader Journey 6 expectation, not a silent claim that it is done.

### Hard lock A — Review owns the schedule; Progress composes its read

- `review_schedules` is the only authority for `dueAt`/interval transitions.
  Review's handler must **stop** issuing `UPDATE user_knowledge`. The existing
  nullable `user_knowledge.next_review_at` column is a legacy cache: retain it
  for forward compatibility, do not write it from Review, and do not use it as
  the API/Planner source. No applied migration is edited or column dropped.
- Add a bounded, owner-scoped `review.application.ReviewScheduleQueries` contract
  returning immutable `(graphVersionId, nodeId, dueAt, status, version)` rows
  for requested nodes at an explicit `asOf`. Progress presentation composes its
  state rows with that contract and exposes the same `nextReviewAt` API field;
  absent schedule remains `null`. Capture one instant for both reads. Planner
  continues to consume the separate Progress/Review contracts with its one
  `projectionAsOf`; it never reads the legacy column.
- Review does not import Progress application/persistence to perform this fix.
  Progress may depend on Review's **public application interface** for the
  display projection; Review has no reverse code dependency. Existing stored
  `next_review_at` values remain historical but non-authoritative. The existing
  `knowledge-state-v1` calculation is not silently changed; any altered
  `REVIEW_DUE` status semantics require a separately versioned policy.

### Hard lock B — attempt-level evidence barrier and ordered handoff

```mermaid
sequenceDiagram
    participant A as Assessment
    participant P as Progress
    participant R as Review
    participant PL as Planner
    A->>P: AssessmentEvidenceCreated v2 × N (attemptKind, attemptId, expectedCount)
    P->>P: Append/project each source once; count N by attempt kind and ID
    P->>R: EvidenceAccepted v1 × 1 after all N projections
    R->>R: Process schedule/receipt once, even if state unchanged
    R->>PL: PlanningInputsReady v1 × 1 after Review commit
    PL->>PL: Unique replan request; lock Goal and capture projectionAsOf
```

- Assessment still commits each evidence row and its outbox event with the
  attempt. New v2 payloads contain `attemptKind`, `attemptId`, the bounded expected evidence
  count, goal/user/graph/version IDs, and the accepted evidence summary—never
  answer content or keys. Progress owns an attempt-level projection receipt;
  each source row is deduplicated by existing ledger keys. Only the transaction
  that observes all expected IDs projected inserts the uniquely keyed
  `EvidenceAccepted` event keyed by `(attemptKind, attemptId)`. Out-of-order
  delivery is safe because each committed source increments the receipt once;
  a terminally failed sibling leaves a visible incomplete batch, not a partial
  replan.
- `EvidenceAccepted` is a Progress fact about the **whole attempt**, not a
  second mastery authority. Its bounded payload carries each node's projected
  status and validated evidence metadata needed by Review, plus causation,
  graph/policy versions, and whether this attempt may trigger replan. Review
  validates the tuple, records a unique source receipt, and changes only its
  own schedule/attempt tables. In the **same Review transaction**, it inserts
  one `PlanningInputsReady` outbox event keyed by `(attemptKind, attemptId)`
  when eligible.
  An unchanged rounded mastery still gets the receipt and ready event.
- For new diagnostic attempts after P7 rollout, Assessment uses v2 so Review sees each
  accepted attempt; `replanEligible` is true only when the pinned diagnostic
  completes. Task checks are eligible immediately. This prevents eight
  interim diagnostic replans. Legacy `AssessmentEvidenceCreated` v1 and
  already-pending `KnowledgeStateChanged` v1 remain on their existing allowlisted
  handlers; the latter may create a Review schedule but never writes Progress
  or emits a duplicate P7 ready event. No v1 row is retroactively reinterpreted
  as a complete attempt. A later explicit refresh can reconcile an older plan.
- Planner consumes only `PlanningInputsReady`, records a unique attempt/trigger
  receipt keyed by `(attemptKind, attemptId)`, then processes the replan request
  after Review commits. The Review
  handler and dispatcher acknowledgement are atomic; Planner failures retry
  without rolling back the accepted attempt or Review schedule. A single
  attempt can create at most one ready event and one active plan revision.

## Scope

### In scope

- One task-specific objective check, deterministic scoring, validated concept
  evidence, provenance, idempotent attempt, and bilingual learner presentation.
- Evidence → Progress → Review → durable replan handoff with ordered, allowlisted
  outbox consumption, retry/backoff, and recovery visibility.
- Partial plan revisions and same-day budget overrides with immutable history,
  preserved work, owner-scoped Today/roadmap reads, and one snapshot instant.
- Missed-day expiry/refresh and due-review trigger for active goals.
- Versioned evaluated-failure avoidance with curated alternative content for the
  narrow demonstration node; no broad 17-node content claim.
- API/OpenAPI/types, frontend states, MySQL migrations, security/architecture
  tests, end-to-end adaptive-loop regression, and documentation.

### Out of scope

- Open-response, code/test-runner, rubric, AI/human evaluation, or falsely labelled
  recall/application evidence; Phase 8 remains the AI evaluation phase.
- Automatic Goal completion and the full Journey 6 mastery check.
- Unreviewed external curriculum/resources or automatic generation of alternatives.
- Rewriting applied Flyway migrations, changing `planner-v1` historical scores, or
  treating self-report/elapsed time as knowledge evidence.
- A broker or new runtime dependency without a separate approved decision.

## Design

### P7.0 — Contract and authority lock

The owner approved the conflicts/policies above, and the two hard locks document
the architecture required before coding. Fix named stable fixtures for: a task
check with unchanged rounded mastery, a failed check,
two failures with/without an alternate, partially completed Today, in-progress
task, manual session, 90→30 minute override, and missed day at the HCM boundary.
Keep the P6 locks: one `projectionAsOf` per new snapshot; immutable superseding
revisions; public application contracts only.

Gate: owner approval recorded, explicit evidence dimension and Goal-completion
boundary, one-way Progress → Review application query, and one attempt-level
Review-before-Planner event path; no unresolved authority conflict.

### P7.1 — Forward schema, content, and contracts

- Add forward migrations (tentatively V21–V23): Assessment-owned task-check
  definitions/attempts/unique task submission, pinned question versions and
  graph mappings; allow `OBJECTIVE` on new Learning template versions; add
  planner trigger receipts/queue, local-day overrides/audit, and carry-forward
  links without dropping V17 uniqueness; seed only project-authored bilingual
  check and alternate variants. Preserve old template versions and sessions.
- Assessment reads a Learning-owned immutable task/evaluation view and asks
  Learning to complete an evaluated task through a validated application
  command. Learning never accepts client-supplied score/evidence. Progress,
  Review, Planner, and Goal interact only through public application contracts
  or versioned events, never each other's entities/repositories/SQL tables.
- Remove Review's direct `user_knowledge` write and compose `nextReviewAt` at
  read time through `ReviewScheduleQueries` as locked above. Add a Progress-owned
  attempt projection receipt and Review-owned processed-source receipt with
  unique keys; retain the legacy nullable column without using it as authority.
- Persist source/causation/correlation IDs, event version, pinned graph/question/
  task/policy versions, observed server time, and opaque user/goal references.
  No raw answer or answer key enters outbox, planner snapshots, logs, or UI.

Gate: clean V1→latest and V20→latest MySQL 8.4, Hibernate validation, old history
readability, owner/graph FKs, CHECK/unique constraints, and no module-boundary
regression.

### P7.2 — Deterministic task check and evidence handoff

Expose a pinned owner-scoped task-check read and a `POST` submit command. Answer
option IDs are stable across English/Vietnamese; answer keys stay server-side.
Normalize/hash the payload, validate offered IDs and task state, score with the
approved objective policy, and cap evidence reliability before committing the
attempt, Learning transition, evidence rows, and one outbox event per evidence in
one transaction. Same key/input replays; key mismatch or second task submission
conflicts; concurrent submissions have one winner. Wrong/partial answers are
observations, not authority to change mastery directly.

Gate: scoring/mapping fixtures, privacy and ownership checks, concurrent replay,
rollback, exact attempt/evidence/event/task-transition counts.

### P7.3 — Ordered projection, Review, and replan trigger

Introduce the exact allowlisted v2 Assessment → Progress attempt barrier →
`EvidenceAccepted` v1 → Review receipt/schedule → `PlanningInputsReady` v1 →
Planner request path above, including no-rounded-mastery-change. Planner only
reads after Review commits. The Planner handler records a unique replan request,
acknowledges the event, and retries the request separately with bounded
backoff/dead-letter visibility. Multiple evidence rows from one check coalesce
by `attemptId`. Keep existing v1 pending rows compatible; do not double-advance
a review interval or emit a ready event for incomplete evidence batches.

Gate: two-node event reordering/replay/poison fixtures, Review-before-Plan
assertion, unchanged-score trigger, legacy v1 pending-event replay, failure
after task commit, eventual recovery, no duplicate current plan/revision,
and no cross-owner data exposure.

### P7.4 — Partial revision and budget policy

Implement pure `adaptive-replan-v1` preservation/budget rules and versioned
`planner-v2` failure eligibility without changing P6 history. Learning supplies
an immutable task-state snapshot and validates expiry/append commands in the
existing planner session. Planner creates a new snapshot/decision/revision and
carry-forward links in one serialized goal transaction. A stale task status or
different graph/policy version causes a safe retry/conflict, never a mixed plan.
The new revision displays completed work as history, retains in-progress work,
and recommends at most the number/time of remaining safe items. Historical
decisions/reasons stay unchanged.

Gate: 20/30/60/90-minute fixtures, concurrent trigger/override, in-progress and
blocked preservation, manual-session conflict, immutable historical reads,
budget accounting, different-local-day behavior, and no double assignment.

### P7.5 — Missed day, API, and learner UX

Proposed additions (final names locked in OpenAPI at implementation):

```http
GET  /api/v1/learning/tasks/{taskId}/check
POST /api/v1/learning/tasks/{taskId}/check/attempts
PUT  /api/v1/learning/today/available-minutes
POST /api/v1/learning/today/refresh
GET  /api/v1/learning/today/replan-status
```

All writes require authenticated session, CSRF, and `Idempotency-Key`; identity,
goal, evidence, score, and planner decision are server-derived. `GET` is pure.
Owner-scoped resource IDs are concealed as `404`; stale task/plan is `409`, invalid
answer is `422`, expired/incompatible graph is explicit, and pending/retrying
replan is represented honestly rather than shown as an updated current plan.
Today and roadmap return matching plan/snapshot stamps and a stale/pending badge.
UI shows the task check, answer validation, stable retry key, result as
“diagnostic evidence”, completed/in-progress carry-forward, override control,
missed-day refresh, and failure recovery in both languages. It must not display
correct answers before submission or label self-report as mastery.

Gate: generated TypeScript contract, frontend accessible question/override flows,
GET purity, stale/retry/error/empty states, and local Compose browser smoke using
the **newly built** frontend image/bundle rather than the old 5173 asset.

### P7.6 — End-to-end audit and handoff

Demonstrate register → Goal → diagnostic → Today → curated task check → one
evidence projection/Review update → changed, reasoned Today/roadmap revision;
then repeat the submit/trigger, reduce today's time, and return after a missed
day. Audit authorization, event payloads, idempotency, outbox backlog, SQL/module
ownership, snapshot stamps, timezone/DST, and historical replay. Document
uncovered application/Goal-completion work for Phase 8 or an explicit later phase.

Gate: all applicable backend, frontend, migration, Compose, audit, and API/security
checks pass; mark this plan `DONE` only after the approved scope is implemented.

## Expected files

- Forward `backend/src/main/resources/db/migration/V21__*.sql` onward; never
  modify V1–V20.
- Assessment task-check API/application/domain/persistence; narrow
  `learning.application` evaluated-task contract and owned store changes.
- Progress/Review versioned handlers and contracts; shared outbox registration
  mechanics only; planner policy/orchestration/store/API and Goal query lock.
- Unit, ArchUnit, MySQL clean/upgrade, API/security, concurrency/outbox, and
  adaptive-loop integration tests under `backend/src/test/java/com/skillpath/**`.
- Frontend Learning check and Today/roadmap UX/tests, shared API client,
  generated `schema.d.ts`, i18n, and route/style changes.
- OpenAPI/API, assessment/learning/progress/review/planner/adaptive-loop,
  database/module/testing/product/roadmap docs, `PROJECT_CONTEXT.md`, and this plan.

Any new dependency, external evaluator, practical-skill claim, Goal transition,
or destructive/backfill migration is a material deviation requiring owner review.

## Tests

- Unit: exact/wrong/partial objective check, mapping bounds, no recall/application
  evidence, failure threshold/alternative, budget reservation, midnight/timezone,
  deterministic snapshots and replay.
- MySQL: clean and V20-upgrade path; old diagnostic/Learning/Planner history;
  uniqueness, FKs, outbox order, Review-once, trigger coalescing, partial revision
  atomicity, rollback, concurrent submissions/refreshes, and failure recovery.
  A deliberately stale legacy `user_knowledge.next_review_at` must not affect
  the API's derived `nextReviewAt`; an old pending v1 event must not double
  schedule or create a P7 replan.
- API/security: 401/403/404 concealment, CSRF, forged score/user/graph rejection,
  same-key replay and mismatch, stale task, answer-key secrecy, no GET writes.
  Architecture/source checks forbid Review SQL writes to `user_knowledge` and
  Planner cross-module persistence imports/joins.
- Frontend/E2E: bilingual/accessible check, retry without duplicate, pending
  replan, preserved in-progress work, 90→30 override, missed day, no-safe state,
  roadmap stamp consistency, freshly rebuilt Docker bundle.
- Baseline/regression: `backend/mvnw -f backend/pom.xml clean verify`, frontend
  API generation/check, format/lint/Vitest/build, `docker compose config --quiet`,
  `scripts/audit.ps1`, and `git diff --check`; record exact exits and warnings.

## Acceptance criteria

1. Given a pinned evaluated task, submitting the same valid response/key twice
   creates one Learning completion, one attempt, mapped evidence, and one event
   per evidence; a changed payload or different key for that task conflicts.
2. Given a wrong objective response, evidence may lower the estimate and cause a
   different decision, but it cannot create `RECALL`/`APPLICATION` evidence or
   complete a Goal. Self-report alone creates no evidence.
3. Given accepted evidence whose rounded mastery/status stays unchanged, Review
   still sees the accepted source once and Planner receives one ready trigger
   after Review commits.
4. Given a completed and an in-progress task, replan preserves their identity,
   status, and audit; only unstarted work is replaced, within the remaining
   budget. Failed replan leaves the accepted attempt and prior plan intact.
5. Given two evaluated failures and an approved alternate, the identical
   template version is avoided by `planner-v2`; without an alternate the system
   states the limitation and never invents a remedial item.
6. Given a 90→30 override, the Goal's stored minutes remain unchanged and a new
   immutable revision accounts for work already done/reserved. Duplicate and
   concurrent override/refresh requests converge on one current result.
7. Given a missed local day, old unstarted assignments expire once; in-progress
   work and knowledge estimates survive; a new achievable plan is computed from
   current Review/state, not copied from yesterday.
8. Given a diagnostic/evaluated-task chain, one reproducible end-to-end scenario
   shows evidence → state/review → changed Today and synchronized roadmap, with
   ownership, retry, and failure-recovery checks.

## Rollout and rollback

- Merge and deploy the locally complete P6 work before P7; verify the running
  backend schema and frontend bundle actually contain P6 routes. Do not expose
  objective task templates before new schema, handler allowlists, and consumer
  recovery are deployed. Deploy migrations/consumers first, then producer/content,
  then UI; use a feature gate for auto-replan during staged rollout.
- Existing self-report tasks and immutable P6 snapshots keep their meaning.
  Pending v1 outbox events remain processable. If P7 code is rolled back, disable
  new producers/auto-replan first and retain additive tables/history; repair via
  forward migration or restore, never edit applied Flyway files.

## Risks and open decisions

- The owner approved all four context-conflict resolutions, the objective-only
  evaluator and Goal-completion deferral, time-accounting/override bounds,
  missed-day explicit refresh, and `planner-v2` failure eligibility on
  2026-09-23. The ownership and event-chain hard locks above are normative.
- Objective checks demonstrate conceptual selection, not unaided recall or code
  application. Journey 6 and full MVP practical-skill claims remain unmet until
  separately validated content/evaluation exists.
- Existing Progress→Review v1 events and Review's cross-owner projection write
  need a compatibility-safe transition; an event-order shortcut could yield a
  plan from stale Review state. A stuck attempt batch needs visible recovery.
- Partial-plan revisions span immutable Planner decisions and one Learning
  session; historical read composition, uniqueness, and rollback need real MySQL
  concurrency tests.
- Seeded alternatives require pedagogical review; unanswered question retention,
  real-device accessibility, Flyway/MySQL 8.4 warning, and deployment freshness
  remain launch risks.

## Validation results

P7.6 final gate (2026-09-23): full offline backend `clean verify` exited 0
with 45 unit/architecture tests (13 classes) and 41 MySQL 8.4 integration
tests (18 classes), total 86 tests, 0 failures, in 08:08 minutes. Coverage
includes clean V1–V25 migrations, V20→V23→V25 historical upgrade paths,
PhaseSevenTaskCheckIT (ownership, CSRF, idempotent replay, scoring,
evidence/outbox), PhaseSevenEventChainIT (ordered evidence → Review → one
Planner request), PhaseSevenReplanWorkerIT (lease/retry/execution/terminal
failure), PhaseSevenPartialLearningIT (carry-forward, unstarted expiry,
override without Goal mutation, missed-day refresh). Frontend lint (exit 0),
Vitest (7 files/24 tests, exit 0), and production build passed. Compose
config exited 0. Both rollout gates (`skillpath.phase7.task-check-enabled`
and `skillpath.phase7.replan-worker-enabled`) are enabled by default in
`application.yml` and `compose.yaml`. A freshly rebuilt Compose stack with
the P7-enabled backend and current frontend bundle was deployed and verified
via browser smoke test. `scripts/audit.ps1` security stance and
`git diff --check` hygiene validated. Flyway still warns that MySQL 8.4 is
newer than the latest version it declares tested.

P7.4/7.5 checkpoint (2026-09-23): V25 applied cleanly and on the V20 upgrade
path in targeted MySQL 8.4 tests. A targeted backend `clean verify` passed four
Planner roadmap unit tests and five Phase 7 Learning/Planner integration tests:
Review-ready worker → immutable `planner-v2` revision, original snapshot
unchanged, in-progress carry-forward, unstarted expiry, audited/idempotent
60→30 minute override without Goal mutation, and explicit next-local-day
refresh. Frontend OpenAPI generation/build/lint and 7 files/23 Vitest tests
passed for pending status, explicit refresh, and carry-forward presentation.
The earlier targeted passes exposed a test-event string-format fixture and an
assertion that mistakenly treated a newly appended task as an old unstarted
task; both fixtures were corrected before the successful run. A full backend
regression is running separately and must not be claimed here yet. P7.6 delivery checkpoint (2026-09-23): Full backend `clean verify` passed (with `PhaseSevenReplanWorkerIT` test properties updated to accommodate the rollout gate enablement). The objective-task HTTP controller (`skillpath.phase7.task-check-enabled=true`) and replan background worker (`skillpath.phase7.replan-worker-enabled=true`) are enabled by default in `application.yml`. A freshly rebuilt Compose stack smoke test passed, and security audit returned 0 vulnerabilities. **P7 is DONE.**

P7.3 delivery checkpoint (2026-09-23): full offline backend `clean verify`
exited 0 with 45 unit/architecture and 37 MySQL integration tests. It covered
clean V1–V24, V23→V24 with an existing pending request, Review-ready duplicate
intake, transactional execution rollback, retry/backoff recovery, abandoned
lease reclaim, terminal failure after ten attempts, stale Goal ownership, and
two workers serialized on one Goal. `docker compose config --quiet` and
`git diff --check` exited 0. Compose retained its Docker-config permission
warning; Flyway still warns about undeclared MySQL 8.4 support, and Hikari
logged closed connections as test containers stopped. No P7.4 production
executor or end-to-end revised Today was tested or claimed. This turn did not
change frontend code; its prior 7-file/22-test validation is below.

Implementation checkpoint (not Phase 7 completion): clean V1–V23 migration and
out-of-order evidence → Review → one durable Planner request passed on MySQL 8.4.
The objective-task API/MySQL test passed ownership concealment, CSRF, hidden answer
key, self-report rejection, invalid option, atomic attempt/evidence/task completion,
same-key replay, and no knowledge update before outbox dispatch. A follow-up test
also traced a real task-check outbox through Progress, Review, and one Planner
request. `planner-v2` failure-eligibility and pure adaptive-budget fixtures passed.
The Learning-owned partial revision contract passed MySQL tests for preserving an
in-progress task, expiring only assigned tasks with audit, and rolling back an
invalid replacement. This contract is not yet connected to Planner. Frontend
OpenAPI type generation, build, lint, format, and 7 files/22 Vitest tests passed.
`scripts/audit.ps1` exited 0 with 0 reported vulnerabilities and Compose config
exited 0 with its existing Docker config permission warning. Full backend
`clean verify` passed 41 unit/architecture and 34 MySQL integration tests,
including clean V1–V23 and V20→V23 upgrades. A later targeted `verify` passed
15 unit/architecture and 4 P7 integration tests after the objective-task ordering
fix and appended-task test. The first full pass had exposed a stale V20-latest
assertion in a historical Goal upgrade fixture; that assertion was corrected
before the successful full run. Flyway still warns that MySQL 8.4 is newer than
the latest version it declares tested.

The objective-check HTTP controller is disabled by default through
`skillpath.phase7.task-check-enabled`; P6 still excludes `OBJECTIVE` templates
from its catalog. Do not enable the gate as a complete learner flow yet. The
P7.3 delivery now has V24 lease/retry/result metadata and a Planner-owned
request worker. It claims separately from the Review outbox, then locks the
owned Goal and invokes a `ReplanExecution` application contract in the same
transaction as acknowledgement. A failed execution rolls back before a bounded
retry; expired leases can be reclaimed and ten failed attempts leave a visible
terminal `FAILED` request. The worker is disabled by default with
`skillpath.phase7.replan-worker-enabled=false`. There is intentionally no
production `ReplanExecution` implementation until P7.4, so enabling it early
fails startup rather than marking a request completed without a real revision.
The task-check API gate remains off as well.

The immutable partial revision/carry-forward executor, daily override,
missed-day explicit refresh, and pending Today/roadmap states now have targeted
tests. Full end-to-end and rollout gates are open; P7 is `DONE`.

Discovery/approval baseline (before implementation): frontend lint, Vitest (6 files/21 tests), draft-file
Prettier check, Compose configuration, and pre-edit `git diff --check` exited 0.
Compose emitted the pre-existing Docker config permission warning. P6's full
validation is recorded in its own plan. At this earlier design checkpoint no P7
migration, backend suite, or end-to-end gate had run.
After the owner-policy/architecture documentation update, `git diff --check`
and Prettier checks for this plan, the roadmap, and `PROJECT_CONTEXT.md` exited 0. Prettier still flags `MODULE_BOUNDARIES.md` and `ADAPTIVE_LOOP.md`; their
`HEAD` versions also fail the same check, so this design turn did not reformat
those pre-existing documents or claim a repo-wide format pass.

## Documentation updates

The approval/architecture lock is recorded here, in the adaptive-loop and
module-boundary specifications, the roadmap, and `PROJECT_CONTEXT.md`. During
implementation, reconcile Journey 2/3/6 and MVP claims with actual objective
evidence and Goal authority; update Assessment, Learning, Progress, Review,
Planner, API/OpenAPI, database, testing strategy, and these approved documents
in the same change as behavior. Record exact validation before marking `DONE`.
