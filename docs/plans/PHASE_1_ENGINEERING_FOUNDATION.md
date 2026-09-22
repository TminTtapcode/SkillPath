# Plan: PHASE-1 — Engineering Foundation and Thin Goal Slice

## Status

`DONE`

Owner approval of this plan is required before implementation because the work adds
dependencies, persistent schema, authentication policy, CI, and cross-module
orchestration. Approval includes the proposed decisions called out in this document;
any material deviation returns the plan to `DRAFT`.

## Objective and user story

Deliver the first executable SkillPath vertical slice without implementing the
adaptive-learning algorithm.

As a new learner, I can register, sign in, view the available Java Backend Intern
goal template, create my one active goal with a target date/timezone/daily-time
budget, and retrieve that goal in a browser session. As a developer, I can reproduce
the environment, run both applications and their tests, migrate a clean MySQL 8.4
database, and validate the same workflow in CI.

## Authoritative references

- `AGENTS.md`
- `PROJECT_CONTEXT.md`
- `DEVELOPMENT_RULES.md`
- `docs/product/MVP_SCOPE.md`
- `docs/product/USER_JOURNEYS.md`
- `docs/architecture/SYSTEM_ARCHITECTURE.md`
- `docs/architecture/MODULE_BOUNDARIES.md`
- `docs/architecture/DATABASE_DESIGN.md`
- `docs/api/API_CONTRACT.md`
- `docs/development/TECH_STACK.md`
- `docs/development/TESTING_STRATEGY.md`
- `docs/development/DOCKER_STRATEGY.md`
- `docs/development/DEVELOPMENT_WORKFLOW.md`
- `docs/research/OPEN_SOURCE_ADOPTION.md`
- ADR-0001, ADR-0002, ADR-0005, and ADR-0006

Current upstream references used during discovery:

- Spring Boot 3.5 system requirements and Maven build guidance.
- Spring Security servlet CSRF/session documentation.
- Node.js release schedule; Vite 8 and package metadata.
- MySQL 8.4 container documentation.
- GitHub Actions Java/Maven documentation.

These upstream references inform compatibility only. Project documents remain
authoritative for architecture and policy.

## Current-state evidence

Discovery date: 2026-09-22.

### Repository and implementation

- The workspace contains 33 Markdown documents and no application source, build
  wrapper, migration, Compose file, test, or CI workflow.
- The current directory is not a Git worktree, so `git status` and a normal diff are
  unavailable. The project owner supplied
  `https://github.com/TminTtapcode/SkillPath.git`; the previously verified remote
  default branch is `main` at `9c5f1cd5af344fd348edbe09c4b6e73f5387e250`.
- Local documents contain work that must be preserved when Git metadata is attached.
- With no implementation or build descriptors, the documented baseline build/test
  commands cannot yet run. This is an absence of a baseline, not a passing baseline.
- No context conflict was found among the product, architecture, API, development,
  and accepted ADR documents relevant to Phase 1.

### Local toolchain

| Capability | Discovery result | Design consequence |
|---|---|---|
| Java | Oracle Java 21.0.11 LTS | Compatible with the required Java 21 baseline. CI uses a declared JDK 21 distribution. |
| Maven | No global `mvn` command | Commit Maven Wrapper; global Maven is not required. |
| Node.js | 24.19.0 | Use the Node 24 LTS line. |
| npm | 11.17.0 through `npm.cmd` | PowerShell blocks `npm.ps1`; scripts/docs use portable `npm` syntax and note `npm.cmd` for this Windows machine. |
| Docker | Client/Engine 29.8.0, Linux x86_64 | Compose and Testcontainers can run. |
| Compose | v5.5.1 | Use Compose Specification without obsolete `version`. |
| Git | 2.55.0.windows.3 | Sufficient for the non-destructive repository attachment procedure. |
| MySQL | Local 8.0.46 server owns port 3306 | Keep the project default configurable; use an untracked local override such as `MYSQL_PORT=3307`. |

### Proposed dependency baseline

The implementation must record exact selected versions, license/provenance, and
transitive review in the dependency inventory before adoption. The intended baseline
is:

