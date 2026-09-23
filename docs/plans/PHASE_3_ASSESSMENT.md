# Plan: P3 — Diagnostic Assessment and Concept Evidence

## Status

`DONE — owner approved on 2026-09-23; implementation and validation completed on 2026-09-23`

## Objective and user story

Deliver the first learner-facing diagnostic vertical slice on top of the active goal
and published knowledge graph.

As an authenticated learner with an active goal, I can start or resume a short,
curated diagnostic, answer objective questions, and receive a transparent summary of
the concept-level evidence created by my answers. Retrying a submission must never
create a second attempt, evidence row, or handoff event.

This phase creates evidence only. It does not calculate mastery, confidence,
prerequisite satisfaction, a Today plan, or goal completion. Those authorities remain
with later `progress`, `planner`, and `goal` work.

## Authoritative references

- `AGENTS.md`
- `DEVELOPMENT_RULES.md`
- `PROJECT_CONTEXT.md`
- `docs/adr/0001-modular-monolith.md`
- `docs/adr/0004-ai-boundary.md`
- `docs/adr/0006-web-session-authentication.md`
- `docs/architecture/SYSTEM_ARCHITECTURE.md`
- `docs/architecture/MODULE_BOUNDARIES.md`
- `docs/architecture/DATABASE_DESIGN.md`
- `docs/domain/ASSESSMENT_MODEL.md`
- `docs/domain/KNOWLEDGE_GRAPH.md`
- `docs/domain/USER_KNOWLEDGE_STATE.md`
- `docs/domain/ADAPTIVE_LOOP.md`
- `docs/api/API_CONTRACT.md`
- `docs/development/TESTING_STRATEGY.md`
- `docs/product/MVP_SCOPE.md`
- `docs/product/USER_JOURNEYS.md`
- `docs/plans/PHASE_ROADMAP.md`

## Current-state evidence

### Repository and delivered capabilities

- Baseline commit is `1a03544 feat: complete phase 2 knowledge system`.
- The working tree was clean at the start of discovery.
- Phase 1 provides authenticated cookie sessions, CSRF, one active goal per learner,
  and an idempotent goal command.
- Phase 2 provides a published `JAVA_BACKEND` graph with 17 nodes and 23 relations,
  bounded public reads, and immutable graph/version IDs suitable for assessment
  mappings.
- No `assessment`, `progress`, `review`, `learning`, or `planner` implementation exists.
- The frontend has auth, goal setup, and an Active Goal preview. The current preview
  still says Phase 2 is upcoming and its diagnostic action is disabled.
- Checked-in OpenAPI types are generated into
  `frontend/src/shared/api/schema.d.ts`.

### Baseline validation

Run on 2026-09-23 before design changes:

- Backend Maven `clean verify`: PASS after rerunning outside the workspace sandbox so
  javac could read `C:\Users\Dell\.m2`; 9 unit/architecture tests and 10 integration
  tests passed.
- The first sandboxed Maven attempts failed before code compilation with
  `AccessDeniedException` on `spring-web-6.2.19.jar`; this was an environment access
  failure, not a source failure.
- Frontend lint: PASS.
- Frontend Vitest: PASS, 2 files / 3 tests.
- Frontend production build: PASS, 241 modules.
- `docker compose config --quiet`: PASS with the existing warning that Docker CLI
  cannot read `C:\Users\Dell\.docker\config.json`.
- Existing Flyway warning remains: Flyway 11.7.2 has not declared MySQL 8.4 as a
  tested line, although clean and V5-to-V7 migration tests pass.

### Context conflicts and implementation constraints

1. **CONTEXT CONFLICT — stale learner UI:** `PROJECT_CONTEXT.md` and the roadmap mark
   Phase 2 complete, while `ActiveGoalPage.tsx` says Phase 2 is still upcoming. Phase 3
   must replace that preview with the real diagnostic entry point.
2. `idempotency_records` is named generically in the logical database design, but its
   applied V2 schema has a foreign key from `resource_id` to `user_goals` and its Java
   adapter is private to `goal`. Assessment must not import or repurpose it. Attempt
   idempotency will be assessment-owned and enforced by assessment tables.
