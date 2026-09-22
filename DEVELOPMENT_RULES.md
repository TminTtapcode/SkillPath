# Development Rules

These rules complement `AGENTS.md` and apply to human and agent contributions.

## Code

- Prefer clarity and explicit domain language over clever abstraction.
- Keep methods focused; extract policies/calculators from orchestration services.
- Use immutable DTOs/value objects where practical.
- Validate at boundaries and enforce invariants inside the domain.
- Use UTC instants in storage; convert timezone only at presentation/scheduling boundaries.
- Use `BigDecimal` or explicitly scaled integer values for persisted scores when exact reproducibility matters; document chosen scale.
- Avoid nullable booleans and magic strings; use constrained enums/value types.
- Paginate unbounded collections.

## Backend

- Package by module and layer: `api`, `application`, `domain`, `infrastructure`.
- Entities remain inside their owning module.
- Map API DTOs at the boundary; do not expose JPA entities.
- Transactions begin in application services/use cases.
- External calls, including AI, must have timeout, retry policy where safe, and failure mapping.
- Use optimistic locking for user knowledge projections and other concurrent aggregates.

## Frontend

- Organize by feature, with shared UI only when genuinely reusable.
- Keep server state in a query/cache layer; do not duplicate it across components.
- Every remote view handles loading, empty, error, success, and permission-denied states.
- Client-side checks improve UX but never replace backend authorization.
- Generate or centrally maintain API types; avoid parallel handwritten contracts.

## MySQL and Flyway

- Use `utf8mb4` and a documented collation consistently.
- Prefer explicit foreign keys, unique constraints, check constraints where enforced, and indexes based on query paths.
- Avoid reserved words and ambiguous pluralization.
- Migration names use `V<version>__<description>.sql`.
- Never alter a migration already applied outside a disposable local environment.
- Data backfills must be restartable/idempotent or explicitly guarded.
- MySQL Workbench is allowed for local inspection, EXPLAIN plans, and read-only verification. Exported Workbench model files are optional documentation, never the schema source of truth.

## Git and reviews

- One coherent task per change set.
- Review `git diff` before completion.
- Do not commit generated build output, secrets, IDE state, database volumes, or local `.env`.
- Commit messages should state intent and affected area.

## Documentation

- Update the specification in the same change as behavior.
- Use ADRs for decisions that constrain future implementation.
- Use examples to clarify rules, but examples do not override normative statements.
- Mark assumptions and unresolved decisions explicitly.
