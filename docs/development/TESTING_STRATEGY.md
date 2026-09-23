# Testing Strategy

Phase 4 adds pure projection/decay and review-interval fixtures, clean and V11-to-V13
MySQL migration coverage, idempotent outbox replay, and ownership/role API checks.
Stored mastery must remain stable while effective mastery changes with an injected clock.

Phase 5 adds pure task lifecycle fixtures, clean V1–V16 and V13–V16 MySQL migration
coverage, pinned bilingual catalog/assignment checks, owner/CSRF/idempotency/order
API checks, and frontend start/resume/retry/terminal-state checks. A self-report
completion must leave Assessment, Progress, and Review row counts unchanged. The UI
must never present the curated sequence as a personalized Today plan.

The approved Phase 6 gates require one captured `projectionAsOf` for every dynamic
snapshot input and roadmap overlay, immutable historical snapshot/decision/revision
payloads with atomic supersession, and architecture tests proving Planner uses only
other modules' public application contracts. Automatic evidence-driven replanning
remains a Phase 7 test gate.

## Test pyramid

### Unit

Pure domain tests for graph cycles/traversal, assessment scoring, evidence update, mastery/confidence, decay/review, prerequisite eligibility, priority signals, tie-breakers, task transitions, and goal completion.

### Module integration

Spring module tests with real MySQL via Testcontainers for repositories, Flyway mappings, constraints, transactions, optimistic locking, idempotency, and outbox behavior.

### API/security

Test validation, error envelope, authentication, role checks, ownership/IDOR, hidden answer keys, idempotency keys, and conflict responses.

### Frontend

Test feature behavior and loading/empty/error/success/permission states. Mock network at contract boundary. Avoid snapshots as primary assertion.

For the visual goal map, test keyboard navigation, non-color status cues, selected
node detail, blocked prerequisite paths, progressive expansion, and consistency with
the graph/progress/review/planner version stamps returned by the API. Layout rendering
tests do not replace assertions on graph semantics.

### End-to-end

Keep a small critical suite: register/login, create goal, diagnostic, receive Today plan, complete/evaluate, observe replan, change available time, and reject cross-user access.

Include opening the roadmap from Today, locating the current task and a blocked
prerequisite, then returning without losing plan context.

## Database tests

- Clean MySQL 8.4 migration from zero.
- Upgrade from latest released schema fixture.
- Constraint/index/query behavior with representative data.
- Rollback strategy is forward-fix/restore, not editing applied migration.

## AI tests

- Provider adapter contract with stubbed responses.
- JSON/schema/range/allowlist rejection.
- Timeout/retry/invalid output behavior.
- Gold evaluation dataset; AI live calls are not required for deterministic CI.

## Third-party and research spikes

- Test external libraries at SkillPath contract boundaries; do not duplicate their
  internal suite as a substitute for our behavior tests.
- Pin clocks, graph/state/policy versions, random seeds where exposed, locale, and
  numeric tolerance for algorithm comparisons.
- An adapted algorithm requires provenance plus independent fixtures for boundary,
  replay, malformed input, empty/large graph, and fallback/removal paths.
- A visual-map spike uses one shared fixture and measures keyboard/screen-reader
  behavior, non-color cues, progressive expansion, layout determinism, interaction
  latency, and version-stamp consistency.
- FSRS/BKT/IRT/learned-policy experiments run offline against versioned datasets and
  cannot write production mastery, review, or planner state.
- Dependency upgrades run license/vulnerability review and representative contract
  regression tests before merge.

## Planner fixtures

Use named scenarios with fixed clock, graph version, policy version, state, and expected explanation. Same fixture must always produce same decision.

## Quality gates

- No failing test or skipped critical suite.
- Coverage is a signal, not target gaming; core calculators and security boundaries require comprehensive branch cases.
- Mutation testing may be introduced for domain calculators after MVP foundation.
- CI uses MySQL, not H2, for persistence behavior.
- CI/dependency audit fails on unapproved license/provenance exceptions once the
  dependency inventory/SBOM gate is introduced.

## Phase 1 executable gates

- `backend/mvnw -f backend/pom.xml clean verify`: unit, ArchUnit, Spring security/API,
  Flyway, JPA validation, and full flow against Testcontainers MySQL 8.4.
- `npm --prefix frontend run format`, `lint`, `test -- --run`, and `build`.
- Regenerate OpenAPI types and require a clean diff.
- `docker compose config`, build both app images, start healthy services, and smoke
  register → template → create/replay/read goal → logout → rejected read.
