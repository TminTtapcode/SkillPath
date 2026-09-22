# ADR-0004: AI as a Validated Adapter

- Status: Accepted
- Date: 2026-09-22

## Context

Semantic evaluation and content generation benefit from AI, but model output is probabilistic and may be invalid or unsafe.

## Decision

AI is accessed through versioned structured contracts. Output is untrusted until schema, ranges, IDs, rubric, and policy limits are validated. Domain services own state transitions and final decisions.

## Consequences

The product can operate partially during AI outages. Provider changes remain behind adapters. Invalid outputs create no evidence. Gold datasets and regression tests are required before increasing AI authority/reliability.
