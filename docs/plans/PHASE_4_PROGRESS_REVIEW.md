# Plan: P4 — Evidence Projection, Knowledge State, and Review

## Status

`DONE — implemented and validated locally on 2026-09-23`

## Objective and user story

Turn the durable observational evidence produced by Phase 3 into a deterministic,
replayable estimate of what the learner currently knows, then establish the first
versioned review schedule without transferring authority to Assessment or Planner.

As an authenticated learner who completed the diagnostic, I can see concept-level
knowledge estimates, confidence, evidence provenance, and any due reviews in Vietnamese
or English. The screen must explain that this is an estimate derived from evidence. It
must not claim certification, prerequisite completion, goal completion, or recommend a
learning plan.

Phase 4 consumes the existing durable handoff and produces state/review snapshots. It
does not create resources, learning tasks, a Today plan, planner decisions, or the visual
roadmap. Those remain Phase 5 and Phase 6 authorities.

## Authoritative references

- `AGENTS.md`
- `DEVELOPMENT_RULES.md`
- `PROJECT_CONTEXT.md`
- `docs/adr/0001-modular-monolith.md`
- `docs/adr/0002-mysql-flyway-workbench.md`
- `docs/adr/0003-deterministic-planner.md`
- `docs/architecture/SYSTEM_ARCHITECTURE.md`
- `docs/architecture/MODULE_BOUNDARIES.md`
- `docs/architecture/DATABASE_DESIGN.md`
- `docs/domain/ASSESSMENT_MODEL.md`
- `docs/domain/USER_KNOWLEDGE_STATE.md`
- `docs/domain/ADAPTIVE_LOOP.md`
- `docs/domain/PLANNER_ALGORITHM_V1.md`
- `docs/api/API_CONTRACT.md`
- `docs/api/openapi-v1.yaml`
- `docs/development/TESTING_STRATEGY.md`
- `docs/product/MVP_SCOPE.md`
- `docs/product/USER_JOURNEYS.md`
- `docs/plans/PHASE_3_ASSESSMENT.md`
- `docs/plans/PHASE_ROADMAP.md`

## Current-state evidence

### Repository and delivered capabilities

- The last committed baseline is `1a03544 feat: complete phase 2 knowledge system`.
  Phase 3 and localization are complete and validated locally but remain uncommitted in
  the current working tree. Phase 4 implementation must not be mixed into that dirty
  baseline unless the owner explicitly accepts a stacked change.
- Flyway V8/V9 owns immutable assessment questions, sessions, attempts, concept evidence,
  and the generic `outbox_events` envelope. V10/V11 owns Vietnamese presentation overlays.
- Every accepted `attempt_evidence` row creates one pending
  `AssessmentEvidenceCreated` event in the same transaction. Its payload currently
  contains evidence, attempt, user, goal, graph, node, dimension, score, reliability,
  evaluator version, assessment policy version, and observed time. It contains no answer
  payload or answer key.
- The outbox has uniqueness and a pending-order index, but no dispatcher, event-version
  column, lease, retry time, or safe error metadata. All Phase 3 events therefore remain
  intentionally `PENDING`.
- No `progress` or `review` Java package, table, consumer, scheduler, API, or frontend
  feature exists.
- The existing assessment diagnostic emits only `RECOGNITION` and `UNDERSTANDING` with
  limited reliability. It cannot by itself establish practical application mastery.
- The frontend currently ends at the diagnostic evidence result and the Active Goal
  screen still contains no real plan. Phase 4 should expose Knowledge State explicitly
  without pretending Phase 5/6 already exist.

### Baseline validation

Run immediately before this discovery on the current Phase 3/localization tree:

- Backend Maven `clean verify`: PASS, exit `0`; 16 unit/architecture tests and 17
  MySQL integration/migration tests passed.
- Clean V1-to-V11 and explicit V9-to-V11 migrations: PASS on MySQL 8.4.
- Frontend OpenAPI generation, Prettier, ESLint, 8 Vitest tests, and production build:
  PASS; Vite transformed 244 modules.
- `docker compose config --quiet`: PASS with the existing inaccessible Docker config
  warning.
- Dependency/diff audit: PASS; npm reported 0 vulnerabilities.
- Rebuilt MySQL/backend/frontend containers: healthy. Vietnamese, English, and
  unsupported-language fallback smoke checks passed through `localhost:5173`.

### Context conflicts and missing policy

