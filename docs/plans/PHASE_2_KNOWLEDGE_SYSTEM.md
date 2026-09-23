# Plan: P2 — Versioned Knowledge System

## Status

`DONE — owner approved and checkpoints P2.0–P2.5 completed on 2026-09-23`

## Objective and user story

Deliver the curriculum-neutral knowledge backbone for SkillPath. A learner or client
can query the currently published graph for an active goal template, while an
authorized curriculum curator can validate and atomically publish a versioned DAG.

As a learner, I can retrieve a stable, explainable prerequisite graph for my selected
goal so later assessment, progress, planner, and visual-roadmap phases can refer to the
same versioned knowledge IDs.

As a curator, I can validate a draft, see deterministic violations, and publish it
without corrupting or partially replacing the currently published curriculum.

Phase 2 ends at a read-only published graph. It does not infer personal mastery, build
the learner roadmap UI, or select learning tasks.

## Authoritative references

- `AGENTS.md`
- `PROJECT_CONTEXT.md`
- `DEVELOPMENT_RULES.md`
- `docs/adr/0001-modular-monolith.md`
- `docs/adr/0002-mysql-flyway-workbench.md`
- `docs/adr/0003-deterministic-planner.md`
- `docs/adr/0004-ai-boundary.md`
- `docs/adr/0006-web-session-authentication.md`
- `docs/architecture/SYSTEM_ARCHITECTURE.md`
- `docs/architecture/MODULE_BOUNDARIES.md`
- `docs/architecture/DATABASE_DESIGN.md`
- `docs/domain/KNOWLEDGE_GRAPH.md`
- `docs/domain/ASSESSMENT_MODEL.md`
- `docs/domain/USER_KNOWLEDGE_STATE.md`
- `docs/domain/PLANNER_ALGORITHM_V1.md`
- `docs/api/API_CONTRACT.md`
- `docs/api/openapi-v1.yaml`
- `docs/development/TESTING_STRATEGY.md`
- `docs/development/DEVELOPMENT_WORKFLOW.md`
- `docs/research/OPEN_SOURCE_ADOPTION.md`
- `docs/plans/PHASE_ROADMAP.md`

## Current-state evidence

Discovery performed on 2026-09-23 against `main` at commit `210b36c`.

### Repository and implementation

- Phase 1 supplies a Java 21/Spring Boot modular monolith, React client, MySQL 8.4,
  Flyway V1–V5, cookie-session authentication, CSRF, and role values `LEARNER`,
  `ADMIN`, and `CURATOR`.
- Only `auth`, `user`, `goal`, and `shared` have implementation packages. There is no
  `knowledge` package, graph table, graph endpoint, or graph test yet.
- `goal_templates` contains the active `JAVA_BACKEND_INTERN` template with ID `1`.
- Registration grants only `LEARNER`. The current security chain authenticates all
  non-public routes but does not yet enforce `ADMIN`/`CURATOR` route authorization.
- `AuthenticatedUser` already maps stored roles to Spring authorities named
  `ROLE_<role>`.
- Current architecture tests cover controller-to-persistence and domain-to-framework
  boundaries, but not cross-module private-package imports.
- The checked-in OpenAPI document contains only Phase 1 operations. Phase 2 contracts
  and generated TypeScript types do not exist.
- Graph UI dependencies are absent, as required. `JGraphT` remains reference-only;
  small deterministic DAG functions are sufficient for this phase.

### Baseline validation

- Backend: Maven wrapper invocation through PowerShell failed before Maven started
  (`Cannot index into a null array`). Direct Maven 3.9.16 with the existing local
  repository then ran `clean verify` successfully: 5 unit/architecture tests and 5
  MySQL 8.4 integration tests passed.
- Frontend: `npm --prefix frontend run lint`, `test -- --run`, and `build` passed;
  2 test files and 3 tests passed.
- Existing known warning remains: Flyway 11.7.2 reports MySQL 8.4 newer than its latest
  tested MySQL line (8.1), although clean migrations pass.

### Preserved unrelated work

