# Development Workflow

## Feature lifecycle

1. **Discover:** read context/specs, inspect repository, run baseline, identify ownership.
2. **Design:** write objective, user story, affected modules/tables/contracts, failure cases, tests, migration strategy, out-of-scope.
3. **Approve:** owner approval for material plans.
4. **Implement:** smallest vertical slice, tests with behavior.
5. **Validate:** run relevant quality gates in a clean/reproducible environment.
6. **Audit:** inspect diff, dependencies, security, ownership, idempotency, data compatibility.
7. **Document:** update context/spec/ADR and close plan honestly.

## Branch/task discipline

- One task/plan per coherent branch or changeset.
- Do not combine refactor, dependency upgrade, and feature unless plan requires it.
- Keep migrations and corresponding model/tests in the same change.
- Capture follow-up work explicitly; do not hide it as “done”.

## Implemented scripts

```text
scripts/dev-start.ps1
scripts/dev-stop.ps1
scripts/backend-test.ps1
scripts/frontend-test.ps1
scripts/db-migrate.ps1
scripts/verify.ps1
scripts/audit.ps1
```

Provide equivalent portable commands through Maven/npm/Compose; scripts are convenience, not the only path.

`dev-start.ps1` builds/starts the full Compose app profile. `db-migrate.ps1` starts
MySQL and invokes the Flyway Maven plugin. `verify.ps1` runs backend, frontend, and
Compose gates; `audit.ps1` checks the diff and npm vulnerabilities. See the repository
README for the verified newcomer order.

## Baseline before editing

- Check working tree and preserve unrelated user changes.
- Run targeted existing tests or full baseline when feasible.
- Inspect applied migrations and module contracts.
- Record pre-existing failures separately.

## Open-source intake

Use `docs/research/OPEN_SOURCE_ADOPTION.md` for every external repository, dependency,
code adaptation, curriculum/content import, model, or dataset.

1. Start from a concrete capability gap in an approved plan; do not browse-add
   dependencies speculatively.
2. Record repository/package, exact tag or commit, license at that revision,
   maintenance/release evidence, security policy, transitive dependencies, runtime
   data/network behavior, and removal/replacement strategy.
3. Classify the candidate as `REFERENCE`, `SPIKE`, `DEPENDENCY_CANDIDATE`,
   `ADAPT_CODE_CANDIDATE`, or `REJECTED`.
4. Review code and bundled content/data licenses separately. Public source without a
   license is reference-only and must not be copied.
5. Obtain owner approval for material dependencies, copyleft/attribution obligations,
   new infrastructure, external services, or policy/algorithm changes.
6. Pin the selected version; retain required notices and update the dependency
   inventory, lockfile, `THIRD_PARTY_NOTICES`, and SBOM process when they exist.
7. Add contract, deterministic, security, failure/fallback, migration, and removal
   tests proportional to the dependency's authority.
8. Re-run license, vulnerability, and compatibility review on every upgrade.

Copying or translating an algorithm still requires provenance. Reimplementation must
follow SkillPath's specification and tests rather than silently inheriting thresholds,
state transitions, or AI authority from the source project.

## Completion report

State outcome first, then files/behavior changed, validation commands/results,
migration/API impact, new/changed third-party dependencies and licenses, and remaining
risks. Never claim unexecuted tests passed.