1. **CONTEXT CONFLICT — freshness versus replay:** `USER_KNOWLEDGE_STATE.md` applies
   evidence age to the acquisition update alpha, while also requiring incremental live
   ingestion and a later full replay at one `projectionAsOf` to produce the same state.
   Re-aging old evidence during replay changes historical alphas and cannot reproduce the
   incremental result. This plan proposes a versioned resolution below; implementation
   must not silently choose one interpretation.
2. The state spec orders evidence by `(observed_at, evidence_id)`, but IDs generated when
   a consumer inserts ledger rows depend on dispatch order. The producer outbox ID is
   already stable and must become the deterministic secondary order key.
3. The review specification says a score in `0.60–0.79` may “hold/reduce one level” but
   does not define which behavior occurs at which value. A reproducible interval policy
   needs one exact rule.
4. Durable events are required to be versioned, but existing pending outbox rows have no
   explicit event version. The consumer needs a backward-compatible v1 interpretation.
5. The logical model includes misconception state but does not fully define confidence
   accumulation or verification-based resolution. Phase 4 needs a bounded v1 rule or an
   explicit deferral.
6. `AssessmentEvidenceCreated` already contains all numeric state inputs but omits
   `assessmentPurpose`, `evaluatorType`, and `misconceptionCodes`. Existing events must
   remain consumable while future producers add those non-authoritative fields.

## Decisions proposed for owner approval

Approval of this plan accepts these Phase 4 policy and operational choices:

1. The projection policy is versioned as `knowledge-state-v1`. Ingestion/replay applies
   each evidence at its immutable observation point with `freshnessFactor = 1`. Elapsed
   time never rewrites stored acquisition scores. Decay is applied only when calculating
   an effective snapshot for a supplied `projectionAsOf`. This resolves the freshness/
   replay conflict and will be reflected in `USER_KNOWLEDGE_STATE.md`.
2. Dimension acquisition uses the existing formula and constants:

   ```text
   alpha = clamp(0.10 + reliability × 0.35, 0.10, 0.45)
   newDimensionScore = clamp(old + alpha × (evidenceScore - old), 0, 1)
   ```

   Values are calculated with `BigDecimal`, persisted at scale 4 using `HALF_UP`, and
   replayed in `(observedAt ASC, sourceEventId ASC)` order.
3. Stored mastery uses the accepted weights: recognition `0.15`, understanding `0.25`,
   recall `0.25`, application `0.35`. A missing dimension remains `0`; dimensions are
   never inferred from one another.
4. Effective dimension decay uses explainable half-lives in `knowledge-state-v1`:

   | Dimension | Half-life |
   |---|---:|
   | `RECOGNITION` | 120 days |
   | `UNDERSTANDING` | 150 days |
   | `RECALL` | 45 days |
   | `APPLICATION` | 180 days |

   The pure policy uses `StrictMath.exp`, converts through `BigDecimal.valueOf`, and
   rounds only at named persisted/API boundaries. Negative age is invalid; server UTC
   time supplies `projectionAsOf`.
5. Confidence uses the documented `0.5 volume + 0.3 coverage + 0.2 recency` formula,
   a 365-day evidence window, coverage reliability threshold `0.30`, and the same
   dimension decay at the requested snapshot time. `selfConfidence` remains excluded.
6. The stored acquisition status uses `UNKNOWN`, `LEARNING`, `PROVISIONAL`, or
   `MASTERED`. The effective snapshot may return `REVIEW_DUE` only for a node that has
   previously reached `MASTERED` and is due by review snapshot or has effective mastery
   below `0.75`. Mastery still requires stored mastery `>= 0.80` and effective confidence
   `>= 0.60`.
7. Every evidence event is first copied into the progress-owned append-only ledger.
   Projection is rebuilt for that user/node from the ordered ledger in the same
   transaction. This costs more than a blind incremental update but makes final state
   independent of outbox delivery order and is appropriate for MVP volume.
8. The existing generic outbox becomes an internal at-least-once dispatcher; no broker
   or new dependency is introduced. It uses database claims, 30-second leases, stable
   `(occurred_at, id)` ordering, batches of at most 50, one-second polling, exponential
   retry capped at five minutes, and a maximum of 10 attempts before `FAILED`.
   Handler writes and the final `PUBLISHED` transition share one MySQL transaction.
9. V12 adds `event_version=1` and lease/retry metadata to the envelope. Existing pending
   events are interpreted as `AssessmentEvidenceCreated` v1. The Phase 3 producer begins
   emitting v1 with optional `assessmentPurpose`, `evaluatorType`, and
   `misconceptionCodes`; absence in historical rows means `DIAGNOSTIC`,
   `DETERMINISTIC`, and an empty list.
