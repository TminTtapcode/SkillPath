# Plan: DOC-001 — Context Consistency v1

## Status

`DONE`

Approved by the project owner on 2026-09-22. The owner additionally clarified that
Java Backend is the first MVP track, not the permanent product boundary; future
curricula should cover other IT specializations. The learner experience must include
a visual, explainable map of the goal graph and progress while keeping Today as the
primary action screen.

## Objective and user story

Đồng bộ bộ tài liệu Phase 0 để một lập trình viên hoặc coding agent có thể bắt đầu
Engineering Foundation và các phase domain mà không phải tự chọn giữa hai contract,
hai thứ tự triển khai, hoặc các khoảng trống policy có thể làm thay đổi hành vi.

Là project owner, tôi muốn các tài liệu authority cao và domain specification dùng
cùng vocabulary, endpoint, ownership, versioning và deterministic rules trước khi
khởi tạo implementation.

## Authoritative references

- `AGENTS.md`
- `PROJECT_CONTEXT.md`
- `DEVELOPMENT_RULES.md`
- `docs/product/MVP_SCOPE.md`
- `docs/product/USER_JOURNEYS.md`
- `docs/architecture/SYSTEM_ARCHITECTURE.md`
- `docs/architecture/MODULE_BOUNDARIES.md`
- `docs/architecture/DATABASE_DESIGN.md`
- `docs/domain/README.md`
- `docs/domain/KNOWLEDGE_GRAPH.md`
- `docs/domain/ASSESSMENT_MODEL.md`
- `docs/domain/USER_KNOWLEDGE_STATE.md`
- `docs/domain/PLANNER_ALGORITHM_V1.md`
- `docs/domain/LEARNING_TASK_MODEL.md`
- `docs/domain/ADAPTIVE_LOOP.md`
- `docs/api/API_CONTRACT.md`
- `docs/ai/AI_BOUNDARIES.md`
- `docs/plans/PHASE_ROADMAP.md`
- `docs/development/TESTING_STRATEGY.md`
- Accepted ADRs `0001`–`0006`

## Current-state evidence

- Repository contains documentation only; backend, frontend, migrations, Compose,
  wrappers, and tests have not been created.
- The local directory is not currently recognized as a Git worktree, so no local
  Git diff/baseline can be recorded. The project remote was confirmed separately as
  `https://github.com/TminTtapcode/SkillPath.git`, branch `main`, at
  `9c5f1cd5af344fd348edbe09c4b6e73f5387e250` when inspected.
- `PROJECT_CONTEXT.md` and `PHASE_ROADMAP.md` place Learning before Planner, while
  `docs/domain/README.md` places Planner before Learning.
- The central API contract uses `/knowledge/me`; the progress domain specification
  uses `/me/knowledge`.
- The Assessment AI response says version is required, but its normative JSON
  example contains no version field.
- Knowledge-state status rules leave `0.75 <= mastery < 0.80` undefined and do not
  state precedence between review status and mastery status.
- Replay/freshness rules do not define an `asOf` time, so live ingest and replay can
  diverge despite the replayability requirement.
- Planner v1 promises 1–3 ordered tasks at product level but specifies only selection
  of one result. Candidate truncation, time tolerance, signal edge cases, and plan
  packing are not fully deterministic.
- Review scheduling ownership is split between the `review` module and fields/rules
  described in `progress` without an explicit public contract.
- Knowledge node IDs are written as `UUID/long`, while database architecture selects
  `BIGINT` for MVP.

## Scope

### In scope

- Resolve documentation conflicts according to the authority order.
- Make status, replay, candidate ordering, signal normalization, time fit, task
  packing, and module ownership deterministic enough to implement and test.
- Align API paths and AI version contracts.
- Align vocabulary and MVP ID strategy.
- Record multi-track IT extensibility and the learner-facing visual roadmap as an
  explicit product/architecture direction without expanding the first MVP curriculum.
- Record accepted decisions and remaining non-blocking product/deployment choices in
  `PROJECT_CONTEXT.md`.
- Re-run a cross-document terminology, endpoint, and local-link audit.

### Out of scope

- Application code, schema migrations, OpenAPI generation, Docker, CI, or tests.
- Changing accepted architecture or ADR decisions.
- Selecting an AI provider, hosting provider, public brand, or curriculum license.
- Designing planner v2/statistical ranking.
- Initializing Git or changing remote/history.

## Design

### 1. Implementation order

Use the higher-authority sequence already stated in `PROJECT_CONTEXT.md` and the
phase roadmap:

`Knowledge → Assessment → Progress → Learning → Planner → Adaptive Loop → AI hardening`.

Update `docs/domain/README.md` to match. This is documentation alignment, not a new
architecture decision.

### 2. Canonical API paths