The working tree already contains owner changes to `PROJECT_CONTEXT.md` and frontend
design-system files, plus untracked `frontend/.agents/`. Phase 2 planning does not
modify, stage, delete, or claim those changes. Implementation must preserve them and
must not commit `frontend/.agents/` as application source.

### Context clarification

`MODULE_BOUNDARIES.md` draws `goal -> knowledge contract`, while
`KNOWLEDGE_GRAPH.md` says goal-template identity comes through the public goal
contract. This plan reconciles the statements without a cyclic dependency:

- the goal-owned HTTP adapter validates/exposes the active goal-template identity and
  calls the public `knowledge` query contract for the goal-graph route;
- the knowledge module stores only the opaque goal-template ID in its mapping and does
  not import goal entities, repositories, controllers, or private implementation;
- a database foreign key may enforce that the referenced goal-template row exists.

If the owner instead wants `knowledge -> goal` as a runtime code dependency, update
`MODULE_BOUNDARIES.md` first; do not silently implement both directions.

## Scope

### In scope

- A new `knowledge` module with domain, application, API, and persistence layers.
- Version, node, relation, goal mapping, and lifecycle-audit schema through new
  forward-only Flyway migrations.
- Deterministic validation of ranges, lifecycle, duplicate/dangling edges, cross-version
  edges, self-loops, prerequisite cycles, terminal reachability, and stable topology.
- Direct/transitive prerequisite and dependent traversal, stable topological ordering,
  node lookup, and goal-subgraph queries.
- A canonical, project-authored Java Backend Internship graph and goal mapping.
- Curator/admin validate and publish commands with role enforcement, CSRF, concurrency
  control, atomic retirement/publication, and durable successful-transition audit.
- Published-only read endpoints, including a bounded goal-graph response suitable for
  later visual projection.
- Pure fixtures for `innerFringe`, `outerFringe`, and `blocked`; these fixtures consume
  synthetic graph/state/task-availability inputs and persist no derived frontier.
- OpenAPI, generated frontend types, documentation, migration, domain, integration,
  API/security, and architecture tests.

### Out of scope

- Learner mastery/evidence, assessment sessions, questions, review schedules, planner
  scoring, learning resources/tasks, and personalized roadmap composition.
- React graph UI, admin UI, and selection of React Flow or Cytoscape.js. That remains a
  bounded Phase 6 spike against this API contract.
- Browser-based role administration, self-service role elevation, or a seeded admin
  password/account.
- General graph CRUD/import HTTP APIs. Canonical Phase 2 curriculum is supplied by an
  auditable Flyway data migration; later authoring/import needs its own approved plan.
- AI-generated curriculum, AI validation, Neo4j, Redis, queues, or microservices.
- Copying/adapting code or curriculum data from the open-source research register.
- Migration of historical learner state; no knowledge evidence exists before Phase 2.

## Design

### Checkpoint P2.0 — Contract lock and clean boundary

- Approve this plan and the proposed seed outline before changing code/schema.
- Resolve the dependency-direction clarification above in documentation if the owner
  chooses a different direction.
- Define public immutable application DTOs/contracts in `knowledge.application`;
  transport and persistence types remain private to their layers.
- Extend ArchUnit rules so no module imports another module's API controller,
  persistence, repository, or private infrastructure package.

Gate: approved plan, no unresolved authority conflict, baseline recorded, and no
unrelated owner changes overwritten.

### Checkpoint P2.1 — Flyway graph schema

Add `V6__create_knowledge_graph_schema.sql` with the following knowledge-owned tables:

1. `knowledge_graph_versions`
   - `id BIGINT` primary key;
   - `curriculum_key VARCHAR(100)` and `version_label VARCHAR(50)`;
   - lifecycle `status`: `DRAFT`, `VALIDATED`, `PUBLISHED`, `RETIRED`;
   - optimistic `version BIGINT`;
   - validation/publication/retirement actor and UTC timestamp columns;
   - created/updated UTC timestamps;
   - unique `(curriculum_key, version_label)`;
   - a nullable generated publication key with a unique constraint so MySQL enforces
     at most one `PUBLISHED` version per curriculum.