- Backend: Java 21, Maven Wrapper 3.9.16, Spring Boot 3.5.16.
- Frontend: Node 24 LTS, npm lockfile, React 19.3, Vite 8.3, TypeScript 5.9.3,
  React Router 8.4, TanStack Query 5.103, React Hook Form 7.88, Zod 4.6,
  Vitest 5.0, Testing Library React 16.3, ESLint 10, and Prettier 3.9.
- Contract generation candidate: `openapi-typescript` 7.13.

Registry metadata confirms that the proposed React Router, Vite, and Vitest versions
accept Node 24 and the proposed React/Vite majors. Exact peer compatibility is a
scaffold acceptance gate; an incompatible combination must not be forced and any
material version change must be reported.

Implementation deviation (2026-09-22): the approved draft named TypeScript 7.0, but
`typescript-eslint` 8.70.1 declares TypeScript `<6.1.0` and
`openapi-typescript` 7.13.0 declares TypeScript 5.x. TypeScript 5.9.3 is used as the
newest version satisfying both constraints so linting and contract generation are
not weakened or bypassed. npm's strict peer-resolution failure was retained as the
evidence; `--force` and `--legacy-peer-deps` were not used.

Spring Boot 4 is intentionally not selected: `docs/development/TECH_STACK.md`
requires Spring Boot 3.x, and 3.5.16 is a current stable 3.x release compatible with
Java 21.

## Scope

### In scope

- Attach the supplied Git remote to the current workspace without overwriting local
  files, then establish an auditable working-tree baseline.
- Add Maven and Node/npm wrapper/configuration, deterministic lockfiles, formatting,
  linting, editor defaults, and repeatable development scripts.
- Scaffold a Java 21 Spring Boot 3 modular monolith and a React/TypeScript/Vite SPA.
- Add MySQL 8.4 Compose infrastructure, health check, configurable host port,
  persistent local volume, `.env.example`, and Workbench instructions.
- Add Flyway-owned identity, session, goal, idempotency, and seed migrations.
- Implement `auth`, `user`, and `goal` module foundations with boundary tests.
- Implement session registration/login/logout, current profile, CSRF handling,
  authorization, ownership, and the agreed session lifecycle.
- Implement goal-template listing, one active goal creation, and active-goal read.
- Publish an OpenAPI contract and centrally generate/check frontend API types.
- Implement browser routes and all loading, empty, validation, error, success, and
  unauthenticated states needed for the thin slice.
- Add backend/frontend/Compose CI, dependency/license inventory, third-party notice
  placeholder/process, and secrets/configuration checks.
- Add backend and frontend Dockerfiles only after the host-run slice passes.

### Out of scope

- Assessment, knowledge graph storage, progress/evidence, planner, learning tasks,
  reviews, adaptive replanning, AI adapters, or roadmap visualization.
- Goal update/completion and every API endpoint not explicitly included below.
- OAuth/social login, email verification, password reset, MFA, mobile/API bearer
  tokens, Redis, microservices, or horizontal deployment.
- Production hosting, production secrets, production data, or deployment pipelines.
- Copying code, content, datasets, or algorithms from researched repositories.
- Adopting a UI framework, graph visualization library, Spring Modulith, Lombok,
  MapStruct, JGraphT, or any unapproved infrastructure/dependency.

## Delivery checkpoints

Implementation proceeds in order. Each checkpoint must pass its validation gate
before the next begins.

### P1.0 — Preserve and attach repository history

1. Re-verify the exact remote URL, default branch, and commit before mutation.
2. Create a recoverable copy/archive of the current documentation inside an explicit
   temporary/recovery location.
3. Initialize Git, add/fetch the supplied remote, and attach `HEAD`/index to
   `origin/main` with a non-destructive procedure that preserves working files (for
   example, a verified mixed-index operation).
4. Confirm that every local documentation change appears as an intentional diff.
5. Do not use `git reset --hard`, checkout-overwrite, force push, or delete the
   recovery copy during Phase 1.

Gate: remote ancestry is visible, local content hashes are unchanged, and a cleanly
reviewable status/diff exists.

### P1.1 — Reproducible toolchain and empty applications