3. `AssessmentSession` requires an owned active goal, but `goal` currently exposes no
   dedicated public query contract. Phase 3 must add a narrow immutable application
   contract; assessment must not import goal persistence.
4. Assessment needs the currently published graph ID and allowed goal node IDs.
   Existing knowledge queries are optimized for public graph rendering, not an
   internal assessment invariant. Add a narrow knowledge application contract rather
   than querying knowledge repositories or parsing transport DTOs.

## Approved decisions

The owner approved these Phase 3 policy choices on 2026-09-23:

1. Phase 3 is objective-only: `SINGLE_CHOICE` and `MULTIPLE_CHOICE` with `EXACT`
   evaluation. Open response, code execution, rubric, human review, and AI evaluation
   remain inactive until later phases.
2. The canonical diagnostic contains eight project-authored questions with an
   estimated duration of approximately six to eight minutes. It samples foundational
   nodes; it does not claim full curriculum coverage.
3. A diagnostic session expires seven days after start. Leaving the page does not
   abandon it; the learner resumes the pinned session until completion or expiry.
4. A goal may have one completed diagnostic baseline. `POST /assessments/diagnostic`
   creates, resumes, or returns that completed session rather than silently creating
   repeated baselines. A future explicit retake policy may add new session semantics.
5. Multiple-choice partial score policy v1 is:

   ```text
   score = clamp(
       (correctSelections - incorrectSelections) / correctOptionCount,
       0,
       1
   )
   ```

   Single-choice score is `1` for the exact option and `0` otherwise.
6. Objective evidence reliability is versioned as `assessment-objective-v1`:
   single-choice recognition `0.45`, multiple-choice understanding `0.55`, capped by
   `maxEvidenceStrength` and multiplied by the concept mapping weight. Calculations use
   `BigDecimal`, scale 4, `HALF_UP`.
7. Objective Phase 3 questions may emit only `RECOGNITION` or `UNDERSTANDING`
   evidence. They cannot emit `RECALL` or `APPLICATION` evidence.
8. Every accepted evidence row creates one durable `AssessmentEvidenceCreated` outbox
   record in the same transaction. Phase 3 stores the handoff; Phase 4 will implement
   the idempotent progress consumer.
9. No new Maven or npm dependency is required.
10. Diagnostic evidence is observational, not authoritative mastery. Assessment
    answers what an attempt demonstrated; Knowledge State estimates what the learner
    currently knows; Planner decides what the learner should do next. Diagnostic
    API/UI language must preserve this boundary.

## Scope

### In scope

- New `assessment` module with API, application, domain, and persistence layers.
- Immutable/versioned questions and question-to-knowledge mappings.
- Deterministic objective scoring and evidence calculation policy v1.
- Diagnostic create/resume/completed behavior pinned to an active goal and one
  published graph version.
- Session question snapshot/order so later question activation changes cannot alter an
  in-progress diagnostic.
- Idempotent attempt submission with transactional attempt, evidence, session progress,
  and outbox writes.
- Project-authored Java Backend diagnostic seed.
- Authenticated diagnostic API and result API with answer-key secrecy and ownership
  enforcement.
- Learner diagnostic frontend: start/resume, progress, objective answer form, error
  recovery, completion, and evidence summary.
- OpenAPI, generated TypeScript types, migrations, tests, architecture rules, and
  context documentation.

### Out of scope

- Mastery/confidence projection, `user_knowledge`, review scheduling, or misconception
  state; these belong to Phase 4.
- Today task generation, resources, planner decisions, roadmap overlays, or goal
  completion.
- Free-text, long-text, code, debugging, design, rubric, AI, or human evaluation.
- AI SDK/provider selection or even a runtime AI stub; no Phase 3 use case requires it.
- Curator question CRUD/import UI or browser role administration.
- Randomized/adaptive question selection, item-response theory, anti-cheat systems, or
  high-stakes exam claims.
