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
- Localized learner reads accept `Accept-Language: vi-VN|en`. Unsupported, malformed,
  or absent values resolve to canonical English. Successful localized responses return
  the resolved tag in `Content-Language`; IDs and authoritative values never vary by locale.

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

Phase 1–5 implemented operations are published in `openapi-v1.yaml`. The core
identity/goal/graph/diagnostic operations include:

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
POST /api/v1/assessments/diagnostic
GET  /api/v1/assessments/{sessionId}/next-question
POST /api/v1/assessments/{sessionId}/attempts
GET  /api/v1/assessments/{sessionId}/result
```

Registration creates the profile and an authenticated session. `POST /goals`
requires `Idempotency-Key`: same key plus the same normalized input replays the
original result; different input returns `IDEMPOTENCY_KEY_REUSED`; an existing active
goal returns `ACTIVE_GOAL_ALREADY_EXISTS`. Each section below distinguishes implemented
endpoints from future target contracts.

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

`POST /goals` accepts an integer stored `defaultDailyMinutes` from 20 through 180
(`goal-daily-budget-v2`). A Goal budget is not a Today override. Values outside the
range are rejected at the transport boundary and by Goal/domain and database
constraints; existing Goal IDs and budgets remain unchanged.

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

Phase 3 implements these four endpoints for authenticated learners. Starting creates
or resumes one seven-day diagnostic pinned to the active goal, graph version, question
versions, and `assessment-objective-v1` policy. Attempt submission requires
`Idempotency-Key`; the same key and normalized payload replays the original attempt,
while key reuse with a different payload is rejected. Question responses never expose
answer keys or mappings.

Phase 3 result scores and concept rows are observational evidence answering “what did
this attempt demonstrate?”. They are not mastery, confidence, prerequisite
satisfaction, readiness, or a planner recommendation. Knowledge State and Planner
retain those separate authorities.

### Localization

The localized reads are goal-template discovery and graph, published knowledge node/
prerequisite/dependent queries, diagnostic next-question, and diagnostic result. English
is immutable canonical content; Vietnamese is a presentation overlay. Diagnostic prompt
and option labels may change with locale, but `sessionQuestionId`, `questionVersionId`,
option IDs, answer keys, scoring, evidence, and idempotency input do not. Changing locale
therefore never submits or advances a diagnostic.

Phase 5 also localizes resource, task, checklist, and sequence presentation. A session
pins both language snapshots when assigned. Switching language does not change task
identity, step IDs, actual minutes, command receipts, or lifecycle state.

### Today/learning

Phase 5 implements a learner-selected study sequence, not the Planner's personalized
Today plan. The new authenticated routes are:

```http
GET  /learning/sequences
GET  /learning/sequences/{key}
POST /learning/sequences/{key}/sessions
GET  /learning/sessions/active
GET  /learning/sessions/{id}
POST /learning/tasks/{id}/start
POST /learning/tasks/{id}/complete
POST /learning/tasks/{id}/skip
POST /learning/tasks/{id}/blocked
POST /learning/tasks/{id}/resume
POST /learning/tasks/{id}/abandon
```

All writes use `Idempotency-Key` and CSRF. Same key with canonical command input
replays the saved outcome; a different command/input conflicts. Reads and writes are
principal-scoped, with cross-owner session/task IDs concealed as `404`. Task completion
accepts bounded actual minutes and server-declared checklist IDs, not score, evidence,
mastery, user ID, assignment source, or planner decision. The content snapshot includes
both languages; locale affects only presentation, never command hashes or task history.
Self-report records engagement only and cannot change Knowledge State or Review.

Phase 7 introduces an objective task check behind the disabled-by-default
`skillpath.phase7.task-check-enabled` rollout gate:

```http
GET  /learning/tasks/{taskId}/check
POST /learning/tasks/{taskId}/check/attempts
```

The GET returns a pinned single/multiple-choice question with localized option
labels and stable IDs, never an answer key. POST requires CSRF and
`Idempotency-Key`, validates the owned, started `OBJECTIVE` task, and atomically
records one attempt/evidence handoff and Learning completion. An identical retry
returns the attempt; another key or payload for that task conflicts. Score is
observational, not mastery. Ordinary `/complete` rejects `OBJECTIVE` tasks.
The task-check gate remains off until a freshly built end-to-end learner flow has
been validated; the P6 catalog does not assign these variants.

Phase 6 adds explicit, authenticated Today and roadmap routes:

```http
GET  /learning/today
POST /learning/today/generate
POST /learning/today/revise
GET  /learning/today/plans/{id}
GET  /roadmap
```

The two POST commands have an empty body and require CSRF plus `Idempotency-Key`.
The server derives the learning day and budget from the owned goal; the client cannot
set priority, decision, assignment, or mastery. `GET /learning/today` never generates
work. `generate` returns the existing current plan for the day if present. `revise`
creates a new immutable revision only if all previous planner tasks remain `ASSIGNED`.
A learner-selected active session blocks planner assignment. Historical revisions
remain available through the owner-scoped plan read; cross-owner IDs are concealed.
No-plan responses carry `reasonCode`: `NO_CONTENT` when no active compatible task
candidate exists, `NO_ELIGIBLE_VARIANT` when candidates are blocked or unsafe, or
`NO_TIME_FIT_VARIANT` when available content exceeds the stored budget. A goal
completion candidate is not a completed Goal.
The UI treats diagnostic evidence, knowledge estimates, and planner reasons as
different authorities.

Phase 7 adds the following authenticated, owner-scoped day commands and read:

```http
PUT  /learning/today/available-minutes
POST /learning/today/refresh
GET  /learning/today/replan-status
```

Both writes require CSRF and `Idempotency-Key`. PUT accepts only an integer
`availableMinutes` in 1–180, audits a local-day override without changing Goal,
and queues a durable replan. GET remains side-effect-free and reports pending,
processing, completed, or terminal-failed request state without claiming the
plan already changed. A missed-day Today read returns `REFRESH_REQUIRED`; only
the explicit refresh expires old unstarted planner assignments. `planner-v2`
revisions carry completed/in-progress task references to their original
immutable decisions instead of duplicating Learning task IDs.

### Progress

```http
GET /knowledge/me
GET /knowledge/me/{nodeId}
GET /knowledge/me/{nodeId}/evidence
GET /reviews/today
```

Phase 4 implements these authenticated, principal-scoped reads plus the ADMIN-only
`POST /admin/progress/rebuild`. Knowledge responses distinguish stored acquisition
mastery from time-decayed effective mastery, carry `knowledge-state-v1`, and expose
append-only evidence provenance without raw answers. The due-review read does not
assign a learning task or advance an interval.

The Phase 6 roadmap response is a read-only, bounded graph projection with mutually
exclusive `knowledgeStatus`, separate `current`/`ready`/`blockedBy` overlays,
graph version, progress/review digests, plan revision, and one `projectionAsOf`.
When a Today plan exists, overlays are derived from its pinned snapshot, with a stale
badge if current state differs. The read accepts `limit` 1–100 (default 50) and an
opaque `cursor`; `hasMore`/`nextCursor` page nodes in stable graph order. Edges whose
target is on the page accompany that page. The cursor binds the owner, goal, graph,
plan revision, `projectionAsOf`, and progress/review digests; a changed snapshot
returns `409 ROADMAP_CURSOR_STALE`, and malformed cursors return `400`. The roadmap cannot
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
