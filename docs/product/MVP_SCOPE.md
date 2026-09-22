# MVP Scope

## MVP hypothesis

A narrow evidence-backed daily planner can help a Java Backend learner progress faster than a static roadmap by reducing decision overhead and adapting task choice to knowledge gaps and available time.

## In scope

### Account and goal

- Sign up/sign in/sign out.
- Create one active goal from the curated `Java Backend Intern` template.
- Set target date, timezone, and default daily minutes.
- Temporarily override today's available minutes.

### Diagnostic and knowledge state

- Short curated diagnostic using objective and small practical questions.
- Question-to-knowledge mapping.
- Concept-level evidence and four dimensions: recognition, understanding, recall, application.
- Mastery/confidence projection and review schedule.

### Daily learning

- Today plan containing 1–3 ordered tasks.
- Curated resource section, practice, and recall/assessment task types.
- Start, complete, skip, and report blocked.
- Mini assessment at the end of applicable tasks.
- Automatic replan after new evidence or time-budget change.
- Read-only visual goal map showing current, ready, blocked, mastered, and review-due concepts with prerequisite explanations.

### Planner

- Deterministic prerequisite gate and versioned priority calculation.
- Task time-fit and stable tie-breaker.
- Explanation/reason codes and blocked prerequisites.

### Administration

- Seeded curriculum via migrations/import tooling.
- Minimal protected curator/admin APIs; a polished admin UI is not required.

## Out of scope

- Multiple public curricula or learning every subject.
- Production support for additional IT tracks; the architecture remains extensible, but the MVP ships only the curated `Java Backend Intern` track.
- User-generated public curriculum.
- Social feed, groups, leaderboard, competitive streaks.
- Native mobile application and offline mode.
- Video hosting and marketplace/payment.
- GitHub/CV analysis in MVP.
- Voice tutor, live mentor marketplace, automatic job applications.
- Microservices, Kafka, Kubernetes, Elasticsearch, mandatory Redis.
- Fully AI-generated curriculum or final planner decisions.

## MVP completion scenario

Given a new user with a Java Backend Intern goal and 60 minutes available, the user completes a diagnostic, receives a three-task plan, completes practice and recall, and receives a changed next plan based on concept evidence. The full path is secured, persisted, tested, and reproducible.

The user can also open the visual goal map and see the same state and prerequisite
explanation that produced the Today plan.

## Release gates

- One end-to-end adaptive loop works with curated content.
- No cross-user data access in authorization tests.
- Planner and knowledge updates are deterministic and replayable.
- Clean MySQL database can be created entirely by Flyway.
- Application runs locally using documented commands and Docker Compose.
- Core failure modes have user-visible recovery states.
- The visual map remains consistent with the published graph version, knowledge-state snapshot, and planner explanation.
