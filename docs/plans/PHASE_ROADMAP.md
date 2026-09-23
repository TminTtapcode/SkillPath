# Delivery Roadmap

Every phase must deliver a testable vertical outcome. Phase numbers are not permission to implement all listed work without a task plan.

## Phase 0 — Definition

- Product vision, MVP, journeys.
- Architecture, module boundaries, data model.
- Core domain specifications and ADRs.
- Agent/development/testing rules.
- Open-source adoption register and license/provenance workflow.

Exit: documents are consistent and owner accepts open decisions.

## Phase 1 — Engineering foundation

Status: completed locally on 2026-09-23; validation evidence is recorded in
`PHASE_1_ENGINEERING_FOUNDATION.md`.

- Repository, Maven/Node wrappers, formatting/lint.
- MySQL Compose, Workbench connection guide, Flyway baseline.
- Spring Boot and React skeletons.
- Authentication baseline and CI.
- Dependency inventory/licensing baseline and a place for future third-party notices/SBOM output.
- Thin goal create/read vertical slice.
- Backend/frontend Dockerfiles after the slice.

Exit: new developer can start MySQL, run apps/tests, and complete goal slice.

## Phase 2 — Knowledge system

Status: completed locally on 2026-09-23; implementation and validation evidence are in
`PHASE_2_KNOWLEDGE_SYSTEM.md`.

- Graph/version entities and migrations.
- Relation validator, traversal, goal subgraph.
- Curated Java Backend seed/import.
- Admin validation/publish path.
- Read-only published goal-graph query suitable for later visual projection.
- Derived prerequisite-frontier fixtures (`innerFringe`, `outerFringe`, `blocked`) without persisting a second source of truth or enumerating all knowledge states.

Exit: versioned acyclic goal graph can be published and queried.

## Phase 3 — Assessment

Status: completed locally on 2026-09-23; implementation and validation evidence are in
`PHASE_3_ASSESSMENT.md`.

- Questions/mappings/session/attempts.
- Objective evaluation and evidence contract.
- Diagnostic journey; AI adapter stub only if needed.

Exit: diagnostic produces concept-level evidence exactly once.

### Cross-cutting localization milestone L10

Status: completed locally on 2026-09-23; implementation and validation evidence are in
`LOCALIZATION_V1.md`.

- Vietnamese/English interface and canonical learner content overlays.
- Browser-persisted language choice using `Accept-Language`/`Content-Language`.
- Ho Chi Minh City timezone default for new registration and goal forms.

Exit: the current learner journey works in both languages without changing domain IDs,
diagnostic progress, scoring, or evidence.

## Phase 4 — Progress and review

Status: completed locally on 2026-09-23; implementation and validation evidence are in
`PHASE_4_PROGRESS_REVIEW.md`.

- Evidence ledger, state projection, mastery/confidence.
- Misconceptions, decay, review schedule, replay/rebuild.
- Capture versioned review events sufficient for later offline scheduler evaluation; keep deterministic interval policy v1.

Exit: deterministic projection passes replay fixtures.

## Phase 5 — Learning system

Status: P5.0–P5.5 implemented locally on 2026-09-23; scope, policy boundary, and
validation evidence are in `PHASE_5_LEARNING_SYSTEM.md`.

- Curated resources, templates/variants, assignments, lifecycle.
- Learner-selected study-session UI and completion flow; personalized Today plan
  selection remains with Phase 6.

Exit: user can execute a bounded learn/practice/recall sequence.

## Phase 6 — Planner

Status: `DONE` for the owner-approved Phase 6 slice on 2026-09-23; validation is
recorded in `PHASE_6_PLANNER.md`. The [visual-map spike](../research/PHASE_6_VISUAL_MAP_SPIKE.md)
supports retaining dependency-free SVG plus a semantic list for the current bound.
A graph-rendering dependency still needs separate approval.

- Candidate generation, prerequisite gate, signals, scoring, tie-breaks.
- Decisions, explanations, alternatives, plan revisions.
- Learner roadmap projection combining graph, progress, review, and active plan versions.
- Approved React Flow versus Cytoscape.js spike using the same bounded roadmap fixture before selecting a dependency.
- Accessible visual goal-map UI with progressive disclosure for large graphs.

Exit: fixed fixtures produce safe deterministic Today plans fitting time, and the
visual map explains the same current/ready/blocked path from the same snapshots.

## Phase 7 — Adaptive loop

Status: `DONE` on 2026-09-23. Core orchestration, task check API, evidence projection, and replan worker integrated and enabled. Phase 8 will focus on AI evaluation.

- Evidence → state → replan orchestration.
- Failure/retry/idempotency/outbox paths.
- Missed day and time override journeys.

Exit: MVP completion scenario works end to end.

## Phase 8 — Onboarding & Pedagogical Pivot

Status: `DRAFT` (Awaiting P7 completion for domain changes. P8.0 and P8.1 UI prototyping approved on 2026-09-23).

- **P8.0 Pedagogical Contract**: Define architectural boundaries (Practice != Evidence, Review = Planner Candidate).
- **P8.1 Onboarding**: Interactive Generic Web Architecture mental map.
- **P8.2 Learning Content Model**: `ResourceVersion` and `TaskResource` entities.
- **P8.3 Practice**: Formative practice flow prior to assessment.
- **P8.4 Review-aware Planner**: Budget-aware priority policy combining `REVIEW_DUE` and `NEW` learning.
- **P8.5 UX Integration**: Multi-track goal switching and unified `Learn + Practice + Check` task view.
- **P8.6 E2E Validation**: Integration tests for the new pedagogical loop.

Exit: The learner can complete a full cycle from onboarding to a mixed review/new learning plan using real resources and formative practice.

## Phase 9 — AI and product hardening

- Rubric-based evaluation, question/exercise/explanation generation.
- Gold dataset and cost/rate controls.
- Offline evidence-based evaluation of FSRS/BKT/IRT or learned ranking only when enough representative data exists; adoption requires a separate versioned policy plan/ADR.
- UX, observability, security/performance audit, deployment.

Exit: release gates in `MVP_SCOPE.md` pass.

## Open-source gate for every phase

Before using external code, content, model, dataset, or service, follow
`docs/research/OPEN_SOURCE_ADOPTION.md`: verify the exact revision and license, record
provenance and transitive obligations, assess security/maintenance/exit strategy, and
obtain approval for material dependencies or domain-policy changes. A public GitHub
repository and a persuasive README are not sufficient approval.