- Diagnostic retakes and comparison between attempts.
- Visual knowledge-map rendering or a new graph library.
- Progress consumption of outbox events; Phase 3 only produces the durable contract.

## Design

### Checkpoint P3.0 — Contract lock and boundary clarification

- Approve this plan and the policy choices above.
- Add `goal.application.GoalQueries` implemented by the existing goal application
  service. Its immutable assessment-facing view exposes only goal ID, template ID,
  status, and ownership-validated active state.
- Add `knowledge.application.AssessmentKnowledgeQueries` returning the current
  published graph ID and allowed goal-node summaries for one goal-template ID.
- Update the module dependency documentation to include `assessment -> goal contract`
  and retain `assessment -> knowledge contract`.
- Add architecture rules preventing code outside `assessment.infrastructure.persistence`
  from importing assessment persistence types.

Gate: owner-approved plan, no unresolved authority conflict, clean boundaries, and no
repository/entity import across modules.

### Checkpoint P3.1 — Flyway assessment schema and canonical seed

Add `V8__create_assessment_schema.sql` with assessment-owned tables:

#### `questions`

- Stable identity: `id`, immutable `question_key`, audit timestamps.
- `question_key` is unique and curriculum-neutral.

#### `question_versions`

- `id`, `question_id`, positive `version_number`, `type`, `prompt`, `difficulty`,
  `estimated_seconds`, `scoring_strategy`, `options`, `answer_key`, nullable `rubric`,
  `status`, `source`, and timestamps.
- Unique `(question_id, version_number)`.
- JSON validity checks where MySQL provides them; application validation remains
  authoritative for the per-type shape.
- Phase 3 active rows are limited by application policy to objective types and `EXACT`.

#### `question_knowledge`

- `question_version_id`, `graph_version_id`, `knowledge_node_id`, `dimension`,
  `weight`, `max_evidence_strength`, nullable `rubric_criterion_key`.
- Composite foreign key keeps the node in the declared graph version.
- Unique question-version/node/dimension mapping.
- Decimal range checks for weight and evidence cap. The domain validates that mapping
  weights for one version are positive and sum to `1.0000`.

#### `assessment_sessions`

- `id`, `user_id`, `goal_id`, `purpose`, `status`, `graph_version_id`,
  `assessment_policy_version`, `started_at`, `expires_at`, nullable `completed_at`,
  optimistic `version`, and timestamps.
- Statuses: `IN_PROGRESS`, `COMPLETED`, `EXPIRED`.
- Purpose enum includes the domain values, although Phase 3 creates only `DIAGNOSTIC`.
- Generated nullable uniqueness prevents two active diagnostic sessions for one goal.
- A second generated nullable uniqueness prevents two completed diagnostic baselines
  for one goal.

#### `assessment_session_questions`

- `id`, `session_id`, `question_version_id`, one-based `position`, timestamp.
- Unique `(session_id, position)` and `(session_id, question_version_id)`.
- This is the immutable session snapshot; next-question reads never reselect from the
  current active bank.

#### `answer_attempts`

- `id`, `session_id`, `session_question_id`, `user_id`, `idempotency_key`,
  `request_hash`, `answer_payload`, `raw_score`, nullable `self_confidence`,
  `time_spent_seconds`, `evaluation_status`, `evaluator_version`, `submitted_at`.
- Unique `(user_id, session_id, idempotency_key)` handles safe retry.
- Unique `(session_id, session_question_id)` enforces one diagnostic answer per
  question even when a different idempotency key is used.
- Score/confidence/time/status checks are enforced in both database and domain.

#### `attempt_evidence`

- `id`, `attempt_id`, `graph_version_id`, `knowledge_node_id`, `dimension`, `score`,
  `reliability`, `evaluator_type`, `evaluator_version`, `rationale`,
  `misconception_codes`, `created_at`.
- Unique `(attempt_id, knowledge_node_id, dimension)` prevents duplicate concept
  evidence.
- Composite foreign key preserves graph/node integrity.
- Phase 3 evaluator type is always `DETERMINISTIC`; misconception codes are empty.