- Create `backend/` as one Maven-built Spring Boot modular monolith using Java 21 and
  base package `com.skillpath`.
- Create `frontend/` as one React/TypeScript/Vite SPA using Node 24 LTS and an npm
  lockfile.
- Add health-only backend and shell-only frontend startup paths; no domain behavior
  is added at this checkpoint.
- Add formatting, linting, test, build, and aggregate verification commands.
- Add GitHub Actions with least-privilege permissions and pinned action revisions.

Gate: clean checkout instructions run backend tests/package, frontend install/lint/
test/build, and `docker compose config` successfully.

### P1.2 — MySQL and migration baseline

- Compose runs `mysql:8.4` with a named volume and health check. Credentials and host
  port come from environment variables; only non-secret examples are committed.
- Canonical project port remains `3306`; this machine uses an untracked
  `MYSQL_PORT=3307` because 3306 is occupied.
- Use `utf8mb4` and `utf8mb4_0900_ai_ci`; store instants in UTC and retain learner
  timezone as an IANA identifier.
- Flyway is the only schema writer. Hibernate uses `ddl-auto=validate`. Spring Session
  automatic schema initialization is disabled; its tables are created by Flyway.
- Verify migration from empty schema and repeat application startup against the same
  migrated database. Never edit an applied migration.

Gate: a clean MySQL 8.4 instance becomes healthy, all migrations apply once, repeat
startup is idempotent, and schema validation succeeds.

### P1.3 — Authentication, session, and profile foundation

- Implement registration/login/logout and `GET /api/v1/me`.
- Keep credential/session/role persistence inside `auth`; keep profile, timezone,
  and preferences inside `user`. Registration is an application orchestration using
  a public user-module contract within one database transaction; neither module
  imports the other's entity or repository.
- Store passwords only through Spring Security's delegating encoder with BCrypt cost
  12. Trim and lowercase email with `Locale.ROOT`; enforce database uniqueness.
- Proposed password policy: 12–128 characters, no silent truncation. Do not log raw
  passwords, cookies, session IDs, CSRF tokens, or sensitive payloads.
- Proposed abuse baseline: after 5 consecutive failed logins, lock that credential
  for 15 minutes; successful authentication clears the counter. Return a generic
  authentication failure that does not reveal whether an account exists.
- Persist sessions in MySQL through Spring Session JDBC. Proposed policy: 8-hour idle
  timeout, at most 5 concurrent sessions per user, expire the oldest session when a
  sixth is established, no remember-me, session ID rotation after authentication,
  immediate server-side invalidation on logout, and scheduled expired-session
  cleanup. Values are configurable but tests pin the defaults.
- The session cookie is HTTP-only and `SameSite=Lax`; it is `Secure` outside local
  development. CORS allows only configured frontend origins with credentials.
- Expose `GET /api/v1/auth/csrf` for the SPA. State-changing requests require the
  CSRF header/token pair; the frontend refreshes the token at startup and after login
  or logout. POST login and logout are not CSRF exemptions.

Gate: security/API/integration tests cover happy path, duplicate registration,
invalid credentials, lockout/recovery, CSRF rejection, session fixation protection,
logout revocation, timeout/concurrency behavior, and unauthenticated access.

### P1.4 — Thin goal create/read slice

Implement only:

```text
GET  /api/v1/goal-templates
POST /api/v1/goals
GET  /api/v1/goals/active
```

- Seed one active `JAVA_BACKEND_INTERN` template through Flyway. Java Backend is data,
  not a module or hard-coded branching policy.
- `POST /goals` accepts an opaque template ID, future `targetDate`, valid IANA
  `timezone`, and `defaultDailyMinutes` from 30 through 180. User identity always
  comes from the authenticated principal.
- Enforce at most one active goal per user in both domain logic and a database unique
  invariant. A second active-goal attempt returns a stable `409` problem response.
- Require `Idempotency-Key`. The same user/key/request returns the original successful
  response; reuse with a different normalized request returns `409`; concurrent
  duplicates create exactly one goal. Persist request hash, outcome reference/status,
  and expiry without retaining secrets.
- Return transport DTOs and the documented problem envelope with correlation ID.
  Do not expose numeric ordering semantics even though MVP IDs are `BIGINT`.