- `scripts/audit.ps1`: whitespace/diff check and npm high-severity vulnerability gate.

Phase 1 does not claim coverage of the later graph, assessment, planner, or roadmap
tests listed above.

## Phase 2 executable gates

- Pure domain fixtures cover deterministic cycle paths, stable topology/traversal,
  relation-type boundaries, and derived prerequisite frontiers.
- `PhaseTwoFlowIT` applies V1–V7 to clean MySQL 8.4 and verifies the canonical graph,
  bounded public queries, learner rejection, curator replay, successor validation,
  atomic publish/audit, and concurrent one-winner publication.
- Architecture tests keep knowledge persistence private and domain code framework-free.
- Checked-in OpenAPI is regenerated into TypeScript and frontend lint/test/build remain
  regression gates even though Phase 2 adds no learner UI.

## Phase 3 executable gates

- Pure domain fixtures cover exact single choice, partial/penalized multiple choice,
  invalid selections, dimension authority, reliability caps/weights, rounding, and
  deterministic replay.
- `AssessmentMigrationUpgradeIT` proves V7-to-V9 upgrade and the eight-question seed;
  clean application integration applies V1-to-V9 on MySQL 8.4.
- `PhaseThreeFlowIT` covers create/resume, concurrent-start convergence, pinned order,
  answer-key secrecy, CSRF,
  ownership concealment, expiry persistence, incomplete/completed result semantics,
  same-key replay, hash mismatch, different-key conflict, exact evidence/outbox counts,
  and concurrent one-attempt behavior.
- Frontend tests cover objective question and completed evidence states, non-mastery
  language, and stable idempotency headers. Generated types, lint, test, and build are
  required gates.

## Localization executable gates

- `SupportedLocaleTest` covers exact, weighted, malformed, absent, and unsupported
  language resolution with deterministic English fallback.
- `LocalizationMigrationUpgradeIT` proves V9-to-V11 on MySQL 8.4, complete 1/17/8
  Vietnamese seed coverage, JSON option cardinality, and rejection of unsupported locales.
- `PhaseThreeFlowIT` proves localized goal/node/question reads, `Content-Language`, stable
  session/question/option IDs, and unsupported-language English fallback without exposing
  answer keys.
- Frontend tests cover default Vietnamese, persisted English selection, locale headers,
  `Asia/Ho_Chi_Minh`, and selection preservation while a question is re-rendered in the
  other language.

## Phase 6 executable gates

- `PlannerPolicyV1Test` fixes prerequisite direction/threshold, mastered-source
  exclusion, time-fit/no-content reasons, deterministic ordering, and the
  non-authoritative completion-candidate outcome.
- `PhaseSixFlowIT` applies V1–V20 to clean MySQL 8.4 and exercises explicit
  generation, CSRF, idempotent replay/conflict, immutable revision, owner-scoped
  history, concurrent generation/revision convergence, rollback on a forced planner
  item insert failure, paged roadmap snapshot/cursor ownership, manual-session
  conflict, hard budget, and started-task revision rejection.
  `PlannerMigrationUpgradeIT` covers V16–V19 upgrade.
- ArchUnit prevents Planner from importing other modules' API, infrastructure, or
  Learning store types. Frontend tests assert GET purity, explicit generation,
  semantic roadmap representation, 50-node page expansion, mismatched-snapshot
  refusal, locale refetch, and prerequisite direction. `PlannerRoadmapSnapshotTest` covers
  50/200-node pagination, cross-page edges, incompatible progress, and stale plans.
- Full backend `clean verify`, generated OpenAPI types, frontend format/lint/test/build,
  Compose configuration, and secret/dependency audit passed for Phase 6. The
  [visual-map spike](../research/PHASE_6_VISUAL_MAP_SPIKE.md) records the same-fixture
  library comparison and a narrow-headless Chromium layout check. Real-device and
  assistive-technology usability remain pre-public-launch checks.
- `GoalBudgetMigrationUpgradeIT` checks V19-to-V20 with an existing Goal row,
  accepting 20 and rejecting 19 at MySQL. Phase 6 API integration creates a
  20-minute Goal, generates a plan under that stored budget, and rejects 19-minute
  input; the frontend exposes the 20-minute choice. Earlier 30–180 minute Goal
  behavior remains a regression gate.
