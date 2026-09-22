# ADR-0001: Modular Monolith

- Status: Accepted
- Date: 2026-09-22

## Context

The product has rich domain interactions but MVP scale and team size do not justify distributed operations.

## Decision

Use one Spring Boot modular monolith. Enforce module boundaries through contracts and architecture tests. Deploy as one backend application.

## Consequences

Transactions and local development stay simple. Modules must still avoid persistence/entity coupling so future extraction remains possible. Microservices require a new ADR backed by operational need.