#### `outbox_events`

- Generic reliability envelope: `id`, unique `event_key`, `owner_module`,
  `aggregate_type`, `aggregate_id`, `event_type`, `payload`, `status`, `attempt_count`,
  `occurred_at`, nullable `published_at`, and timestamps.
- Phase 3 writes `owner_module=assessment`,
  `event_type=AssessmentEvidenceCreated`, `status=PENDING`.
- Payload contains evidence ID, attempt ID, user ID, goal ID, graph version, node ID,
  dimension, score, reliability, evaluator version, policy version, and observed time.
  It contains neither answer payload nor answer key.
- Unique event key is derived from the immutable evidence ID.

Indexes follow actual paths: active/completed diagnostic by goal, session by owner,
session questions by position, attempts by session/submission, evidence by
attempt/node, and pending outbox by status/time.

Add `V9__seed_java_backend_diagnostic.sql` with eight project-authored objective
questions targeting representative foundational nodes:

- programming fundamentals;
- Git fundamentals;
- HTTP fundamentals;
- relational data and SQL;
- Java language;
- object-oriented design;
- collections and generics;
- unit testing.

The seed must use stable keys and graph-version mappings, contain no copied external
curriculum text, and expose no personal data.

Gate: clean V1-to-V9 and upgrade V7-to-V9 migrations pass on MySQL 8.4; constraints
reject invalid ranges, duplicate active/completed diagnostics, cross-version mappings,
duplicate submissions, and duplicate evidence.

### Checkpoint P3.2 — Pure deterministic assessment domain

Implement immutable domain types and pure functions without Spring, JPA, HTTP, clock
reads, or AI:

- question type/status/source/scoring enums;
- assessment purpose/session/evaluation status enums;
- answer selections and validated option sets;
- `ObjectiveScoringPolicyV1`;
- `EvidencePolicyV1`;
- question mapping validation;
- deterministic request canonicalization/hash input;
- session completion decision from pinned question count and accepted attempts.

Invariants:

- Question options have nonblank unique opaque IDs and nonblank labels.
- Single choice has exactly one selected option and exactly one correct option.
- Multiple choice has at least two options and at least one correct option; selected
  IDs must be a subset of the offered IDs.
- Difficulty is 1–5 and estimated seconds is positive.
- Objective mappings use only `RECOGNITION` or `UNDERSTANDING`.
- Mapping weights are positive, sum to `1.0000`, and reliability is capped before
  persistence.
- All calculated decimals are clamped to `[0,1]` and rounded by the named policy.
- No evaluator can infer mastery, satisfy a prerequisite, or update another module.

Gate: exhaustive unit fixtures cover correct, wrong, partial, over-selection, unknown
option, empty answer, rounding boundaries, mapping weights, caps, and deterministic
replay.

### Checkpoint P3.3 — Session create/resume and pinned question delivery

Application flow for `POST /api/v1/assessments/diagnostic`:

1. Derive user ID from `AuthenticatedUser`.
2. Resolve the learner's owned active goal through `GoalQueries`.
3. Resolve the current published graph and allowed node IDs through
   `AssessmentKnowledgeQueries`.
4. Return the existing completed diagnostic, or lock/resume a non-expired active
   diagnostic.
5. Lazily mark an elapsed session `EXPIRED` using the injected server `Clock`.
6. Select the eight active, compatible seeded question versions in stable ID order.
7. Validate coverage/mappings and snapshot the ordered versions into
   `assessment_session_questions` in the same transaction as session creation.
8. Concurrent starts converge on one active session through database uniqueness and
   reload; no duplicate snapshot is produced.

`GET /api/v1/assessments/{sessionId}/next-question`:

- Requires authentication and concealed ownership (`404` for missing/cross-owner).
- Returns the first pinned unanswered question by position.
- Returns `204` when the session is completed.
- Returns `410 ASSESSMENT_SESSION_EXPIRED` when expiry is observed.
- Response contains session/question opaque IDs, type, prompt, options, progress,
  estimated seconds, and expiry—but never answer key, rubric internals, mapping
  weights, or evaluator configuration.

