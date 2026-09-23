# Project Context

## Product

- Working name: **SkillPath**
- Category: adaptive learning planner
- Core promise: give the learner a small, achievable plan for today and recalculate the route from observed performance
- Initial audience: students and junior developers pursuing Java Backend Internship readiness
- Product horizon: expand to multiple IT specializations after validating the first curriculum; Java Backend is the initial track, not a permanent domain boundary
- Product metaphor: Google Maps for learning — show the next move, reroute when reality changes

## Current phase

**Phase 2 — Knowledge System completed locally.** The repository now contains the
versioned Java Backend graph schema and seed, deterministic validation/traversal,
published read APIs, and secure atomic curator publication. Phase 3 (Assessment) has
not started. Validation evidence is recorded in
`docs/plans/PHASE_2_KNOWLEDGE_SYSTEM.md`.

## Confirmed decisions

- Build a web MVP first; no native mobile app in MVP.
- Use a modular monolith, not microservices.
- Backend: Java 21 and Spring Boot 3.x.
- Frontend: React + TypeScript + Vite.
- Database: MySQL 8.4 LTS.
- MySQL Workbench is a local GUI/inspection tool, not migration authority.
- Flyway owns schema evolution.
- Planner v1 is deterministic and explainable.
- The primary experience remains Today, supported by a visual goal map showing progress, prerequisites, blocked concepts, and the reason for the next task.
- Curriculum and graph contracts must support additional IT specializations without introducing subject-specific rules into the planner core.
- Open-source projects are inputs to bounded research/spikes, not architecture or domain authorities. Forking an existing tutor is not the default strategy.
- AI is an assistant behind validated contracts, not the product architect or final decision maker.
- The four core IP areas are Knowledge Graph, Assessment Model, User Knowledge State, and Planner Algorithm.
- Dockerize local infrastructure first. Containerize backend/frontend after the first end-to-end vertical slice runs locally.
- Redis, queues, Kubernetes, microservices, social features, marketplace, voice tutor, and complex gamification are outside MVP.

## MVP learning loop

1. User defines a goal, target date, and daily time budget.
2. User completes a short diagnostic.
3. System creates an evidence-backed knowledge state.
4. Planner chooses one small set of tasks fitting today's time.
5. User learns, practices, and recalls.
6. Evaluation produces concept-level evidence.
7. Knowledge state and review schedule update.
8. Planner generates the next plan.

## Current documentation status

- Product vision and MVP scope: defined.
- System architecture/module boundaries: defined for MVP.
- MySQL logical data model and migration rules: defined; Phase 1 identity/session/goal
  and Phase 2 knowledge graph V6/V7 migrations are implemented and tested from clean
  and V5 upgrade paths.
- Core domain specifications: defined at v1 design level.
- Learning task and adaptive loop: defined at v1 design level.
- Learner-facing visual goal-map/read-model direction: defined for MVP with accessibility and progressive-disclosure constraints.
- Open-source adoption register, license/provenance workflow, visual-map spike, and post-MVP algorithm evaluation path: defined.
- API contract: Phase 1 identity/goal and Phase 2 published graph/admin lifecycle
  endpoints are published in `docs/api/openapi-v1.yaml`; frontend TypeScript types are
  generated and checked for drift.
- AI boundary: defined.
- Development, testing, and Docker strategies: defined.

## Known open decisions

- Final public brand/domain and trademark availability.
- AI provider/model and cost limits.
- Pedagogical review and expansion of the initial project-authored Java Backend graph;
  learning-resource licensing remains a later content decision.
- Which IT specialization follows Java Backend and the evidence required to prioritize it.
- Visual graph library selection after a React Flow versus Cytoscape.js spike.
- Whether later review data justifies a versioned move from interval policy v1 to FSRS.
- Hosting provider and production observability stack.
- Production hosting, observability, backup/restore, immutable image digests, and SBOM automation.
- Flyway 11.7.2 (managed by Spring Boot 3.5.16) warns that MySQL 8.4 is newer than its
  most recently tested MySQL line; clean and repeat migrations pass against MySQL 8.4,
  but upgrades require rechecking this compatibility.

