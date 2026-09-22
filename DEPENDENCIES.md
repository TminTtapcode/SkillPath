# Dependency Inventory

Last reviewed: 2026-09-23. Exact Java transitive versions are resolved by the
Spring Boot 3.5.16 BOM; exact Java and npm graphs are reproducible from `pom.xml`
and `frontend/package-lock.json`. Re-run the audit process before every upgrade.

## Backend

| Component | Version | License | Purpose / exit path |
|---|---:|---|---|
| Java | 21 | GPL-2.0-with-classpath-exception (OpenJDK builds) | Runtime; remain on supported LTS |
| Maven Wrapper | 3.9.16 | Apache-2.0 | Reproducible build; replace with a later verified wrapper |
| Spring Boot | 3.5.16 | Apache-2.0 | Application/BOM; ordinary Spring upgrade path |
| Spring Web, Validation, Security, Data JPA, Actuator | BOM managed | Apache-2.0 | HTTP/security/persistence/health; isolate behind module contracts |
| Spring Session JDBC | BOM managed | Apache-2.0 | Persistent browser sessions; replace through session contract |
| Flyway Core/MySQL | 11.7.2 (BOM managed) | Apache-2.0 | Sole migration runner; migration files remain portable SQL where practical |
| MySQL Connector/J | BOM managed | GPL-2.0 with Universal FOSS Exception | MySQL JDBC driver; replace at datasource boundary |
| springdoc-openapi | 2.8.13 | Apache-2.0 | Runtime API discovery; canonical client contract remains the checked-in OpenAPI file |
| Testcontainers | BOM managed | MIT | MySQL integration tests only |
| ArchUnit | 1.4.1 | Apache-2.0 | Architecture tests only |

## Frontend

| Package | Version | License | Purpose / exit path |
|---|---:|---|---|
| React / React DOM | 19.3.0 | MIT | UI runtime |
| React Router | 8.4.0 | MIT | SPA routing; routes are application-owned |
| TanStack Query | 5.103.2 | MIT | Server-state lifecycle; fetch client remains application-owned |
| React Hook Form | 7.88.0 | MIT | Form state |
| `@hookform/resolvers` | 5.2.2 | MIT | Form/schema adapter |
| Zod | 4.6.5 | MIT | Client validation; server remains authoritative |
| TypeScript | 5.9.3 | Apache-2.0 | Compiler; current lint and OpenAPI tools do not support TS 7 |
| Vite / React plugin | 8.3.0 / 6.1.1 | MIT | Build/dev server |
| Vitest / Testing Library / jsdom | 5.0.1 / 16.3.3 / 30.1.1 | MIT | Tests only |
| ESLint / typescript-eslint | 10.11.0 / 8.70.1 | MIT | Static analysis |
| Prettier | 3.9.8 | MIT | Formatting |
| openapi-typescript | 7.13.0 | MIT | Generates checked client types from the OpenAPI contract |

The complete exact npm graph is in `frontend/package-lock.json`. `npm audit` reported
zero known vulnerabilities at the review date.

## Images and CI actions

| Artifact | Pin | Use |
|---|---|---|
| `mysql` | `8.4` | System of record and Testcontainers |
| `maven` | `3.9.16-eclipse-temurin-21` | Backend image builder |
| `eclipse-temurin` | `21-jre-noble` | Backend runtime |
| `node` | `24.19.0-alpine` | Frontend image builder |
| `nginxinc/nginx-unprivileged` | `1.29-alpine` | Non-root frontend runtime/proxy |
| `actions/checkout` | `fbc6f3992d24b796d5a048ff273f7fcc4a7b6c09` | CI checkout |
| `actions/setup-java` | `b6effb05e454b25005698d916606bdc6ffcbf961` | CI JDK |
| `actions/setup-node` | `249970729cb0ef3589644e2896645e5dc5ba9c38` | CI Node |

Release builds should additionally record immutable OCI digests and produce an SBOM.
Container images aggregate packages with their own notices; inspect the selected
digest before production distribution.