Gate: MySQL/API tests prove stable snapshots, resume, expiry, concurrent start,
published-version pinning, ownership, and answer-key secrecy.

### Checkpoint P3.4 — Idempotent submit, evidence, and durable handoff

`POST /api/v1/assessments/{sessionId}/attempts` requires `Idempotency-Key` and accepts:

```json
{
  "sessionQuestionId": "opaque-id",
  "selectedOptionIds": ["option-id"],
  "selfConfidence": 0.7,
  "timeSpentSeconds": 42
}
```

Submission transaction:

1. Validate idempotency-key syntax and canonical request hash.
2. Lock/validate owned in-progress session, expiry, pinned question, and expected next
   position. Out-of-order submission is rejected.
3. If the same key/hash already completed, return the original attempt with `200`.
   Same key with a different payload returns `409 IDEMPOTENCY_KEY_REUSED`.
4. A different key for an already-answered session question returns
   `409 QUESTION_ALREADY_ANSWERED`.
5. Validate selected IDs against the server-held options and evaluate with the pinned
   policy version.
6. Insert one attempt, one evidence row per valid question mapping, and one outbox row
   per evidence in the same transaction.
7. If the final pinned question is accepted, atomically mark the session `COMPLETED`.

Concurrent duplicate submissions must have one winner. The loser reloads the completed
outcome for the same key/hash or receives the documented conflict; it never duplicates
evidence/events.

Gate: integration tests verify same-key replay, hash mismatch, different-key conflict,
concurrent duplicates, transaction rollback, exact evidence/event counts, and final
completion.

### Checkpoint P3.5 — Result API and learner diagnostic UI

`GET /api/v1/assessments/{sessionId}/result`:

- Conceals cross-owner resources with `404`.
- Returns `409 ASSESSMENT_NOT_COMPLETED` before completion.
- Returns graph version, assessment policy version, timestamps, answered/total count,
  overall objective score, and concept evidence grouped by node/dimension.
- Node display names are composed at read time through the knowledge application
  contract; assessment does not copy or own curriculum labels.
- Labels explicitly say diagnostic evidence, not mastery, confidence, readiness, or
  prerequisite completion.

Frontend work, without a new dependency:

- Add `/assessment/diagnostic` route and `DiagnosticPage` feature.
- Replace the stale Phase 2 disabled preview on Active Goal with a working
  Start/Resume/View Result action.
- Start only from an explicit user action; do not create a session merely by rendering
  a page.
- Render single-choice radios and multiple-choice checkboxes in `fieldset`/`legend`,
  with keyboard access, visible focus, progress text, estimated time, and accessible
  field errors.
- Handle loading, no active goal, empty/unavailable question bank, permission/session
  loss, expired session, submit conflict/recovery, success, and completed result.
- Preserve the current answer while a submission is pending. On an ambiguous retry,
  reuse the same in-memory idempotency key; if the page reloads and the question was
  already accepted, refetch next-question/result rather than resubmitting blindly.
- Do not show correct answers or per-question correctness during the diagnostic.

Gate: frontend tests cover start/resume, question rendering, validation, stable
idempotency header during retry, completion, error/expired states, and absence of
answer-key fields. Lint, build, and generated-contract drift checks pass.

### Checkpoint P3.6 — Audit, documentation, and handoff to Phase 4

- Run full backend, frontend, Compose, migration, API/security, architecture, and
  dependency/secret audits.
- Review ownership checks, module imports, transaction boundaries, logs, answer-key
  exposure, payload bounds, concurrency, and replay paths.
- Document that outbox rows remain pending until the approved Phase 4 progress
  consumer exists; this is expected, not a silent failure.
- Update assessment/domain/API/database/module/testing documents and
  `PROJECT_CONTEXT.md` with implementation facts and remaining risks.
- Mark this plan `DONE` only after all applicable gates pass.

Gate: diagnostic produces traceable concept evidence and exactly one durable handoff
per evidence; no code claims mastery or a personalized plan.

## Public API contract