2. `knowledge_nodes`
   - opaque `BIGINT` ID and owning `graph_version_id`;
   - version-local unique `slug`, name, learning-outcome description, category;
   - `difficulty` 1–5, positive `estimated_minutes`;
   - status `DRAFT`, `ACTIVE`, `DEPRECATED`, or `ARCHIVED`;
   - JSON metadata for non-authoritative extensions only;
   - composite uniqueness needed for same-version foreign keys.

3. `knowledge_relations`
   - owning version, source node, target node, relation type, `DECIMAL(5,4)` strength,
     status, rationale, timestamps;
   - composite foreign keys guarantee both endpoints belong to the same version;
   - SQL checks reject self-loops and out-of-range strength;
   - a nullable generated active marker plus unique constraint rejects duplicate active
     `(version, source, target, type)` relations while permitting historical deprecated
     rows.

4. `goal_knowledge`
   - graph version, opaque goal-template ID, node ID;
   - `DECIMAL(5,4)` relevance and required mastery, terminal flag;
   - unique `(graph_version_id, goal_template_id, knowledge_node_id)`;
   - same-version node FK and goal-template existence FK;
   - indexes for version/goal membership and terminal lookup.

5. `knowledge_version_events`
   - append-only successful lifecycle events with version ID, event type, actor user ID,
     correlation ID, from/to status, UTC timestamp, and non-sensitive summary JSON;
   - no graph payload, password, session, or private learner data.

Hibernate remains `validate`; it must never create these tables. V1–V5 are not edited.
Migration tests cover clean creation, V5-to-V6/V7 upgrade, checks, FKs, indexes, and
generated uniqueness behavior on MySQL 8.4.

Gate: clean and upgrade migrations pass; invalid same-version/cross-version rows and a
second published curriculum version are rejected by the database where specified.

### Checkpoint P2.2 — Pure deterministic graph domain

Implement domain-owned immutable types and pure algorithms without Spring, JPA, AI,
clock reads, or third-party graph libraries:

- `GraphVersion`, `KnowledgeNode`, `KnowledgeRelation`, `GoalKnowledge`;
- explicit lifecycle transitions `DRAFT -> VALIDATED -> PUBLISHED -> RETIRED`;
- `GraphValidator` returning stable violation codes and ordered details;
- iterative traversal for direct/transitive prerequisites and dependents;
- deterministic Kahn topological sort, breaking ties by slug then opaque node ID;
- cycle reporting with a deterministic closed path such as `A -> B -> C -> A`;
- goal-subgraph closure and terminal-to-root reachability checks;
- bounded work guards for malformed/oversized input.

Only active `PREREQUISITE` relations affect DAG validation, topological order, blocking,
or prerequisite traversal. `PART_OF`, `RELATED`, and `APPLIED_IN` remain queryable but
never become prerequisite gates.

Published and retired versions are immutable. A validated version cannot be edited;
content correction creates a new draft/version. Validation failure leaves the version
in `DRAFT`; publication failure leaves it `VALIDATED`.

Frontier semantics remain derived test/contract fixtures:

- `innerFringe`: mastered nodes adjacent to a not-yet-mastered dependent;
- `outerFringe`: not-yet-mastered nodes whose hard prerequisites satisfy the supplied
  policy threshold and for which the supplied task predicate is true;
- `blocked`: not-yet-mastered nodes with an unsatisfied hard prerequisite.

The graph module does not own mastery thresholds or task availability. Fixtures pass
these values explicitly and do not create a production table or second truth source.

Gate: comprehensive unit fixtures pass for empty/single/branching/disconnected/large
graphs, stable ordering, duplicate edges, dangling nodes, cross-version edges, cycles,
and all relation types.

### Checkpoint P2.3 — Persistence and query contracts

- Add JPA entities/repositories only under `knowledge.infrastructure.persistence`.
- Map persistence entities to immutable application/domain DTOs before crossing a
  package boundary.
- Expose public application contracts for:
  - published node lookup;
  - direct/transitive prerequisite and dependent lookup;
  - version-stamped goal subgraph;
  - stable topological order;
  - internal validation/publication commands.
- Query only published versions on learner/public routes. Draft/validated/retired data
  is visible only to authorized curator operations or history-specific internal calls.
