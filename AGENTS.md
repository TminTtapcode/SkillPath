# AGENTS.md

## 1. Mission

Build **SkillPath**, an adaptive learning planner that converts a learner's goal, current knowledge, available time, deadline, and observed performance into the next highest-value learning task.

The core loop is:

`Goal → Assessment → Evidence → Knowledge State → Planner → Learning Task → Evaluation → State Update → Replan`.

## 2. Authority order

When sources conflict, use this order:

1. Explicit instruction from the project owner for the current task.
2. `AGENTS.md`.
3. Accepted ADRs in `docs/adr/`.
4. `PROJECT_CONTEXT.md`.
5. Product and architecture documents.
6. Domain specifications.
7. Approved task plan.
8. Existing code and tests.
9. External official documentation.
10. Agent assumptions.

If documentation and code disagree, report a `CONTEXT CONFLICT`. Do not silently choose or rewrite architecture.

## 3. Mandatory reading

Before any change:

1. Read `PROJECT_CONTEXT.md` and `DEVELOPMENT_RULES.md`.
2. Read relevant ADR, architecture, domain, API, and test documents.
3. Inspect existing implementation and current diff.
4. Run or identify the baseline validation commands.
5. State affected modules, data, risks, and planned tests.

For a large or cross-module change, create/update a plan from `docs/plans/PLAN_TEMPLATE.md` and wait for owner approval before implementation.

## 4. Architecture rules

- Backend is a modular monolith. Do not introduce microservices without an accepted ADR.
- Modules communicate through public application contracts, domain events, or explicit internal service interfaces.
- Never import another module's persistence entity, repository, controller, or private implementation package.
- Controllers authenticate and validate transport input, then delegate. They do not contain domain rules.
- Repositories do not decide business eligibility.
- Domain calculations must be deterministic pure functions where practical.
- MySQL is the system of record. Redis is not part of MVP unless approved by ADR.
- Flyway is the only authority for persistent schema changes.
- MySQL Workbench may inspect/query local development data; it must not become a schema-change workflow.

## 5. AI rules

AI may generate questions/exercises, evaluate open responses against a rubric, explain concepts, summarize approved resources, and propose misconception evidence.

AI must not directly determine or persist:

- final mastery;
- prerequisite satisfaction;
- planner priority;
- completion of a goal;
- authorization decisions;
- database mutations outside validated application contracts.

All AI output is untrusted structured input. Validate schema, allowed knowledge IDs, ranges, evaluator version, and evidence reliability before persistence.

## 6. Security and data rules

- Derive user identity from the authenticated principal, never request parameters.
- Enforce ownership server-side for every user-scoped object.
- Do not log passwords, tokens, full AI prompts containing private data, or sensitive answer content.
- Use parameterized queries/JPA. Never concatenate untrusted SQL.
- Keep secrets in environment variables or secret stores; never commit them.
- Do not use production data in local development.

## 7. Change rules

- Do not modify unrelated files.
- Do not add dependencies without justification and approval for material additions.
- Do not duplicate an existing service, model, DTO, utility, rule, or endpoint.
- Do not silently change formulas, weights, thresholds, enums, relation direction, or lifecycle transitions.
- Behavioral changes require tests and documentation; domain-policy changes require policy versioning and usually an ADR.
- Never edit an applied Flyway migration. Add a new forward migration.
- Never remove, skip, or weaken tests to make a build pass.
- Preserve backward compatibility unless the approved plan explicitly allows a break.

## 8. Required workflow

1. **Discover** — read context, inspect code, run baseline; make no changes.
2. **Design** — describe scope, contracts, schema impact, risks, tests, and files.
3. **Approve** — required for architecture, dependency, schema, security, or cross-module scope changes.
4. **Implement** — smallest coherent vertical slice; tests alongside code.
5. **Validate** — unit, integration, architecture, API, migration, frontend, security as applicable.
6. **Audit** — review diff, ownership, module boundaries, idempotency, error paths.
7. **Document** — update specs, ADRs, plan, and `PROJECT_CONTEXT.md`.

## 9. Default commands

Use repository wrappers/scripts when present. Intended command contract:

```bash
./mvnw -f backend/pom.xml clean verify
npm --prefix frontend ci
npm --prefix frontend run lint
npm --prefix frontend run test -- --run
npm --prefix frontend run build
docker compose config
```

Do not claim a command passed unless it was run and its exit code was successful.

## 10. Definition of Done

A task is done only when applicable items pass:

- acceptance criteria implemented;
- unit and integration tests added;
- API validation and authorization tested;
- Flyway migration tested against a clean MySQL container and upgrade path;
- module boundaries preserved;
- failure, retry, concurrency, and idempotency paths considered;
- frontend loading/empty/error/success states handled;
- diff reviewed for secrets and unrelated changes;
- documentation and project context updated;
- remaining risks reported honestly.

## 11. Stop conditions

Stop and ask the owner when:

- two authoritative documents conflict;
- a requirement changes core product direction;
- destructive data migration is needed;
- required credentials/permissions are unavailable;
- a missing business decision materially changes the implementation;
- completing the task requires weakening security or tests.
