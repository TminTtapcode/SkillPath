# ADR-0006: Server-Managed Web Session Authentication

- Status: Accepted
- Date: 2026-09-22

## Context

The web MVP needs a concrete authentication delivery mechanism before the secured
goal vertical slice. The open choices were a server-managed cookie session or a
browser-held access token with refresh rotation. SkillPath initially has one React
web client and one Spring Boot modular-monolith backend, so adding refresh-token
rotation and browser token handling would add failure and exposure paths without a
current cross-client requirement.

## Decision

Use a server-managed session carried by an HTTP-only cookie for the web MVP. Non-local
cookies use `Secure` and an explicit `SameSite` policy. State-changing requests use
CSRF protection. Authentication rotates the session identifier, logout invalidates
the server-side session, and authorization continues to derive identity from the
authenticated principal rather than request parameters.

Session records, expiry, concurrent-session policy, and cleanup must be specified in
the Phase 1 implementation plan. Secrets and raw session identifiers must not be
logged.

## Consequences

The initial web flow avoids exposing bearer tokens to JavaScript and keeps revocation
server-controlled. Horizontal deployment must provide an approved shared/persistent
session strategy or documented session affinity; Redis remains outside MVP unless a
measured need and an approved ADR introduce it. A token-based browser/mobile/API flow
requires deployment or client evidence plus an approved plan or superseding ADR.