- Avoid N+1 traversal: load the bounded version/goal node and edge set through explicit
  repository queries, then traverse in memory.
- Apply explicit maximums (`limit <= 200`, `depth <= 10` initially) and deterministic
  ordering. Opaque cursors carry graph version and last stable key; stale/mismatched
  cursors are rejected rather than silently mixing versions.

Gate: MySQL integration tests prove mappings, query plans/index use for representative
fixtures, published-only filtering, stable cursors, and no persistence entity escape.

### Checkpoint P2.4 — Curator validation and atomic publication

Admin commands:

```http
POST /api/v1/admin/knowledge/versions/{versionId}/validate
POST /api/v1/admin/knowledge/versions/{versionId}/publish
```

- Permit only `ROLE_CURATOR` or `ROLE_ADMIN`; learner and anonymous requests receive
  `403` and `401` respectively.
- Keep CSRF protection for both commands.
- Validation returns a deterministic report. Valid input transitions to `VALIDATED`;
  invalid input remains `DRAFT` and returns the violation list without mutation.
- Publication locks the curriculum's versions, revalidates the persisted snapshot,
  retires the previous published version, publishes the requested validated version,
  and records its event in one transaction.
- Publishing the already-current version is an idempotent `200` no-op response.
- Two concurrent publication attempts for the same curriculum yield one winner; the
  loser remains `VALIDATED` and receives `409 GRAPH_PUBLICATION_CONFLICT`.
- No request accepts an actor/user ID; the audit actor comes from the authenticated
  principal. Rejected attempts emit structured security logs with correlation ID;
  successful transitions also receive durable audit rows.
- Role provisioning is deliberately outside the public API. Production operator/IAM
  provisioning requires a later security plan; Phase 2 tests create role assignments
  as fixtures.

Gate: lifecycle, rollback, retry, concurrency, role, CSRF, and audit tests pass against
real MySQL.

### Checkpoint P2.5 — Canonical seed, public API, and handoff

Add `V7__seed_java_backend_knowledge_graph.sql` containing project-authored curriculum
data only. No external curriculum text is copied. Proposed bounded seed:

- foundations: programming fundamentals, Git, command line, HTTP, SQL;
- Java: Java language, object-oriented design, collections/generics, exceptions,
  testing fundamentals;
- backend: REST API design, Spring Boot, persistence/JPA, validation/error handling,
  authentication/authorization, integration testing, Docker/deployment basics;
- explicit prerequisite relations and a weighted mapping to
  `JAVA_BACKEND_INTERN`, with at least one terminal outcome.

Exact node names, outcomes, edges, weights, required mastery, and terminal flags must
be reviewed as a plan attachment/diff before V7 is accepted. The canonical seed may be
inserted as already `PUBLISHED` only after the same validator fixture proves it valid;
future replacements must use the runtime validate/publish lifecycle.

Public queries:

```http
GET /api/v1/knowledge/nodes/{nodeId}
GET /api/v1/knowledge/nodes/{nodeId}/prerequisites?transitive=false&limit=100
GET /api/v1/knowledge/nodes/{nodeId}/dependents?transitive=false&limit=100
GET /api/v1/goal-templates/{goalTemplateId}/graph?anchorNodeId=&depth=2&limit=100&cursor=
```

Published curriculum contains no learner-private data, so the proposed Phase 2 reads
are public like `GET /goal-templates`; only active goal-template mappings are exposed.
If the owner prefers authenticated reads, that security choice must be changed in this
plan and OpenAPI before implementation.

The graph response includes:

- `graphVersionId`, `curriculumKey`, `versionLabel`, and `publishedAt`;
- stable node/edge IDs and all display/domain fields needed by later projection;
- per-node goal relevance, required mastery, and terminal marker;
- deterministic nodes/edges, `truncated`, and opaque `nextCursor`/expansion metadata;
- no mastery, readiness, plan overlay, or inferred goal completion.

Errors use the existing problem envelope and stable codes including
`KNOWLEDGE_NODE_NOT_FOUND`, `PUBLISHED_GOAL_GRAPH_NOT_FOUND`,
`INVALID_GRAPH_CURSOR`, `GRAPH_VERSION_STATE_CONFLICT`, and
`GRAPH_PUBLICATION_CONFLICT`.