10. Misconception policy `misconception-v1` consumes only allowlisted codes. Repeated
    observation updates confidence as
    `1 - (1 - oldConfidence) × (1 - evidenceReliability)` and retains the taxonomy
    severity. Two later verification evidences on the same node/dimension with score
    `>= 0.80`, reliability `>= 0.70`, and no repeated code resolve it. Phase 3 currently
    emits no codes, so this path is exercised by deterministic fixtures but does not
    invent learner misconceptions.
11. Review policy `review-interval-v1` owns intervals `[1, 3, 7, 14, 30, 60]` days.
    First transition to `MASTERED` schedules day 1. An explicit review result applies:
    score `< 0.60` resets to index 0; `0.60–0.79` moves back one index (floor 0);
    score `>= 0.80` advances one index (cap 5). Due time is UTC and server-derived.
12. Phase 4 creates schedule/read/event support, but no review-taking UI. Review attempts
    require a future explicit `ReviewCompleted` contract; ordinary diagnostic evidence
    cannot advance an interval merely because it shares a node.
13. Learner APIs expose estimates and evidence provenance only. Admin rebuild accepts an
    explicit `projectionAsOf`, defaults to dry-run, requires `ADMIN`, CSRF, and an
    idempotency key for apply mode. Rebuild drift tolerance is `0.0001`.
14. No new Maven or npm dependency and no new ADR is required if implementation stays
    inside the modular monolith, database outbox, and versioned deterministic policies.

## Scope

### In scope

- New `progress` module with immutable evidence ledger, deterministic projection,
  misconception state, replay/rebuild, learner queries, and progress events.
- New `review` module with deterministic interval policy, schedule/attempt audit model,
  due transition, queries, and review events.
- Generic database-backed local outbox dispatcher with claim/lease/retry/recovery and
  versioned handler registration.
- Backward-compatible consumption of every pending Phase 3 assessment evidence event.
- Progress/review tables, constraints, indexes, optimistic versions, and migration tests.
- Authenticated learner Knowledge State list/detail/evidence and due-review APIs.
- Protected dry-run/apply rebuild API with drift report.
- Locale-aware node labels composed through a narrow knowledge application contract.
- Learner Knowledge State frontend showing dimensions, mastery/confidence estimates,
  evidence count, review state, and explicit non-planner language.
- OpenAPI/generated types, architecture rules, unit/integration/security/frontend tests,
  and synchronized context/domain documentation.

### Out of scope

- Today plan generation, task priority, prerequisite eligibility decision, planner
  snapshots, or plan explanations.
- Learning resources/tasks, review question selection, or a learner review-submission UI.
- Visual graph/map dependency or roadmap projection.
- Goal completion or prerequisite satisfaction mutation.
- AI, BKT/IRT/FSRS, learned ranking, or statistical efficacy claims.
- Rewriting/deleting assessment attempts or `attempt_evidence` after consumption.
- Cross-curriculum node migration; Phase 4 records graph versions but does not migrate
  historical learner state to a successor graph.
- Production broker, Redis, Kafka, or multi-service extraction.

## Design

### Checkpoint P4.0 — Contract lock and clean implementation baseline

- Approve this plan and all 14 policy choices.
- Commit or otherwise explicitly accept the completed Phase 3/localization tree as the
  implementation baseline before Phase 4 code begins.
- Update the state specification to resolve freshness/replay and exact review/
  misconception policies before writing calculators.
- Add public immutable knowledge lookup and progress/review contracts; do not import
  another module's persistence package.
- Extend ArchUnit rules so progress/review/outbox persistence remains private and both
  domain packages remain framework-free.

Gate: approved formulas and boundaries, clean/accepted baseline, no unresolved authority
conflict, and no new dependency.

### Checkpoint P4.1 — Flyway progress, review, and reliable outbox schema

Add `V12__create_progress_and_outbox_schema.sql`:

#### Extend `outbox_events`

- `event_version INT NOT NULL DEFAULT 1`;
- status adds `PROCESSING`;
- nullable `available_at`, `locked_at`, `locked_until`, `locked_by`,
  `last_error_code`;
- retry/claim indexes on `(status, available_at, occurred_at, id)` and lease recovery;
- error metadata is bounded and contains no payload, SQL, stack trace, or learner answer.

