# ADR-0005: Staged Dockerization

- Status: Accepted
- Date: 2026-09-22

## Context

Containerizing everything before code exists adds friction, while postponing all containers creates MySQL/environment drift.

## Decision

Dockerize MySQL infrastructure at repository bootstrap. Develop backend/frontend skeletons on host. Add application Dockerfiles and full Compose after the first database-backed vertical slice works. Harden production images during release preparation.

## Consequences

Developers get a consistent database early and retain fast debugging/hot reload. The first vertical slice becomes the packaging checkpoint. CI must still validate containers and clean Flyway startup.
