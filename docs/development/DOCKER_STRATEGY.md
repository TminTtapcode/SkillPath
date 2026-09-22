# Docker Strategy

## Decision

Do not choose between “Docker first” and “web first” as absolutes.

1. **Infrastructure first:** create Compose for MySQL at repository bootstrap.
2. **Application code next:** run backend/frontend efficiently on host during rapid skeleton development.
3. **Application containers after first vertical slice:** once create/read goal works with MySQL and Flyway, add backend/frontend Dockerfiles and full Compose profile.
4. **Production hardening near deployment:** multi-stage builds, non-root users, health checks, read-only filesystem where feasible, secrets, backups, and migration release process.

## Why

- Early MySQL container prevents local-version drift and makes onboarding/CI reproducible.
- Delaying app containers briefly keeps hot reload/debugging simple while structure changes rapidly.
- Containerizing after a working slice tests real ports, CORS, health, networking, and environment contracts before architecture expands.

## Intended local Compose services

```text
mysql        always in foundation
backend      added after first vertical slice
frontend     added after first vertical slice (development profile optional)
redis        absent until approved
```

The Phase 1 state now includes all three intended services. `mysql` runs by default;
`backend` and `frontend` are enabled with `--profile app`. The frontend Nginx service
serves the SPA and proxies `/api/` to the backend, so the browser uses one origin.

## MySQL requirements

- Pin `mysql:8.4` line, not `latest`.
- Named volume for local durability.
- Health check before backend readiness.
- Credentials from `.env`; commit only `.env.example` with non-secret placeholders.
- Bind `3306` to localhost for Workbench, configurable to avoid conflicts.
- Initialization and schema changes via Flyway, not ad-hoc `/docker-entrypoint-initdb.d` business migrations.

## Image requirements

- Multi-stage builds.
- Minimal runtime base, non-root user.
- No source secrets or local `.env` in image layers.
- Reproducible dependency resolution.
- Backend exposes readiness/liveness endpoints.
- Frontend production image serves static assets and routes safely.
- Tag images with immutable commit/version in release environments.

## Validation

At foundation: `docker compose config`, MySQL health, Workbench connection, Flyway clean-database migration.

After vertical slice: build all images, start from empty volumes, verify API/web health and end-to-end goal flow, stop/restart without data loss, and confirm no committed secrets.

Implemented Dockerfiles use Maven/Temurin multi-stage backend builds and a
Node-to-unprivileged-Nginx frontend build. The backend runtime creates a dedicated
non-root user; the frontend base image is unprivileged. Configuration remains in
environment variables and `.env` is excluded from build contexts.