- The SPA provides register, login, goal setup, and active-goal summary routes. It
  handles loading, empty, validation, duplicate/conflict, expired session, retry, and
  success states; fields have labels, keyboard access, visible focus, and associated
  error text.

Gate: one browser-level happy path and the API/integration negative/retry/concurrency
paths pass against MySQL 8.4.

### P1.5 — App containers and handoff

- Add multi-stage backend and frontend Dockerfiles only after P1.4 passes on the host.
- Run containers as non-root where supported, copy only runtime artifacts, add health
  checks, and keep configuration/secrets external.
- Extend Compose with optional application services without making Docker the only
  supported development workflow.
- Complete newcomer runbook, dependency/license inventory, notices, audit, and full
  Phase 1 validation.

Gate: a new developer can start MySQL, migrate, run the apps/tests, and complete the
goal slice using documented commands; equivalent container startup also succeeds.

## Design

### Backend structure and dependencies

Use package-by-module, then layer:

```text
com.skillpath
  shared/{api,config}
  auth/{api,application,domain,infrastructure}
  user/{api,application,domain,infrastructure}
  goal/{api,application,domain,infrastructure}
```

`shared` contains only genuinely cross-cutting transport/error/configuration code; it
must not become a domain dumping ground. Controllers validate/authenticate transport
input and call application services. Domain invariants stay deterministic and
persistence-independent where practical.

Proposed backend dependency capabilities:

- Spring Web, Validation, Security, Data JPA, Actuator.
- Flyway core plus MySQL support and MySQL Connector/J at runtime.
- Spring Session JDBC for server-managed shared/persistent sessions without Redis.
- Springdoc OpenAPI for the published HTTP contract.
- Spring Boot Test, Spring Security Test, Testcontainers JUnit/MySQL, and ArchUnit in
  test scope.

Use the Spring Boot dependency-management BOM where applicable and pin unmanaged
versions. Do not add a dependency whose exact revision/license/security/exit entry is
missing from the inventory.

### Persistent model and ownership

All identifiers are `BIGINT`; timestamps are UTC. Tables use foreign keys and
purpose-specific unique/index constraints.

| Module | Tables | Important invariants |
|---|---|---|
| `auth` | `user_credentials`, `user_roles`, Spring Session tables | normalized email unique; password hash only; roles constrained; sessions revocable and expiring |
| `user` | `users` | one profile per auth subject; valid timezone; optimistic version/timestamps |
| `goal` | `goal_templates`, `user_goals`, `idempotency_records` | unique template key; one active goal/user; owned idempotency key and request hash |

Use a nullable generated-column/unique-index strategy for the MySQL one-active-goal
constraint so historical completed/cancelled goals remain possible later. The exact
DDL and concurrency behavior require MySQL integration tests.

Suggested forward migrations:

```text
V1__create_identity_and_session_schema.sql
V2__create_goal_schema.sql
V3__seed_java_backend_goal_template.sql
```

If Spring Session's official MySQL schema needs a separate migration, split it before
implementation rather than mixing ownership ambiguously. Migration numbering, once
applied, is immutable.

### API contracts

In addition to the three goal endpoints, Phase 1 exposes:

```text
GET  /api/v1/auth/csrf
POST /api/v1/auth/register
POST /api/v1/auth/login
POST /api/v1/auth/logout
GET  /api/v1/me
```

Registration returns the authenticated session and own profile or a documented
created response followed by login; implementation must select one contract and
capture it in OpenAPI before frontend coding. The recommended choice is registration
creates and authenticates the session to avoid an unnecessary second credential
submission.

All errors use `application/problem+json` with the fields in `API_CONTRACT.md`.
`401`, `403`, `404`, `409`, `422`, and `429` semantics must remain distinct. No
endpoint accepts `userId` for user-scoped behavior.

Generate frontend TypeScript types from the published OpenAPI document and add a CI
drift check. The generated artifact must not be hand-edited.

### Frontend architecture

Use React Router in declarative SPA mode; TanStack Query owns server state and request
retry/invalidation. React Hook Form plus Zod owns form input and client feedback, but
the backend remains authoritative. Keep a small fetch client that always uses
`credentials: include`, maps problem responses, and manages CSRF refresh.