#### `knowledge_evidence`

- `id`, unique `source_event_id`, immutable `source_event_key`, `source_type`,
  `source_id`, `user_id`, `goal_id`, `graph_version_id`, `knowledge_node_id`,
  `dimension`, `score`, `reliability`, `source_policy_version`, `observed_at`,
  `ingested_at`;
- unique `(source_type, source_id, knowledge_node_id, dimension)` plus event uniqueness;
- composite graph/node FK and score/reliability/dimension checks;
- indexes for replay `(user_id, knowledge_node_id, observed_at, source_event_id)` and
  learner evidence pagination.

#### `user_knowledge`

- unique `(user_id, knowledge_node_id)`; graph version retained for compatibility;
- four dimension scores, stored mastery, confidence, evidence/reliable-evidence counts,
  attempt/correct counts, first/last evidence time, nullable assessed/practiced times;
- `ever_mastered_at`, nullable derived `next_review_at`, acquisition/effective status,
  projection policy/version, `projected_at`, optimistic `version`, timestamps;
- all numeric/status/count constraints in both SQL and domain.

#### Misconception tables

- versioned `misconception_definitions` with stable code, severity, verification
  dimension, status, and timestamps;
- `user_misconceptions` unique by user/node/code with confidence, occurrence count,
  verification streak, first/last seen, last verified, resolved time, policy version,
  optimistic version.

Add `V13__create_review_schema_and_policy_seed.sql`:

- `review_schedules` unique by user/node with status, interval index/days, due time,
  last reviewed/score, source state version, policy version, optimistic version;
- append-only `review_attempts` unique by source result/node with previous/new interval,
  outcome, score, observed time, and policy version;
- append-only `knowledge_projection_runs` for explicit rebuild request, input count,
  state hash, per-field max drift, dry-run/applied outcome, actor, correlation, and time;
- seed the three documented misconception definitions only; no personal data.

Gate: clean V1-to-V13 and upgrade V11-to-V13 pass on MySQL 8.4; uniqueness, ranges,
same-version FKs, lease states, unsupported dimensions/statuses, duplicate source events,
and invalid intervals are rejected.

### Checkpoint P4.2 — Pure deterministic policies

Implement framework-free immutable types and pure policies:

- `KnowledgeStatePolicyV1` for acquisition, mastery, effective decay, confidence, and
  effective status;
- `MisconceptionPolicyV1` for observation and verification streaks;
- `ReviewIntervalPolicyV1` for initial schedule and exact score bands;
- canonical state hash and drift comparison at tolerance `0.0001`;
- validated event DTO/parser for `AssessmentEvidenceCreated` v1;
- explicit `projectionAsOf`; no domain function reads `Clock`.

The projection service locks the user/node aggregate, appends the ledger event once,
then folds all ledger rows in stable order. Duplicate consumption returns the existing
projection without a second state/review event.

Gate: exhaustive fixtures cover zero/one/many evidence, all dimensions, alpha/range/
rounding boundaries, negative/future time, decay half-lives, confidence threshold,
mastery hysteresis, replay order, misconception observe/verify/resolve, every review
score boundary, state hash, and deterministic repetition.

### Checkpoint P4.3 — Outbox dispatch, assessment consumption, and replay

Implement a generic infrastructure dispatcher with no domain policy:

1. Recover expired `PROCESSING` leases using bounded retry metadata.
2. Claim due `PENDING` rows with `FOR UPDATE SKIP LOCKED`, stable order, and a unique
   worker token.
3. Resolve an allowlisted `(ownerModule, eventType, eventVersion)` handler.
4. Parse/validate ranges, IDs, graph/node relationship, timestamps, and optional fields.
5. In one handler transaction, append/replay projection and mark the claimed event
   `PUBLISHED`.
6. On transient failure, record a safe code and backoff; on malformed/unsupported
   permanent input, mark `FAILED` without changing learner state.

The assessment producer adds the v1 optional fields without modifying historical V8/V9
migrations. Missing fields in already-pending events receive documented legacy defaults.

Progress emits one `KnowledgeStateChanged` event only when the state hash changes.
Review consumes that fact idempotently to create the first schedule after `MASTERED`.
Review emits versioned schedule events; progress consumes them to refresh its derived
`nextReviewAt`/effective status snapshot. Event chains are bounded and cannot echo the
same version indefinitely.

The rebuild use case reads only `knowledge_evidence`, folds it in canonical order at the
requested `projectionAsOf`, reports drift, and changes the projection only in explicit
apply mode. It never rereads private assessment tables.