## Phase 1 security decision

The web MVP uses a server-managed secure HTTP-only cookie session. Non-local cookies
must use `Secure` and an explicit `SameSite` policy; state-changing requests require
CSRF protection. Authentication rotates the session and logout invalidates it
server-side. A token-based browser flow requires deployment evidence and an approved
plan or ADR change.

## Implemented Phase 1 surface

- Spring Boot 3.5.16 modular monolith on Java 21; React 19/Vite 8 SPA on Node 24.
- MySQL 8.4 and Flyway V1–V5; Hibernate validates and never creates schema.
- Cookie session auth, CSRF, BCrypt cost 12, login lockout, persistent session registry,
  five-session cap, logout revocation, and ownership derived from the principal.
- Public goal-template list; authenticated idempotent active-goal create/read.
- Multi-stage non-root images, Compose app profile, pinned-action CI, run/test/audit scripts.
- SkillPath Design System (UI UX Pro Max): Minimalist Technical Swiss (Border-First)
  with Inter + JetBrains Mono typography, Slate neutrals, 8pt spatial grid, and 4–8px
  radii. Includes 5 pedagogical knowledge state tokens (Unreached, Learning, Review Due,
  Mastered, Blocked), 15.8:1 text contrast, accessible form error associations, and
  a Today Command Center layout with high-value task preview.

## Frontend design system baseline

- Architecture: Border-first, zero-gradient, zero-glassmorphism technical interface.
- Typography: Inter for UI; JetBrains Mono for syntax, IDs, and tabular figures.
- Tokens: Centralized CSS variables in `src/shared/styles/global.css` (`--sp-*`).
- Semantic Knowledge States: Teal 700 (`#0f766e`) for Mastered, Blue 600 (`#1d4ed8`) for Learning,
  Amber 700 (`#b45309`) for Review Due, Rose 700 (`#be123c`) for Blocked.
- Accessibility (WCAG 2.2 AA): 42–44px minimum touch targets, `aria-describedby` field error
  association, `aria-invalid` flags, `onBlur` validation, and `@media (prefers-reduced-motion)`.
- Screen Surface:
  - Header: Workstation navigation shell with track indicator and daily countdown.
  - Auth: Accessible Login and Register forms with field-level live descriptions.
  - Goal Setup: Track selection cards with topic tags and segmented study-minute pills.
  - Active Goal: "Today Command Center" displaying today's high-value task, daily budget,
    and active track specifications.

## Implemented Phase 2 surface

- Flyway V6 graph/version/node/relation/goal-mapping/lifecycle-audit schema and V7
  project-authored `JAVA_BACKEND` v1 seed: 17 nodes and 23 relations.
- Pure deterministic validation, cycle paths, stable topology, direct/transitive
  traversal, and non-persisted prerequisite-frontier fixtures.
- Public, published-only node/prerequisite/dependent and bounded goal-graph queries with
  version stamps and opaque cursors.
- `CURATOR`/`ADMIN` validation and publication with CSRF, principal-derived audit actor,
  idempotent replay, atomic retire/publish/audit, and one-winner concurrency behavior.
- Goal-owned HTTP adapter delegates through the public knowledge application contract;
  no cross-module persistence import or new graph dependency was introduced.

## Next approved work sequence

1. Discover/design Phase 3 Assessment using the approved plan workflow and the
   published knowledge-node/version contracts.
2. Keep the React Flow versus Cytoscape.js visualization comparison as a bounded later
   spike; Phase 2 adds no graph-rendering dependency or learner roadmap UI.
3. Continue Assessment, Knowledge State, Learning Task, Planner, and Adaptive Loop in
   roadmap order; keep curriculum data generic across IT specializations.

## Context maintenance

Update this file after each approved milestone. Keep it factual: completed, in progress, blockers, accepted decisions, known problems, and next approved task. Do not use it as a daily diary.