Treat `docs/api/API_CONTRACT.md` as the canonical transport contract and use:

```http
GET /api/v1/knowledge/me
GET /api/v1/knowledge/me/{nodeId}
GET /api/v1/knowledge/me/{nodeId}/evidence
POST /api/v1/internal/knowledge-state/rebuild
```

Domain documents may show the full `/api/v1` prefix; the central API document lists
paths relative to that prefix. Add the missing evidence endpoint to the central
contract.

### 3. Knowledge status policy

Make evaluation order explicit:

1. `UNKNOWN`: no evidence.
2. `REVIEW_DUE`: the concept previously reached `MASTERED` and its review is due or
   effective mastery is below the retention threshold `0.75`.
3. `MASTERED`: mastery `>= 0.80`, confidence `>= 0.60`, and review is not due.
4. `PROVISIONAL`: mastery `>= 0.80`, confidence `< 0.60`.
5. `LEARNING`: all remaining cases with evidence, including
   `0.75 <= mastery < 0.80`.

Stored mastery and effective mastery must be named explicitly in each rule. Thresholds
remain policy-versioned. This preserves the documented acquisition threshold (`0.80`)
and retention threshold (`0.75`) instead of silently collapsing them.

### 4. Replay clock and numerical reproducibility

- Add `projectionAsOf` to rebuild/planning snapshots.
- Freshness and recency are pure functions of `observedAt`, `projectionAsOf`, and the
  policy version.
- Live ingest uses the transaction's captured server instant as `projectionAsOf`.
- Replay of the same ordered ledger, policy, and `projectionAsOf` must produce the
  same result.
- Define evidence ordering as `(observedAt, evidenceId)`.
- Persist decimal scores at scale 4, calculate at documented higher precision, and
  compare replay results using a policy-owned tolerance. Initial proposed tolerance:
  `0.0001`.

### 5. Planner determinism and Today-plan composition

- Candidate generation deduplicates by `(knowledgeNodeId, candidateReason)` and uses
  a stable pre-limit order: urgent review/remedial first, then goal relevance
  descending, terminal unlock value descending, topological order, stable node ID.
- Candidate limit stays policy-configurable; proposed default remains `100`.
- Every normalized signal is clamped to `[0,1]`.
- `prerequisiteValue` is `0` when the candidate-set denominator is `0`.
- `learningROI` documents non-zero normalized effort and clamps the computed signal.
- Time tolerance becomes policy config. Proposed MVP default: `0` minutes; only
  declared task variants/checkpoints can fit a smaller budget.
- Build a Today plan by repeatedly taking the highest-ranked compatible task while
  updating remaining minutes, up to 3 items. Stop when no safe time-fit item exists.
- Do not duplicate the same task/template instance in a plan. Preserve a
  pedagogically declared sequence (`LEARN → PRACTICE/APPLICATION → RECALL/CHECK`)
  when its complete bounded sequence fits; otherwise choose a standalone valid task.
- Persist one planning snapshot and the ordered candidate/selection decisions used
  for every revision.

### 6. Module ownership

- `goal` owns goal templates and user-goal lifecycle.
- `knowledge` owns graph versions, nodes, relations, and the versioned mapping from a
  goal-template ID to goal knowledge requirements.
- `review` owns review interval advancement and `review_schedules`/
  `review_attempts`.
- `progress` owns evidence and knowledge projections and may carry
  `nextReviewAt` only as a derived snapshot supplied through the public review
  contract; it must not independently advance intervals.
- Cross-module references use IDs/contracts, never persistence entities.

### 7. IDs and AI versions

- Replace `UUID/long` ambiguity with `BIGINT` internal IDs for MVP, matching database
  architecture. Public APIs expose IDs as opaque values and do not promise their
  numeric nature.
- Add `evaluatorVersion` to the Assessment AI response example and state whether
  provider/model identifiers are adapter audit metadata rather than learner-visible
  response fields.
- Validate returned evaluator version against the request contract before evidence
  creation.

### 8. Authentication decision

Promote the existing web-MVP default to the Phase 1 decision: secure HTTP-only cookie
session with `Secure` in non-local environments, `SameSite`, CSRF protection, session
rotation after authentication, and server-side logout/revocation. Token-based auth
would require deployment evidence and an approved plan/ADR change.

## Expected files