Update checked-in OpenAPI and regenerate `frontend/src/shared/api/schema.d.ts`; add no
Phase 2 learner UI. Complete documentation/context and record validation evidence.

Gate: a clean database exposes the seeded published Java graph; public queries return
only that immutable version; authorized test curators can validate/publish a successor;
the old version retires atomically; all Phase 2 and regression gates pass.

## API compatibility and security summary

- All changes are additive under `/api/v1`; Phase 1 response shapes remain unchanged.
- IDs remain opaque strings in JSON even though MySQL stores `BIGINT`.
- Read endpoints never reveal draft curriculum or personal state.
- Admin endpoints derive identity/roles from the authenticated session and require
  CSRF. They do not accept user IDs or role claims in request bodies.
- Bounded query parameters prevent unbounded graph traversal and response amplification.
- Publication uses optimistic version checks plus transactional locking and database
  uniqueness; validation always runs on the exact persisted snapshot being published.
- AI has no role in schema, seed, validation, publication, or traversal.

## Expected files

Expected additions:

- `backend/src/main/resources/db/migration/V6__create_knowledge_graph_schema.sql`
- `backend/src/main/resources/db/migration/V7__seed_java_backend_knowledge_graph.sql`
- `backend/src/main/java/com/skillpath/knowledge/api/*`
- `backend/src/main/java/com/skillpath/knowledge/application/*`
- `backend/src/main/java/com/skillpath/knowledge/domain/*`
- `backend/src/main/java/com/skillpath/knowledge/infrastructure/persistence/*`
- `backend/src/test/java/com/skillpath/knowledge/domain/*`
- `backend/src/test/java/com/skillpath/knowledge/*IT.java`
- optional focused test fixtures under `backend/src/test/resources/knowledge/`

Expected modifications:

- `backend/src/main/java/com/skillpath/auth/infrastructure/security/SecurityConfig.java`
- `backend/src/main/java/com/skillpath/goal/api/GoalController.java` or a goal-owned
  graph HTTP adapter delegating to the knowledge public contract
- `backend/src/test/java/com/skillpath/ArchitectureTest.java`
- `docs/api/openapi-v1.yaml`
- `frontend/src/shared/api/schema.d.ts` (generated)
- `docs/api/API_CONTRACT.md`
- `docs/architecture/DATABASE_DESIGN.md`
- `docs/architecture/MODULE_BOUNDARIES.md` if clarification is accepted
- `docs/domain/KNOWLEDGE_GRAPH.md`
- `docs/development/TESTING_STRATEGY.md`
- `docs/plans/PHASE_ROADMAP.md`
- `PROJECT_CONTEXT.md`
- this plan's status and validation section

No new Maven/npm dependency, frontend feature, or Docker service is expected. Any
deviation requires reporting and, for material dependencies or architecture/security
changes, owner approval before continuing.

## Tests

### Unit/domain

- lifecycle transitions and immutability;
- all range/status/mapping invariants;
- duplicate, dangling, self-loop, and cross-version relations;
- deterministic cycle path and stable topological order;
- direct/transitive prerequisites and dependents;
- goal closure, root/terminal reachability, deprecated/archived behavior;
- non-prerequisite relations never block;
- empty, branching, disconnected, deep, wide, and bounded-large graph fixtures;
- derived `innerFringe`, `outerFringe`, and `blocked` fixtures without persistence.

### MySQL integration/migration

- clean V1–V7 migration and V5-to-V7 upgrade path;
- Hibernate schema validation;
- every FK/check/unique/generated publication constraint;
- repository mappings and representative indexed queries;
- atomic retire/publish rollback when any operation fails;
- concurrent publish winner/loser behavior;
- successful lifecycle audit rows and no partial audit/state.

### API/security

- published node, prerequisite, dependent, and goal-graph schemas;
- deterministic pagination/cursor, invalid/stale cursor, depth/limit validation;
- unpublished/retired data concealment;
- anonymous and learner rejection for admin commands;
- curator/admin success, CSRF rejection, stale state conflict;
- validation report with cycle path and no state mutation;
- publish retry idempotency and publication conflict;
- existing Phase 1 auth/goal behavior remains green.