Gate: integration/concurrency tests prove backlog drain, same-event replay, two-worker
claim exclusion, expired lease recovery, out-of-order delivery convergence, optimistic
conflict recovery, poison event isolation, retry exhaustion, transaction rollback,
exact ledger/projection/event counts, and live-versus-rebuild equality.

### Checkpoint P4.4 — Review schedule and due transition

- On first `MASTERED`, create one active schedule at `observedAt + 1 day`.
- Never advance an interval from ordinary diagnostic/practice evidence.
- Accept only explicit versioned `ReviewCompleted` input for `review_attempts` and apply
  the approved score bands once.
- A clock-injected scheduled job claims due schedules, changes `ACTIVE -> DUE` with
  optimistic locking, and emits one `ReviewBecameDue` event per schedule version.
- Successful/partial/failed review reschedules from server observation time and emits a
  new snapshot. Repeating the same review source is a no-op.
- Progress updates `REVIEW_DUE` only from the authoritative review snapshot/effective
  mastery rule; it never advances interval indices.

Gate: fixed-clock tests cover initial scheduling, all interval boundaries, cap/reset,
duplicate result, concurrent due scan, stale schedule version, timezone independence,
and event-chain convergence.

### Checkpoint P4.5 — Learner/admin API and Knowledge State UI

Public authenticated API:

```http
GET /api/v1/knowledge/me?limit=50&cursor=...
GET /api/v1/knowledge/me/{nodeId}
GET /api/v1/knowledge/me/{nodeId}/evidence?limit=25&cursor=...
GET /api/v1/reviews/today?limit=50&cursor=...
POST /api/v1/admin/progress/rebuild
```

- Identity always comes from the session for learner reads; no learner request accepts
  `userId`, mastery, confidence, status, or review due time.
- List/detail responses include graph/policy/state/review versions and
  `projectionAsOf`; evidence lists expose safe provenance, not answer content.
- Cross-owner/missing private resources share concealed `404`. Empty state is `200` with
  an empty list and explanatory processing/no-evidence metadata.
- Rebuild is `ADMIN` only, CSRF-protected, request-bounded, dry-run by default, and apply
  mode requires `Idempotency-Key`. It never accepts calculated target scores.
- Node name/description uses `Accept-Language` via a new narrow knowledge application
  query; IDs, scores, and snapshot versions remain locale-independent.

Frontend, without a new dependency:

- Add `/knowledge` and a `KnowledgeStatePage` linked from Active Goal and completed
  diagnostic result.
- Show assessed concepts, four dimension scores, mastery/confidence estimates, status,
  evidence count, last observed time, and next review when present.
- Provide list and accessible detail/evidence disclosure with text labels and progress
  bars that do not rely on color alone.
- Show loading, processing/empty, error, unauthenticated, success, stale/refetch, no
  reviews, and due-review states in Vietnamese and English.
- Explicitly state: Knowledge State is the system's current estimate; no plan exists yet.
  Do not render prerequisite-ready, goal-complete, or “learn next” claims.

Gate: API/security tests prove ownership, pagination/cursor binding, localization,
version stamps, no writable authority fields, and rebuild role/idempotency. Frontend
tests cover every state, both locales, accessibility labels, evidence provenance, and
absence of planner language.

### Checkpoint P4.6 — Audit, documentation, and Phase 5 handoff

- Run full backend, frontend, migration, Compose, container smoke, security, dependency,
  secret, architecture, concurrency, retry, and replay gates.
- Inspect pending/processing/failed backlog metrics and safe logs. Never log payloads or
  learner evidence content.
- Document how Phase 5 will consume state/review contracts without writing either.
- Update domain/API/database/module/testing/roadmap/context documents and mark this plan
  `DONE` only after every applicable gate passes.

Gate: the same immutable ledger and policy reproduce the same state within `0.0001`;
one assessment evidence event causes at most one ledger entry and converged projection;
no Phase 4 code claims or produces a personalized plan.

## Public contracts and response semantics

Representative Knowledge State item:

```json
{
  "knowledgeNodeId": "1004",
  "knowledgeNodeSlug": "http-fundamentals",
  "knowledgeNodeName": "Nền tảng HTTP",
  "graphVersionId": "1",
  "scores": {
    "recognition": 0.2575,
    "understanding": 0.0,
    "recall": 0.0,
    "application": 0.0
  },
  "storedMastery": 0.0386,
  "effectiveMastery": 0.0386,
  "confidence": 0.3447,
  "status": "LEARNING",
  "evidenceCount": 1,
  "lastObservedAt": "2026-09-23T03:00:00Z",
  "nextReviewAt": null,
  "policyVersion": "knowledge-state-v1",
  "stateVersion": 1,
  "projectionAsOf": "2026-09-23T03:00:01Z"
}
```

