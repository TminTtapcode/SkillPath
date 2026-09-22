# Technology Stack

## Backend

- Java 21
- Spring Boot 3.x
- Spring Web, Validation, Data JPA, Security
- Maven Wrapper
- Flyway
- MySQL Connector/J
- JUnit 5, Mockito, Spring Boot Test, Testcontainers MySQL, ArchUnit

The Phase 1 pin is Spring Boot 3.5.16 with Maven Wrapper 3.9.16; Spring-managed
transitives are recorded through its BOM. Do not use preview Java features in MVP.

## Frontend

- React + TypeScript + Vite
- React Router
- A query/cache library for server state
- Form/schema validation library chosen once at bootstrap
- Vitest + React Testing Library
- ESLint and formatter

Phase 1 pins React 19.3.0, React Router 8.4.0, TanStack Query 5.103.2, React Hook
Form 7.88.0, Zod 4.6.5, Vite 8.3.0, TypeScript 5.9.3, Vitest 5.0.1, ESLint 10.11.0,
and Prettier 3.9.8 in `package-lock.json`. TypeScript 5.9 is deliberate: the selected
typescript-eslint and OpenAPI generator versions do not accept TypeScript 7.

Avoid adopting a large UI framework or global state library without demonstrated need.

The visual goal-map library is not selected yet. Run the approved React Flow versus
Cytoscape.js spike in `docs/research/OPEN_SOURCE_ADOPTION.md` before proposing a
dependency. Keep graph status and eligibility on the server; a visualization library
renders the roadmap contract and does not become a domain engine.

## Data and tooling

- MySQL 8.4 LTS as system of record
- MySQL Workbench for local inspection and query analysis
- Flyway as schema authority
- Docker Engine/Desktop and Compose v2
- OpenAPI for HTTP contract

## Deferred technology

Redis, message broker, search engine, Kubernetes, service mesh, GraphQL, native mobile stack, vector database, and analytics warehouse require measured need plus ADR.

BKT, IRT, DKT, reinforcement learning/learned ranking, and FSRS are also deferred
policy/model choices. They require data, offline evaluation, versioned migration, and
approval; their presence in another open-source tutor is not sufficient justification.

## Environment profiles

- `test`: isolated tests; integration tests use Testcontainers MySQL.
- `local`: application on host, MySQL via Compose.
- `compose`: full application containers after vertical slice.
- `production`: immutable images, controlled migrations, managed secrets/database.
