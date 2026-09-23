# API Contract v1

## Conventions

- Base path: `/api/v1`.
- JSON uses `camelCase`; timestamps are ISO-8601 UTC.
- Authentication identity comes from secure authenticated context, never a client-supplied `userId`.
- Browser authentication uses a server-managed HTTP-only cookie session for MVP. Non-local cookies are `Secure`; state-changing requests enforce CSRF protection and session rotation/revocation rules.
- Commands that can be retried require `Idempotency-Key`.
- Pagination uses stable cursor where data changes frequently; otherwise documented page/size.
- Return transport DTOs, never persistence entities.
- IDs are opaque API values even when the MVP stores them as `BIGINT`; clients must not infer order or numeric semantics.

## Error envelope

```json
{
  "type": "https://skillpath.app/problems/validation-error",
  "title": "Validation failed",
  "status": 400,
  "code": "VALIDATION_ERROR",
  "detail": "One or more fields are invalid.",
  "instance": "/api/v1/goals",
  "correlationId": "...",
  "fieldErrors": [{"field": "targetDate", "code": "MUST_BE_FUTURE"}]
}
```

Use consistent codes. Do not leak stack traces, SQL, secrets, answer keys, or existence of another user's private resource.

## MVP endpoints

The following Phase 1 and Phase 2 operations are currently implemented and published
in `openapi-v1.yaml`:

```http
GET  /api/v1/auth/csrf
POST /api/v1/auth/register
POST /api/v1/auth/login
POST /api/v1/auth/logout
GET  /api/v1/me
GET  /api/v1/goal-templates
POST /api/v1/goals
GET  /api/v1/goals/active
GET  /api/v1/goal-templates/{goalTemplateId}/graph
GET  /api/v1/knowledge/nodes/{nodeId}
GET  /api/v1/knowledge/nodes/{nodeId}/prerequisites
GET  /api/v1/knowledge/nodes/{nodeId}/dependents
POST /api/v1/admin/knowledge/versions/{versionId}/validate
POST /api/v1/admin/knowledge/versions/{versionId}/publish
```

Registration creates the profile and an authenticated session. `POST /goals`
requires `Idempotency-Key`: same key plus the same normalized input replays the
original result; different input returns `IDEMPOTENCY_KEY_REUSED`; an existing active
goal returns `ACTIVE_GOAL_ALREADY_EXISTS`. All other endpoint lists below are target MVP
contracts, not claims of implementation.

Phase 2 knowledge reads expose only the current published curriculum and contain no
learner state, so they are public like goal-template discovery. Goal-graph queries are
bounded by `depth <= 10` and `limit <= 200`; cursors are opaque and version-bound.
Validation/publication requires `CURATOR` or `ADMIN`, an authenticated session, and
CSRF. Publication is atomic and retrying the already-published version is idempotent.

### Session/profile

```http
POST /auth/register
POST /auth/login
POST /auth/logout
GET  /me
PATCH /me/preferences
```

### Goals

```http
GET  /goal-templates
GET  /goal-templates/{goalTemplateId}/graph
POST /goals
GET  /goals/active
GET  /goals/{goalId}/roadmap
PATCH /goals/{goalId}
```

### Assessment

```http
POST /assessments/diagnostic
GET  /assessments/{sessionId}/next-question
POST /assessments/{sessionId}/attempts
GET  /assessments/{sessionId}/result
```

### Today/learning

```http
GET  /learning/today
PUT  /learning/today/available-minutes
POST /tasks/{taskId}/start
POST /tasks/{taskId}/complete
POST /tasks/{taskId}/skip
POST /tasks/{taskId}/blocked
```

### Progress

```http
GET /knowledge/me
GET /knowledge/me/{nodeId}
GET /knowledge/me/{nodeId}/evidence
GET /reviews/today
```

The roadmap response is a read-only, version-stamped projection containing a bounded
set of nodes/edges, mutually exclusive `knowledgeStatus`, separate planner overlays
(`current`, `ready`, `blocked`), blocked prerequisite IDs/reasons, current plan items,
and expansion cursors/links where needed. It must include graph version,
progress/review snapshot versions, planner revision, and `projectionAsOf`. It cannot
accept mastery, prerequisite-waiver, or goal-completion mutations.

### Admin/curator

Phase 2 implements protected graph validate/publish endpoints. Successful lifecycle
transitions create durable audit entries. General graph CRUD/import and role
provisioning are not public APIs.

## Status semantics

- `200`: successful query/update.
- `201`: resource created.
- `202`: accepted asynchronous evaluation.
- `204`: successful command without body.
- `400`: malformed/invalid request.
- `401`: unauthenticated.
- `403`: authenticated but unauthorized.
- `404`: not found or intentionally concealed cross-owner resource.
- `409`: state/version/idempotency conflict.
- `422`: well-formed command violates domain rule.
- `429`: rate limit.
- `503`: dependency unavailable with safe retry guidance.

## Contract governance

Implementation must publish OpenAPI and test representative schemas. Breaking changes require a new API version or an approved migration plan. Frontend types should be generated or centrally derived from the contract.

For Phase 1, `docs/api/openapi-v1.yaml` is the checked-in source used by
`openapi-typescript`. Runtime springdoc output is available at `/v3/api-docs` for
inspection, but changes must be reconciled into the checked-in contract and its
generated client types in the same changeset.