Expected status semantics:

- Learner reads: `200`, `401`, concealed `404`, `400` invalid cursor/bounds.
- Reviews today: `200` including empty result, `401`.
- Rebuild: `200` dry-run/applied/replayed report, `400` malformed, `401`, `403`, `404`
  concealed target, `409` idempotency/state conflict, `422` incompatible policy/time.

Representative error codes:

- `KNOWLEDGE_STATE_NOT_FOUND`
- `KNOWLEDGE_EVIDENCE_NOT_FOUND`
- `INVALID_PROGRESS_CURSOR`
- `PROJECTION_POLICY_UNSUPPORTED`
- `PROJECTION_TIME_INVALID`
- `PROJECTION_DRIFT_DETECTED`
- `REBUILD_IDEMPOTENCY_KEY_REUSED`
- `OUTBOX_EVENT_UNSUPPORTED`
- `OUTBOX_EVENT_INVALID`

## Security, privacy, concurrency, and failure behavior

- Learner identity is principal-derived and every query is owner-scoped.
- Ledger/result APIs exclude raw answers, answer keys, evaluator prompts, SQL, stack
  traces, worker IDs, and outbox payloads.
- Admin rebuild is audited with actor/correlation but logs neither private evidence text
  nor payload JSON.
- Only allowlisted event type/version and misconception codes are accepted. JSON is
  bounded before parsing; all SQL values are bound parameters.
- The append-only ledger is the reconstruction authority. `user_knowledge` and derived
  review fields may be rebuilt; assessment history is never mutated.
- Per-user/node locking plus uniqueness protects duplicate/concurrent consumption.
  Projection folds stable producer order so final state is independent of worker order.
- A poison event cannot block later unrelated events. It becomes safely `FAILED` after
  the approved policy and can be inspected/requeued only by an explicit admin workflow.
- A projection failure leaves the event retryable and commits no partial ledger/state/
  schedule/event chain.
- Scheduler time is UTC. User timezone is presentation-only in Phase 4.

## Expected files

### New backend production files

- `backend/src/main/java/com/skillpath/progress/api/**`
- `backend/src/main/java/com/skillpath/progress/application/**`
- `backend/src/main/java/com/skillpath/progress/domain/**`
- `backend/src/main/java/com/skillpath/progress/infrastructure/persistence/**`
- `backend/src/main/java/com/skillpath/review/api/**`
- `backend/src/main/java/com/skillpath/review/application/**`
- `backend/src/main/java/com/skillpath/review/domain/**`
- `backend/src/main/java/com/skillpath/review/infrastructure/persistence/**`
- `backend/src/main/java/com/skillpath/shared/infrastructure/outbox/**`
- `backend/src/main/resources/db/migration/V12__create_progress_and_outbox_schema.sql`
- `backend/src/main/resources/db/migration/V13__create_review_schema_and_policy_seed.sql`

### Existing backend files expected to change

- assessment outbox producer for the backward-compatible v1 envelope fields;
- narrow knowledge application contract/implementation for localized node summaries;
- scheduler/application configuration;
- security matcher documentation only if explicit admin matching is needed;
- `ArchitectureTest.java`.

### Tests

- `backend/src/test/java/com/skillpath/progress/domain/**`
- `backend/src/test/java/com/skillpath/review/domain/**`
- `backend/src/test/java/com/skillpath/PhaseFourFlowIT.java`
- `backend/src/test/java/com/skillpath/ProgressMigrationUpgradeIT.java`
- focused dispatcher concurrency/retry integration fixtures.

### Frontend

- `frontend/src/features/progress/KnowledgeStatePage.tsx`
- `frontend/src/features/progress/KnowledgeStatePage.test.tsx`
- `frontend/src/app/App.tsx`
- `frontend/src/features/goals/ActiveGoalPage.tsx`
- `frontend/src/features/assessment/DiagnosticPage.tsx`
- `frontend/src/shared/api/client.ts`
- generated `frontend/src/shared/api/schema.d.ts`
- `frontend/src/shared/i18n/I18n.tsx`
- `frontend/src/shared/styles/global.css`

