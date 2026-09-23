# ADR-0007: Goal daily-budget minimum v2

- Status: Accepted
- Date: 2026-09-23
- Decision owner: SkillPath project owner

## Context

Goal creation originally accepted `defaultDailyMinutes` from 30 to 180. The
deterministic planner can safely compose independently complete short tasks within
20 minutes. The owner explicitly approved making 20 minutes a valid stored Goal
budget. A 20-minute planner fixture alone did not require this Goal change: it
could already represent the time remaining after a task in a 30-minute day.

## Decision

`goal-daily-budget-v2` accepts integer daily budgets from **20 through 180 minutes**
inclusive for new Goals. The value remains owned by Goal and stored with the Goal;
the Phase 6 Today API still accepts no client budget override. Goal API validation,
domain validation, OpenAPI, and frontend choices must agree. Flyway V20 replaces the
old database CHECK with a 20–180 CHECK without modifying existing rows or V2.

## Consequences

Existing 30–180 minute Goals and their historical plans are unchanged. A learner
may create a new 20-minute Goal; Planner must assign only complete variants fitting
that budget or return a no-safe outcome. Values below 20 or above 180 remain invalid.
The minimum is a Goal input policy, not a change to `planner-v1` scoring or its
idempotency semantics. Future changes require another versioned decision and forward
migration.