Proposed structure:

```text
frontend/src
  app/{router,providers}
  features/auth
  features/profile
  features/goals
  shared/{api,components,styles}
```

Use plain CSS or CSS modules for Phase 1; adding a component framework is outside
scope. Do not add a graph library before the Phase 6 comparison spike.

### CI and developer workflow

GitHub Actions jobs run:

```text
./mvnw -f backend/pom.xml clean verify
npm --prefix frontend ci
npm --prefix frontend run lint
npm --prefix frontend run test -- --run
npm --prefix frontend run build
docker compose config
```

Testcontainers uses a real MySQL image; H2 is forbidden. CI grants only required
repository permissions, does not echo secrets, and uses dependency caches keyed by
lock/build files. Convenience PowerShell scripts wrap but do not replace the portable
commands documented in `DEVELOPMENT_WORKFLOW.md`.

## Expected files

Exact scaffold-generated names may vary; deviations must be reported.

```text
.editorconfig
.gitattributes
.gitignore
.env.example
compose.yaml
.github/workflows/ci.yml
DEPENDENCIES.md
THIRD_PARTY_NOTICES.md
backend/pom.xml
backend/mvnw
backend/mvnw.cmd
backend/.mvn/wrapper/*
backend/src/main/java/com/skillpath/{shared,auth,user,goal}/**
backend/src/main/resources/application*.yml
backend/src/main/resources/db/migration/*.sql
backend/src/test/java/com/skillpath/**
backend/Dockerfile
frontend/package.json
frontend/package-lock.json
frontend/tsconfig*.json
frontend/vite.config.*
frontend/eslint.config.*
frontend/src/{app,features,shared}/**
frontend/src/**/*.test.*
frontend/Dockerfile
scripts/{dev-start,dev-stop,backend-test,frontend-test,db-migrate,verify,audit}.ps1
README.md
PROJECT_CONTEXT.md
docs/api/API_CONTRACT.md
docs/architecture/DATABASE_DESIGN.md
docs/development/{TECH_STACK,TESTING_STRATEGY,DOCKER_STRATEGY,DEVELOPMENT_WORKFLOW}.md
docs/research/OPEN_SOURCE_ADOPTION.md
docs/plans/PHASE_1_ENGINEERING_FOUNDATION.md
```

## Tests

### Unit and architecture

- Email normalization, password policy, lockout state transitions, goal validation,
  active-goal invariant, and idempotency request hashing.
- Controllers do not import repositories; external modules do not import private
  infrastructure; domain packages remain free of controller/repository dependencies.

### MySQL integration and migration

- Apply all migrations to a clean MySQL 8.4 container and validate ORM mappings.
- Re-start without reapplying or mutating migrations.
- Enforce normalized-email and one-active-goal uniqueness under concurrency.
- Persist/retrieve/expire/revoke sessions and clean expired records.
- Seed exactly one stable Java Backend template across clean setups.
- Confirm UTF-8 text and timezone round trips.

### API and security

- Register/login/logout/me success and all validation/error envelopes.
- CSRF missing/invalid/valid, allowed-origin credentials, cookie flags by profile,
  session rotation, logout revocation, idle expiration, and concurrent-session cap.
- Duplicate email and generic failed-login/lockout responses do not enumerate users.
- Unauthenticated goal requests fail; caller identity cannot be overridden.
- Goal create/read, invalid template/date/timezone/minutes, already-active conflict,
  same/different idempotency replay, and concurrent retry.

### Frontend and end to end

- Components/routes cover loading, empty, validation, server error, conflict,
  unauthenticated, and success states.
- API client includes credentials, sends/refreshes CSRF correctly, and maps problem
  details without exposing unsafe content.
- Keyboard navigation, labels, focus, and error associations pass automated checks
  plus a short manual smoke test.
- One end-to-end flow: register → view template → create goal → reload → read active
  goal → logout → protected read is rejected.

## Acceptance criteria

1. Given a fresh supported machine, when the documented setup commands run, then
   MySQL becomes healthy, Flyway migrates it, both apps start, and all quality gates
   complete without global Maven.