- `docs/plans/CONTEXT_CONSISTENCY_V1.md`
- `PROJECT_CONTEXT.md`
- `docs/product/PRODUCT_VISION.md`
- `docs/product/MVP_SCOPE.md`
- `docs/product/USER_JOURNEYS.md`
- `docs/domain/README.md`
- `docs/domain/KNOWLEDGE_GRAPH.md`
- `docs/domain/ASSESSMENT_MODEL.md`
- `docs/domain/USER_KNOWLEDGE_STATE.md`
- `docs/domain/PLANNER_ALGORITHM_V1.md`
- `docs/domain/LEARNING_TASK_MODEL.md`
- `docs/domain/ADAPTIVE_LOOP.md`
- `docs/architecture/SYSTEM_ARCHITECTURE.md`
- `docs/architecture/MODULE_BOUNDARIES.md`
- `docs/architecture/DATABASE_DESIGN.md`
- `docs/api/API_CONTRACT.md`
- `docs/ai/AI_BOUNDARIES.md`
- `docs/plans/PHASE_ROADMAP.md`
- `docs/development/TESTING_STRATEGY.md`
- `docs/adr/0006-web-session-authentication.md`

No code, dependency, schema, or runtime configuration file is expected to change.

## Tests

Documentation validation:

- All relative Markdown links resolve.
- Endpoint inventory contains no conflicting path for the same operation.
- Module/table ownership terms are consistent across architecture and domain docs.
- Threshold boundary table covers no evidence, `0.7499`, `0.75`, `0.7999`, `0.80`,
  low/high confidence, previously-mastered review due, and retention decay.
- Planner examples cover zero denominator, more than 100 candidates, exact budget,
  no fitting task, 1/2/3-item plans, stable ties, and repeated failures.
- AI response example satisfies its own required schema/version rules.
- Search for superseded terms and paths returns no normative conflicts.

These are specification scenarios for the later automated tests; no application test
suite exists yet.

## Acceptance criteria

- Given the documentation set, there is exactly one approved implementation order.
- Given a progress query, all normative documents identify the same HTTP path.
- Given any mastery/confidence boundary, exactly one status rule applies.
- Given the same evidence ledger, policy, and `projectionAsOf`, replay behavior is
  fully specified and deterministic.
- Given planner candidate/time edge cases, ordering, clamping, tolerance, and plan
  composition have defined outcomes.
- Given a review update, one module owns interval mutation and other modules consume
  it through contracts.
- Given an AI evaluation response, required version data is present and validated.
- `PROJECT_CONTEXT.md` accurately records resolved decisions and remaining open
  decisions.
- No accepted ADR is contradicted and no implementation artifact is introduced.

## Rollout and rollback

This is a pre-implementation specification change. Apply all normative document
updates in one coherent change. Rollback is a documentation revert; no data migration
or backward-compatible runtime rollout is involved.

After approval, domain behavior introduced by these clarifications becomes v1 policy.
Any later behavioral change must be versioned and may require an ADR.

## Risks and open decisions

- The owner approved knowledge-status hysteresis, stable candidate limiting,
  multi-task packing, zero-minute time tolerance, and cookie-session authentication
  on 2026-09-22. They are now normative v1 behavior; future changes require policy
  versioning and, where architectural/security impact exists, an ADR.
- Score precision/tolerance must be verified against Java `BigDecimal` and MySQL
  `DECIMAL(5,4)` during implementation; changing persisted scale later requires a
  forward migration.
- The next IT specialization after Java Backend remains a product decision based on
  MVP evidence. It does not block the curriculum-neutral foundation.
- Visual graph layout technology remains an implementation-plan choice; the contract,
  accessibility, progressive-disclosure, and source-of-truth rules are fixed here.

## Validation results

- Full documentation inventory reviewed before editing: 26 files under `docs/`, plus
  root context, development rules, agent rules, and README. At this plan's completion,
  the set had 28 files under `docs/` after adding this plan and ADR-0006; later plans
  may legitimately add further documentation.
- No implementation/build baseline is available because the repository currently
  contains documentation only.
- Git diff validation is unavailable until the local directory is initialized or
  cloned as a Git worktree.
- PowerShell local Markdown-link audit: `PASS` across 32 Markdown files.
- Superseded normative-term audit (`/api/v1/me/knowledge`, `UUID/long`, obsolete task
  activity names, unresolved auth-choice wording): `PASS`, no matches outside the
  historical evidence in this plan.
- Canonical progress API audit: `PASS`.
- Planner policy coverage audit for 1–3 items, stable limit, and time tolerance:
  `PASS`.
- Knowledge-state boundary/replay audit for the 0.75/0.80 hysteresis,
  `projectionAsOf`, and `0.0001` tolerance: `PASS`.
- Accepted authentication ADR presence audit: `PASS`.

## Documentation updates

Completed:

1. Applied approved decisions to product, architecture, domain, API, AI, testing, and
   roadmap documents.
2. Added multi-track IT extensibility without expanding the first MVP curriculum.
3. Added an accessible, progressively disclosed visual goal-map read model while
   preserving Today as the primary screen.
4. Recorded secure web-session authentication in ADR-0006.
5. Updated `PROJECT_CONTEXT.md` and completed the consistency audits above.