```http
POST /api/v1/assessments/diagnostic
GET  /api/v1/assessments/{sessionId}/next-question
POST /api/v1/assessments/{sessionId}/attempts
GET  /api/v1/assessments/{sessionId}/result
```

Expected status semantics:

- Start: `201` new, `200` resumed/completed existing.
- Next question: `200` question, `204` completed, `404` concealed, `410` expired.
- Submit: `201` new attempt, `200` idempotent replay, `400` malformed,
  `409` idempotency/order/already-answered conflict, `410` expired, `422` invalid
  answer/session rule.
- Result: `200` completed, `404` concealed, `409` incomplete.

Representative error codes:

- `ACTIVE_GOAL_NOT_FOUND`
- `DIAGNOSTIC_UNAVAILABLE_FOR_GRAPH_VERSION`
- `ASSESSMENT_SESSION_NOT_FOUND`
- `ASSESSMENT_SESSION_EXPIRED`
- `ASSESSMENT_NOT_COMPLETED`
- `QUESTION_NOT_IN_SESSION`
- `QUESTION_OUT_OF_ORDER`
- `QUESTION_ALREADY_ANSWERED`
- `INVALID_ANSWER_SELECTION`
- `INVALID_IDEMPOTENCY_KEY`
- `IDEMPOTENCY_KEY_REUSED`

All endpoints require an authenticated session. POST endpoints require CSRF. No
request accepts `userId` or trusted score/evidence fields from the client.

## Security, privacy, concurrency, and failure behavior

- Identity always comes from the authenticated principal.
- Session/attempt/result queries include owner scope; cross-owner and nonexistent IDs
  share a concealed `404` response.
- Answer keys remain server-side and are excluded from question, error, event, and log
  payloads.
- Raw answer payload is sensitive learner data: do not log it or include it in outbox
  events. Database access follows existing application credentials and local-only test
  data rules.
- Request bounds: idempotency key 1–128 safe characters, bounded option counts/IDs,
  self-confidence `[0,1]`, and time spent `0–3600` seconds.
- Server clock determines submit time and expiry; the client cannot backdate evidence.
- Optimistic version plus database uniqueness protects session completion and
  concurrent start/submit races.
- A failed transaction leaves no partial attempt, evidence, completion, or outbox row.
- Because evaluation is local/deterministic, Phase 3 has no external timeout/retry
  failure mode.
- Question text and seed content require pedagogical review before production claims;
  scores are diagnostic signals, not certification.

## Expected files

### New backend production files

- `backend/src/main/java/com/skillpath/assessment/api/**`
- `backend/src/main/java/com/skillpath/assessment/application/**`
- `backend/src/main/java/com/skillpath/assessment/domain/**`
- `backend/src/main/java/com/skillpath/assessment/infrastructure/persistence/**`
- `backend/src/main/java/com/skillpath/shared/infrastructure/outbox/**` only for a
  generic persistence adapter with no domain policy
- `backend/src/main/resources/db/migration/V8__create_assessment_schema.sql`
- `backend/src/main/resources/db/migration/V9__seed_java_backend_diagnostic.sql`

### Existing backend files expected to change

- narrow public contracts/implementations under `goal.application` and
  `knowledge.application`
- `backend/src/main/java/com/skillpath/auth/infrastructure/security/SecurityConfig.java`
  only if explicit matcher documentation is useful; authenticated fallback already
  protects assessment routes
- `backend/src/main/java/com/skillpath/shared/config/ApplicationConfig.java`
- `backend/src/test/java/com/skillpath/ArchitectureTest.java`

### Tests

- `backend/src/test/java/com/skillpath/assessment/domain/**`
- `backend/src/test/java/com/skillpath/PhaseThreeFlowIT.java`
- `backend/src/test/java/com/skillpath/AssessmentMigrationUpgradeIT.java`

### Frontend