### Contract/frontend

- OpenAPI representative schema tests and generated TypeScript drift check;
- existing frontend lint/tests/build only; no graph UI test is added in Phase 2.

### Architecture/audit

- no controller-to-repository access;
- domain remains framework-free;
- no module imports another module's persistence/controller/private implementation;
- no external curriculum/code copied; no dependency/notice changes expected;
- secret, diff, migration, and generated-artifact audit.

## Acceptance criteria

1. Given the canonical Java graph on a clean MySQL database, when migrations finish,
   then exactly one published `JAVA_BACKEND` version exists and its goal mapping is
   queryable for `JAVA_BACKEND_INTERN`.
2. Given `A -> B -> C -> A`, when a curator validates the draft, then the response
   reports a deterministic closed cycle path and the version remains `DRAFT`.
3. Given a relation whose endpoints belong to different versions, when it is persisted
   or validated, then it is rejected without partial data.
4. Given `RELATED`, `PART_OF`, or `APPLIED_IN`, when prerequisites/topology/frontiers
   are calculated, then those relations never block a node.
5. Given a valid `VALIDATED` successor and an existing published version, when an
   authorized curator publishes, then the successor becomes `PUBLISHED`, the previous
   version becomes `RETIRED`, and the audit event commits atomically.
6. Given two concurrent successor publications, when both execute, then only one can
   become published; the loser stays validated and receives a conflict.
7. Given a publication retry for the already-current version, when repeated, then it
   returns the same published result without duplicate lifecycle mutation.
8. Given an anonymous/learner caller, when an admin validation/publication route is
   called, then it returns `401`/`403` and changes no graph state.
9. Given a public graph query, when draft and retired versions also exist, then only the
   current published graph is returned with its version stamp.
10. Given a query exceeding bounds or using a cursor from another graph version, when
    processed, then it is rejected deterministically and performs no unbounded scan.
11. Given the same published version and query, when repeated, then node/edge/topology
    order and opaque cursor behavior are stable.
12. Given synthetic mastery/task availability, when frontier fixtures run, then
    `innerFringe`, `outerFringe`, and `blocked` match documented semantics and no
    frontier state is persisted.
13. Given existing Phase 1 users/goals, when V6/V7 are applied, then all Phase 1 APIs
    and tests remain backward compatible.

## Rollout and rollback

- V6/V7 are additive forward migrations; no existing table or response is removed.
- Deploy migration before code only if the old application safely ignores the new
  tables. Deploying code before migration is not supported because Hibernate validate
  must fail rather than run against a partial schema.
- The seed is reference data only and contains no personal data.
- After publication, graph versions are immutable. Correct content by creating and
  publishing a successor; never edit a published row or applied Flyway migration.
- A failed publish rolls back the retirement, new publication, and success audit event.
- Operational rollback is application rollback while retaining additive tables, or
  database restore plus a new forward-fix migration. Do not down-migrate production.
- No feature flag is required for the seeded public read; if rollout evidence shows a
  need to hide it, disable routing/configuration rather than deleting graph data.

## Risks and open decisions

### Approved decisions

1. **Curriculum seed content:** the owner approved the proposed project-authored seed
   scope. The exact nodes, learning outcomes, prerequisite edges, weights, required
   mastery, and terminal markers will be reviewed in the implementation diff before V7
   is finalized.
2. **Public read policy:** the owner approved public reads for published curriculum,
   matching public goal-template discovery. Admin writes remain role-protected.
3. **Dependency direction:** the owner approved the goal-owned HTTP adapter delegating
   to the knowledge contract. Choosing a runtime `knowledge -> goal` call instead
   requires a documentation change and a cycle review.
4. **Seed publication:** the owner approved inserting the initial Flyway seed as
   published only after it passes the same validator fixture. All later versions use
   runtime validate/publish.

### Engineering risks

- Curriculum errors are product errors even when the DAG is structurally valid;
  structural validation cannot prove pedagogical correctness.
