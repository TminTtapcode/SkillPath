# Project Context

## Product

- Working name: **SkillPath**
- Category: adaptive learning planner
- Core promise: give the learner a small, achievable plan for today and recalculate the route from observed performance
- Initial audience: students and junior developers pursuing Java Backend Internship readiness
- Product horizon: expand to multiple IT specializations after validating the first curriculum; Java Backend is the initial track, not a permanent domain boundary
- Product metaphor: Google Maps for learning — show the next move, reroute when reality changes

## Current phase

**Phase 1 — Engineering foundation and thin goal slice completed locally.** The
repository now contains runnable backend/frontend applications, MySQL migrations,
tests, CI, and app containers. Phase 2 (Knowledge Graph) has not started.

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
- MySQL logical data model and migration rules: defined; Phase 1 identity, Spring
  Session, goal, idempotency, and template-seed migrations are implemented.
- Core domain specifications: defined at v1 design level.
- Learning task and adaptive loop: defined at v1 design level.
- Learner-facing visual goal-map/read-model direction: defined for MVP with accessibility and progressive-disclosure constraints.
- Open-source adoption register, license/provenance workflow, visual-map spike, and post-MVP algorithm evaluation path: defined.
- API contract: Phase 1 endpoints are published in `docs/api/openapi-v1.yaml`; frontend
  TypeScript types are generated and checked for drift.
- AI boundary: defined.
- Development, testing, and Docker strategies: defined.

## Known open decisions

- Final public brand/domain and trademark availability.
- AI provider/model and cost limits.
- Initial curated Java Backend curriculum dataset and licensing of resources.
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

## Next approved work sequence

1. Discover/design Phase 2 Knowledge Graph using the approved plan workflow.
2. Run the bounded React Flow versus Cytoscape.js visualization spike before selecting
   a graph rendering dependency.
3. Continue Assessment, Knowledge State, Learning Task, Planner, and Adaptive Loop in
   roadmap order; keep curriculum data generic across IT specializations.

## Context maintenance

Update this file after each approved milestone. Keep it factual: completed, in progress, blockers, accepted decisions, known problems, and next approved task. Do not use it as a daily diary.