- `frontend/src/features/assessment/DiagnosticPage.tsx`
- `frontend/src/features/assessment/DiagnosticPage.test.tsx`
- `frontend/src/shared/api/client.ts`
- `frontend/src/shared/api/client.test.ts`
- `frontend/src/shared/api/schema.d.ts` generated from OpenAPI
- `frontend/src/app/App.tsx`
- `frontend/src/features/goals/ActiveGoalPage.tsx`
- `frontend/src/shared/styles/global.css`

### Documentation

- `docs/api/openapi-v1.yaml`
- `docs/api/API_CONTRACT.md`
- `docs/architecture/DATABASE_DESIGN.md`
- `docs/architecture/MODULE_BOUNDARIES.md`
- `docs/domain/ASSESSMENT_MODEL.md`
- `docs/development/TESTING_STRATEGY.md`
- `docs/plans/PHASE_ROADMAP.md`
- `PROJECT_CONTEXT.md`
- this plan

Any new dependency, AI integration, question-admin UI, or persistence change outside
this list is a material deviation and requires owner review.

## Tests

### Unit

- Single-choice exact correct/incorrect.
- Multiple-choice exact, partial, incorrect selection, over-selection, and clamp.
- Unknown/duplicate/empty selections rejected.
- Dimension/type compatibility.
- Weight sum, reliability cap/scaling, decimal rounding, and range invariants.
- Same input and policy always produce identical score/evidence.
- Session completion only after all pinned questions have accepted attempts.

### MySQL migration/integration

- Clean V1-to-V9 and upgrade V7-to-V9.
- Hibernate schema validation.
- Question/version/mapping uniqueness and same-graph foreign keys.
- Generated active/completed diagnostic uniqueness.
- Stable session snapshot after source question status changes.
- Attempt/evidence/outbox atomicity and indexes for representative queries.
- Same-key replay and concurrent duplicate behavior.

### API/security

- Unauthenticated requests return `401`; POST without CSRF returns `403`.
- Cross-user session/attempt/result access is concealed as `404`.
- Start without active goal and start without compatible question bank fail safely.
- New/resume/completed/expired session paths.
- Sequential question enforcement and completion.
- Idempotency replay, hash mismatch, different-key duplicate, and concurrent submit.
- Response/error/log/event serialization contains no answer key.
- Client cannot submit score, reliability, evaluator, node, graph, or user authority.

### Frontend

- Explicit start and navigation.
- Loading, unavailable/empty, error, unauthenticated, expired, question, pending,
  completed, and result states.
- Radio/checkbox keyboard behavior and accessible validation association.
- Submit button disabled for invalid/pending input.
- Stable retry key and recovery from already-answered conflict.
- No answer-key/correct-answer presentation.

### Regression commands

```text
Maven 3.9.16 -o -Dmaven.repo.local=C:/Users/Dell/.m2/repository -f backend/pom.xml clean verify
npm --prefix frontend run api:generate
npm --prefix frontend run format
npm --prefix frontend run lint
npm --prefix frontend run test -- --run
npm --prefix frontend run build
docker compose config
scripts/audit.ps1
```

Do not claim a gate passed unless its exit code is successful. If repo-wide Prettier
still fails on pre-existing formatting, report exact files and do not weaken the gate.

## Acceptance criteria

1. Given an authenticated learner with an active Java Backend goal, when they start a
   diagnostic, then one session pinned to the published graph and eight immutable
   question versions is created.
2. Given the learner leaves and returns before expiry, when they start again, then the
   same unanswered position resumes without a duplicate session.
3. Given a question response, when the learner submits it, then deterministic score,
   mapped evidence, and one outbox event per evidence are committed atomically.
4. Given the same idempotency key and normalized payload is retried, then the original
   attempt is returned and row counts do not change.
5. Given the same key with a different payload or a different key for an answered
   question, then a documented conflict is returned and no state changes.
6. Given two concurrent identical submissions, then exactly one attempt and one set of
   evidence/events exists.
7. Given a single/multiple-choice question, then Phase 3 cannot create `RECALL` or
   `APPLICATION` evidence regardless of client input.
8. Given an unauthenticated, CSRF-invalid, expired, or cross-owner request, then it is
   rejected with the documented safe response and no private existence/answer data is
   leaked.
