# ADR-0003: Deterministic Planner v1

- Status: Accepted
- Date: 2026-09-22

## Context

An LLM can generate plausible roadmaps but cannot guarantee reproducibility, prerequisite safety, stable thresholds, or auditable priority.

## Decision

Planner v1 uses versioned deterministic candidate generation, prerequisite filtering, scoring, tie-breaking, and fallback. AI may explain or generate task content but does not select the final task.

## Consequences

Decisions are testable and replayable. Policy tuning requires versioning and fixtures. More advanced statistical ranking can be evaluated later without surrendering invariant gates.