2. Given a new valid email and password, when the learner registers, then one account,
   profile, authenticated rotated session, and no plaintext credential are created.
3. Given missing/invalid CSRF or no session, when a state-changing/protected request
   is made, then the server rejects it with the documented safe problem response.
4. Given repeated bad credentials, when the fifth consecutive failure is reached,
   then the proposed lockout policy applies without revealing account existence.
5. Given an authenticated learner, when templates are requested, then the seeded Java
   Backend Intern template is returned as data.
6. Given valid goal input and a new idempotency key, when goal creation is requested,
   then exactly one active goal owned by the principal is stored and returned as 201.
7. Given the same key and normalized request, when retried or submitted concurrently,
   then the original result is returned and no duplicate goal exists.
8. Given the same key with different input or an existing active goal, when create is
   requested, then a stable 409 response is returned and existing data is unchanged.
9. Given logout, expiry, or concurrent-session eviction, when the old cookie is used,
   then protected endpoints return 401 and no raw session identifier appears in logs.
10. Given the frontend workflow, when network/auth/domain failures occur, then the UI
    presents accessible recovery guidance without losing valid user input.
11. Given CI on a clean revision, when all jobs run, then backend, frontend, MySQL
    integration, contract drift, architecture, and Compose checks pass.
12. Given the approved open-source gate, when the phase is audited, then every adopted
    dependency/image/action has exact provenance, license, and replacement notes.

## Rollout and rollback

- This is a pre-production foundation with no user data migration or backward client
  compatibility requirement.
- Apply migrations before application startup. Schema rollback is restore-from-backup
  or a reviewed forward fix; applied Flyway files are never edited or deleted.
- Keep P1.0 recovery material until repository history and documentation diff are
  independently verified.
- Application containers are additive and introduced only after host execution is
  green; a container failure falls back to the documented host workflow.
- Lockfiles, exact image tags/digests where practical, and action commit pins make
  rollback to the prior revision reproducible.
- No production rollout occurs in Phase 1.

## Risks and approved-by-plan decisions

Approval of this plan accepts these proposed decisions:

- Base Java package: `com.skillpath`.
- Spring Boot 3.5.x/Java 21 and Node 24/Vite 8/React 19 baselines.
- MySQL-backed Spring Session JDBC; no Redis.
- Registration authenticates the newly created session.
- Password length 12–128, BCrypt cost 12, five-failure/15-minute lockout.
- Session defaults: 8-hour idle timeout, five concurrent sessions, evict oldest,
  no remember-me.
- MySQL collation `utf8mb4_0900_ai_ci` and local port override rather than stopping
  the developer's existing MySQL service.
- Declarative React Router plus TanStack Query, no UI or graph framework.

Principal risks and mitigations:

- **Existing files versus remote history:** attachment can make every local file look
  changed or overwrite work if done incorrectly. Mitigate with exact remote/hash
  verification, recovery copy, content hashes, and non-destructive index attachment.
- **Fast-moving frontend majors:** pin exact compatible versions and lockfile; verify
  engines/peers before scaffold and record any adjustment.
- **Session schema/cleanup/concurrency:** use the official Spring Session contract,
  Flyway ownership, real-MySQL tests, and explicit expiry/concurrency tests.
- **Registration crosses `auth` and `user`:** use public application contracts and one
  transaction; enforce boundaries with ArchUnit.
- **Idempotency races:** use database uniqueness and transactional outcome storage,
  not only an in-memory check.
- **MySQL 8.0 local versus 8.4 project:** Compose/Testcontainers are authoritative;
  do not validate migrations only against the installed 8.0 server.
- **Oracle local JDK versus CI distribution:** compile to Java 21 and run the complete
  test suite in both CI and the local supported JDK.
- **Cookie/CSRF differences by environment:** keep secure production defaults,
  isolate explicit local exceptions, and test both configurations.

No unresolved business decision blocks approval. If the owner does not accept any
decision above, revise this plan before implementation.

## Validation results

Discovery-only results:

