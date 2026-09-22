# ADR-0002: MySQL, Flyway, and Workbench

- Status: Accepted
- Date: 2026-09-22

## Context

The owner prefers MySQL and MySQL Workbench. The project needs repeatable schema evolution across local, CI, and production environments.

## Decision

Use MySQL 8.4 LTS with InnoDB and `utf8mb4`. Flyway migration files are the schema source of truth. MySQL Workbench is a local inspection, query, and `EXPLAIN` tool; manual Workbench schema edits are not shared or deployed.

## Consequences

Integration tests use Testcontainers MySQL rather than H2. Every schema change requires a forward Flyway migration. Workbench diagrams may document the model but cannot supersede migrations.
