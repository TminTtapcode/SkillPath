# Testing Strategy

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