### Contracts and documentation

- `docs/api/openapi-v1.yaml`
- `docs/api/API_CONTRACT.md`
- `docs/architecture/DATABASE_DESIGN.md`
- `docs/architecture/MODULE_BOUNDARIES.md`
- `docs/architecture/SYSTEM_ARCHITECTURE.md`
- `docs/domain/USER_KNOWLEDGE_STATE.md`
- `docs/domain/ADAPTIVE_LOOP.md`
- `docs/development/TESTING_STRATEGY.md`
- `docs/plans/PHASE_ROADMAP.md`
- `PROJECT_CONTEXT.md`
- this plan

Any broker, external scheduler, new dependency, writable learner state endpoint,
planner/learning implementation, or different formula/threshold is a material deviation
requiring owner review.

## Tests

### Unit

- Alpha min/max, score clamp, scale, rounding, and all dimension updates.
- Stored mastery weights and missing-dimension behavior.
- Exact half-life/zero-age/future-age decay and effective mastery.
- Confidence volume/coverage/recency, 365-day window, and `0.30` boundary.
- `UNKNOWN/LEARNING/PROVISIONAL/MASTERED/REVIEW_DUE` precedence and hysteresis.
- Same events in different delivery order fold to the same producer-ordered state.
- Misconception repetition, allowlist, confidence, verification reset, and resolution.
- Review initial/failed/partial/success/cap transitions and versioning.
- Stable state hash, drift tolerance, and replay equality.

### MySQL migration/integration

- Clean V1-to-V13 and upgrade V11-to-V13.
- Hibernate schema validation and all range/unique/composite FK constraints.
- Pending legacy event backfill to event version 1.
- Claim ordering, lease recovery, safe backoff, retry exhaustion, and failed-event
  isolation with two workers.
- Duplicate/out-of-order evidence, node lock convergence, ledger/projection/outbox
  atomicity, and representative replay query indexes.
- Review schedule uniqueness, due scan concurrency, and append-only attempt audit.

### API/security

- Unauthenticated learner reads `401`; cross-owner details/evidence concealed `404`.
- List/detail/evidence/review empty and populated contracts, stable cursors, and bounds.
- Locale changes only labels and `Content-Language`, never numeric/version fields.
- No raw answer, answer key, private event payload, or writable mastery field exposed.
- Rebuild learner/curator forbidden, admin + CSRF required, dry-run unchanged, apply
  idempotent, key mismatch conflict, explicit `projectionAsOf` validation.

### Frontend

- Route/link from completed diagnostic and Active Goal.
- Loading, evidence-processing empty, no-evidence, populated, error, unauthenticated,
  stale/refetch, no-review, and review-due states.
- Vietnamese/English labels and localized node names.
- Four dimensions and confidence/mastery clearly identified as estimates.
- Keyboard-readable detail/evidence controls, focus visibility, and non-color statuses.
- No “next task”, prerequisite satisfied, goal complete, or planner recommendation claim.

### Regression commands

```text
Maven 3.9.16 -o -Dmaven.repo.local=C:/Users/Dell/.m2/repository -f backend/pom.xml clean verify
npm --prefix frontend run api:generate
npm --prefix frontend run format
npm --prefix frontend run lint
npm --prefix frontend run test -- --run
npm --prefix frontend run build
docker compose config --quiet
scripts/audit.ps1
docker compose --profile app up -d --build
```

Do not claim a gate passed without a successful exit code. Record exact test counts,
migration paths, warnings, dispatcher timing configuration, and any policy deviation.

## Acceptance criteria

1. Given the eight pending Phase 3 evidence events, when the Phase 4 dispatcher starts,
   then each becomes one immutable progress-ledger row and the corresponding user/node
   projection converges exactly once.
2. Given the same event is delivered repeatedly or its lease expires, when processing
   resumes, then ledger/state/event counts do not duplicate.
3. Given events are claimed in a different worker order, when projection completes,
   then canonical producer ordering yields the same scores, mastery, confidence, status,
   and state hash.
4. Given the same ledger, policy, and `projectionAsOf`, when rebuilt, then every output
   matches live state within `0.0001`; dry-run performs no mutation.
5. Given only objective diagnostic evidence, then application/recall remain zero and the
   system does not claim authoritative mastery merely from a high diagnostic score.
6. Given insufficient confidence at mastery threshold, then status is `PROVISIONAL`, not
   `MASTERED`; a never-mastered weak node cannot become `REVIEW_DUE`.