```text
git status --short
  NOT RUNNABLE — current directory is not a Git worktree.

java -version
  PASS — Oracle Java 21.0.11 LTS detected.

mvn -version
  NOT RUNNABLE — no global Maven; wrapper does not exist yet.

node --version
  PASS — v24.19.0.

npm.cmd --version
  PASS — 11.17.0. PowerShell npm.ps1 is blocked by local execution policy.

docker version / docker compose version
  PASS — client/engine 29.8.0 and Compose v5.5.1; engine access confirmed.

TCP port/process inspection
  PASS — port 3306 is occupied by mysqld 8.0.46; use local override 3307.

Application tests/build
  NOT RUNNABLE — no source/build files exist before Phase 1 implementation.
```

Implementation results, completed 2026-09-23 on Windows 11, Java 21.0.11,
Node 24.19.0, Docker Engine 29.8.0, and Compose 5.5.1:

```text
P1.0 repository attachment
  PASS (exit 0) — origin/main at 9c5f1cd5af344fd348edbe09c4b6e73f5387e250;
  pre-attachment Markdown hashes remained unchanged; .recovery is retained/ignored.

backend/mvnw.cmd -f backend/pom.xml clean verify
  PASS (exit 0) — 5 unit/architecture tests and 5 MySQL integration tests;
  clean mysql:8.4 applied V1–V5, Hibernate validation passed, no skipped tests.

scripts/db-migrate.ps1
  PASS (exit 0) — existing Compose schema at V5; repeat run reported no migration
  necessary. Script reads the ignored .env and uses the local port 3307 override.

npm --prefix frontend ci / format / lint / test -- --run / build
  PASS (exit 0) — 231 packages installed, 2 files/3 tests passed, 241 modules built.

npm --prefix frontend run api:generate + generated-file diff
  PASS (exit 0) — checked OpenAPI client artifact has no drift.

docker compose config
  PASS (exit 0).

docker compose --profile app up --detach --build --wait
  PASS (exit 0) — MySQL, backend, and frontend all healthy; backend and frontend
  runtime users are non-root (`skillpath` and UID 101).

Container smoke through http://localhost:5173
  PASS — register; list JAVA_BACKEND_INTERN; goal create 201; same-key replay 200;
  active read; logout 204; old session protected read 401.

scripts/audit.ps1 + secret-pattern scan
  PASS (exit 0) — git diff whitespace clean, npm reports 0 vulnerabilities,
  no credential/private-key pattern found, .env and .recovery confirmed ignored.
```

The integration suite specifically covers duplicate registration, generic invalid
credentials, five-attempt lockout, session ID rotation, 8-hour persisted timeout,
five-session eviction returning the standard 401 problem, CSRF rejection, logout
revocation, same/different idempotency replay, active-goal conflict, and two concurrent
same-user requests creating exactly one goal. The concurrency test exposed and drove
fixes for an InnoDB gap-lock deadlock and Spring's default 200 expired-session response.

Context conflict resolved during implementation: the approved draft used
`auth_credentials`/`auth_roles`, while the higher-authority database design specified
`user_credentials`/`user_roles`. V5 performs the forward rename and mappings now use
the authoritative names; no applied migration was edited.

Remaining known risk: Spring Boot 3.5.16 manages Flyway 11.7.2, which logs that MySQL
8.4 is newer than its last tested MySQL line (8.1). Clean migration, repeat migration,
application startup, ORM validation, and integration tests all pass on MySQL 8.4.11;
re-evaluate the Boot/Flyway combination before a production release rather than
overriding the BOM ad hoc.

## Documentation updates

During implementation, update:

- `README.md` with the verified newcomer quick start and goal-slice smoke test.
- `PROJECT_CONTEXT.md` with actual repository/module/schema/API status and remaining
  decisions.
- API, database, tech-stack, testing, Docker, and workflow docs with implemented
  contracts and commands.
- `OPEN_SOURCE_ADOPTION.md`, dependency inventory, notices, and future SBOM location
  with exact adopted revisions/licenses.
- This plan status and validation results after each checkpoint.

Do not update planner, assessment, knowledge-state, or roadmap-visualization policies
unless implementation reveals a real context conflict; report such a conflict instead
of silently changing later-phase domain behavior.