- MySQL generated-column uniqueness and concurrent publication need real MySQL tests;
  H2 or mocks are insufficient.
- Recursive traversal can amplify work; iterative algorithms and strict bounds are
  mandatory.
- Persisted JSON metadata can become an uncontrolled rule channel; core planner/domain
  behavior must stay in typed columns/contracts.
- Role names exist, but production role provisioning is intentionally unresolved and
  cannot be replaced by a public self-elevation endpoint.
- Flyway/MySQL compatibility warning remains until a supported upgrade is validated.
- Existing owner frontend/context changes increase merge/audit risk; Phase 2 commits
  must isolate their diff and preserve that work.

No new ADR is required if the approved implementation stays inside existing modular
monolith, MySQL/Flyway, deterministic graph, and security decisions. A change to graph
database technology, module direction, publication authority, or AI authority requires
an ADR before implementation.

## Validation results

Implementation completed on 2026-09-23 using Java 21.0.11, Maven 3.9.16, MySQL
8.4.11 Testcontainers, Node 24, and npm 11.

| Gate | Final result |
|---|---|
| Backend `clean verify` | PASS via direct Maven 3.9.16; 9 unit/architecture + 10 integration tests |
| Maven wrapper from current PowerShell | ENVIRONMENT FAILURE before Maven start (`Cannot index into a null array`) |
| Frontend lint | PASS |
| Frontend tests | PASS — 2 files, 3 tests |
| Frontend build | PASS |
| Phase 2 migration/domain/API tests | PASS |

Final results superseding the discovery rows above:

- Backend `clean verify`: PASS — 9 unit/architecture tests and 10 integration tests.
- Flyway: PASS — clean V1–V7 and explicit V5→V7 upgrade on MySQL 8.4.11.
- Domain: PASS — deterministic cycle/topology/traversal and derived frontier fixtures.
- API/security: PASS — published graph reads, bounds/cursor rejection, learner denial,
  curator replay, validation/publication, durable audit, and concurrent one-winner
  publication.
- OpenAPI generation: PASS and deterministic; SHA-256 before/after regeneration was
  `A16A0AE9484F6B5CD4D840398285CD3B29DE026F14E235B8CFC82831CFDBB137`.
- Frontend lint/test/build: PASS — 2 files, 3 tests, production build.
- Compose config: PASS; Docker emitted a local config-file access warning but returned
  success.
- `scripts/audit.ps1`: PASS — diff check and npm audit, 0 vulnerabilities.
- Repo-wide `prettier --check`: PRE-EXISTING FAILURE in four owner UI files
  (`RegisterPage.tsx`, `ActiveGoalPage.tsx`, `GoalSetupPage.tsx`, `global.css`). Phase 2
  did not rewrite those unrelated design changes; generated API types are ignored by
  the formatter and all executable frontend gates passed.
- Known warning retained: Flyway 11.7.2 reports MySQL 8.4 newer than its latest tested
  MySQL line (8.1).

Commands used for implementation validation:

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

The backend suite also tested Flyway clean and V5 upgrade paths against MySQL 8.4,
concurrent publication, canonical graph reads, and validated-successor publication.

## Documentation updates

Updated in the same implementation change:

- `KNOWLEDGE_GRAPH.md` with exact lifecycle, stable ordering, bounds/cursor, and initial
  seed contract;
- `DATABASE_DESIGN.md` with actual V6/V7 tables, constraints, and indexes;
- `MODULE_BOUNDARIES.md` with the approved goal/knowledge adapter direction;
- `API_CONTRACT.md` and `openapi-v1.yaml` with published Phase 2 operations;
- `TESTING_STRATEGY.md` with executable Phase 2 gates;
- `PHASE_ROADMAP.md` with honest checkpoint/phase status;
- `PROJECT_CONTEXT.md` with implemented surface, remaining risks, and next approved
  phase only after validation succeeds;
- this plan from `APPROVED` to `IN_PROGRESS`, and finally `DONE` with exact
  validation evidence.

Do not mark Phase 2 complete merely because schema/classes exist. Completion requires
the published seed query, secure lifecycle path, atomic concurrency behavior, full
regression validation, diff audit, and context update.