9. Given an in-progress question response, then no public payload reveals its answer
   key before or after submit.
10. Given all pinned questions have accepted attempts, then the session completes once
    and the learner can see a concept-evidence summary clearly distinguished from
    mastery/confidence.
11. Given Phase 4 is absent, then outbox events remain durably pending and no code
    pretends that knowledge state or a Today plan was produced.
12. Existing Phase 1 and Phase 2 behavior remains green.

## Rollout and rollback

- Deploy migrations before application code. V8/V9 are additive and do not modify V1–V7.
- No feature flag is required for local MVP, but the Active Goal entry point must only
  appear when the diagnostic API is deployed with its schema/seed.
- In-progress sessions remain pinned to their graph/question/policy versions across
  later curriculum publication.
- Rollback application code may leave additive tables and pending outbox rows unused.
  Database rollback is restore/forward-fix; never edit or delete applied migrations.
- Question errors are corrected with a new immutable question version and forward
  migration/curation action. Historical attempts keep their original version.

## Risks and open decisions

- The eight seeded questions are engineering-authored and require pedagogical review
  before production efficacy claims.
- Objective questions produce limited-strength evidence and cannot establish practical
  application skill.
- The seven-day expiry and one-baseline-per-goal policy are accepted v1 defaults; changing
  either after implementation requires policy/version and compatibility review.
- The multiple-choice formula is a domain-policy decision and must be accepted with
  this plan; it must not be silently changed during implementation.
- Outbox dispatch/consumption is intentionally deferred to Phase 4. Operational
  backlog monitoring is needed before production.
- Raw answer retention/deletion policy is not yet production-approved. MVP persists
  answers for reproducibility; production privacy work must define retention before
  launch.
- Flyway/MySQL compatibility warning remains and must be rechecked for V8/V9.
- No new ADR is required if implementation stays within the accepted modular-monolith,
  deterministic evaluation, session auth, and durable-outbox direction. A broker,
  external evaluator, AI authority, or different module ownership would require a new
  decision.

## Validation results

Completed on 2026-09-23:

- Backend Maven `clean verify`: PASS. 14 unit/architecture tests and 15 integration
  tests passed after the final concurrency fix.
- MySQL 8.4 migration coverage: PASS for clean V1-to-V9, historical V5-to-V7, and
  Phase 3 V7-to-V9 paths.
- Phase 3 integration coverage: PASS for create/resume, concurrent start, expiry,
  ownership concealment, answer secrecy, sequential submission, same-key replay,
  mismatch/different-key conflicts, completion, exact evidence/outbox counts, and
  concurrent duplicate submit.
- OpenAPI TypeScript generation: PASS; checked-in `schema.d.ts` regenerated.
- Frontend Prettier: PASS after formatting the Phase 3 files and two pre-existing
  formatting-only files (`RegisterPage.tsx`, `GoalSetupPage.tsx`).
- Frontend ESLint: PASS.
- Frontend Vitest: PASS, 3 files / 6 tests.
- Frontend production build: PASS, 242 modules transformed.
- `docker compose config --quiet`: PASS. Docker still reports the existing warning
  that `C:\Users\Dell\.docker\config.json` is inaccessible in this environment.
- `scripts/audit.ps1`: PASS; npm reported 0 vulnerabilities.
- `git diff --check`: PASS.

The first sandboxed Maven attempt failed before compilation because javac could not
read `C:\Users\Dell\.m2`; rerunning the same offline build with the required filesystem
permission passed. Flyway 11.7.2 continues to warn that MySQL 8.4 is newer than its
declared tested line (8.1); all clean and upgrade migrations passed on MySQL 8.4.

## Documentation updates

During implementation, update in the same change:

- Assessment model with the accepted objective scoring/evidence policy.
- API contract and checked-in OpenAPI.
- Database design and migration history.
- Module dependency diagram/contracts.
- Testing strategy with Phase 3 executable gates.
- Roadmap Phase 3 status.
- `PROJECT_CONTEXT.md` with completed facts, remaining risks, and Phase 4 handoff.