7. Given a first true `MASTERED` transition, then exactly one day-1 schedule is created;
   ordinary diagnostic evidence never advances its interval.
8. Given an explicit review result, then the exact failed/partial/success interval rule
   is applied once and every transition remains auditable/replayable.
9. Given a due schedule, then the server-time transition and progress snapshot occur
   once even with concurrent scheduler workers.
10. Given an invalid, unsupported, or permanently malformed event, then no private state
    changes, unrelated events continue, and only a safe failure code is retained.
11. Given a learner request, then only their state/evidence/reviews are visible and no
    client can submit user ID, mastery, confidence, status, due time, or policy authority.
12. Given Vietnamese or English selection, then learner text and node labels localize
    while IDs, numeric estimates, evidence, and versions remain identical.
13. Given Phase 5/6 are absent, then the UI explicitly says no personalized plan has been
    generated and no API response pretends to recommend the next task.
14. Existing Phase 1–3 and localization behavior remains green.

## Rollout and rollback

- Establish a committed/accepted Phase 3 + localization baseline first.
- Deploy V12/V13 before application code. They are additive except the forward extension
  of the generic outbox constraint/columns; existing pending rows remain valid v1 input.
- Start the dispatcher only after progress/review handlers and schema are present. On
  first deployment it drains the existing backlog; expose pending/failed counts in
  operational logs/metrics without payloads.
- Older application code safely ignores new progress/review tables and outbox columns.
  Rolling back before backlog consumption leaves pending events intact. Rolling back
  after consumption leaves rebuildable ledger/projections unused but does not damage
  assessment history.
- Database rollback is restore/forward-fix. Never edit V1–V13 after application.
- Policy changes require a new policy version and explicit rebuild/migration; never
  silently reinterpret historical state under `knowledge-state-v1`.

## Risks and open decisions

- The proposed half-lives, misconception accumulation/resolution, and exact partial
  review rule are product/domain policy choices, not empirically validated learning
  science. Approval accepts them as explainable MVP defaults, not efficacy claims.
- Diagnostic-only evidence is intentionally weak and covers only two dimensions. Most
  nodes should remain `LEARNING` or low-confidence; this is correct and may feel less
  impressive than an unjustified mastery score.
- Outbox polling is appropriate for the modular-monolith MVP but needs backlog/latency
  monitoring. A future broker requires an ADR and must preserve event/idempotency contracts.
- Rebuilding the full per-node ledger on every event favors determinism over scale. Add
  checkpoint optimization only with replay-equivalence evidence and a versioned plan.
- Graph successor migration remains unresolved. State is pinned to source graph/node IDs;
  a future curriculum migration needs an explicit node-map policy.
- Review-taking is not available until learning/review task delivery exists. Phase 4 may
  display an empty due list or a due item without an action, clearly labelled as upcoming.
- The current Phase 3/localization work is uncommitted; stacking Phase 4 would make audit,
  rollback, and review materially harder.
- Flyway 11.7.2/MySQL 8.4 compatibility warning remains and must be rechecked for V12/V13.

## Validation results

- Backend `clean verify`: PASS; 24 unit/architecture tests and 18 integration tests are
  green, including evidence-to-projection idempotency and V11-to-V13 upgrade coverage.
- After the final misconception lifecycle change, a clean targeted verify reran all 24
  unit/architecture tests plus the five Phase 3/4 handoff integration tests: PASS.
- Frontend lint: PASS; Vitest: PASS (3 files / 8 tests); production build: PASS.
- OpenAPI generation and formatting were rerun after adding the Phase 4 contract.
- Existing Flyway 11.7.2/MySQL 8.4 compatibility warning remains.
- Review-taking UI and `ReviewCompleted` production are deliberately deferred. Phase 4
  creates due schedules/read support but does not pretend a review task was delivered.

## Documentation updates

During implementation, update in the same change:

- User Knowledge State with the accepted acquisition-versus-effective decay resolution,
  exact confidence/misconception/review policies, and policy versions.
- Adaptive loop with concrete local outbox dispatch/failure semantics.
- API/OpenAPI with learner state/evidence/review and admin rebuild contracts.
- Database design/migration history and module dependency/event diagram.
- Testing strategy with Phase 4 executable replay/concurrency/security gates.
- Roadmap Phase 4 status and the explicit Phase 5 handoff.
- `PROJECT_CONTEXT.md` with delivered facts, remaining limits, and confirmation that no
  personalized plan exists until later phases.
