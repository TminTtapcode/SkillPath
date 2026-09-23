# Plan: Phase 8 — Onboarding & Pedagogical Pivot

## Status

`DRAFT` (Awaiting P7 completion for domain changes. P8.0 and P8.1 UI prototyping approved on 2026-09-23).

## Objective and user story

**Objective:** Redefine the learner's onboarding experience and the pedagogical structure of daily tasks. Introduce a visual "Mental Map" for system architecture onboarding, strictly define a `Task = Learn + Practice + Check` structure, formalize Review as a first-class planner candidate, and support seamless switching between multiple learning tracks (goals) without losing progress.

**User Story:**
- As a new learner, I want to understand what a generic Web System is (Frontend, Backend, Database) through an interactive map *before* I commit to a specific track like Java Backend.
- As a learner, when I tackle a task, I want to study real resources, practice formatively without penalty, and then take a formal check to prove my knowledge.
- As a learner, I want the system to automatically balance reviewing overdue concepts with learning new concepts based on urgency and available time.
- As a learner, I want to be able to switch to a different track (e.g., Frontend) while saving my progress on my current track (e.g., Backend).

## Authoritative references

- `PROJECT_CONTEXT.md` (Product vision and MVP learning loop)
- `docs/plans/PHASE_ROADMAP.md` (Phase delivery schedule)
- `AGENTS.md` (Architecture and pedagogical rules)

## Current-state evidence

- Phase 7 (Adaptive Loop) is currently `IN_PROGRESS`.
- Onboarding drops users directly into goal selection without context.
- Tasks are abstract nodes without structured Learn/Practice/Check boundaries.
- The planner focuses on new dependencies; Review exists but lacks a formal budget-aware prioritization policy alongside new items.
- Switching active goals currently discards or overrides context implicitly, lacking a clear "Pause & Switch" mechanism that retains multi-track progress.

## Scope

### P8.0 Pedagogical Contract
- **Architecture Onboarding ≠ Knowledge Graph:** Onboarding is an educational mental map (Generic Web Architecture), decoupled from strict prerequisite relationships or mastery logic.
- **Practice ≠ Assessment Evidence:** Practice is formative and generates immediate feedback but *zero* assessment evidence.
- **Review = First-class Planner Candidate:** `REVIEW_DUE` items compete directly with `NEW` learning items in the planner's candidate pool based on urgency.
- **Task = Learn + Practice + Check:** A single learning unit is composed of these three distinct types of content.

### P8.1 Onboarding
- Implement Generic Web Architecture mental map UI.
- Nodes: Frontend, Backend, Database, Network.
- Interaction: Clicking a node displays an explanation, a simple example, and future concepts the user will encounter.

### P8.2 Learning Content Model
- Define `Resource`, `ResourceVersion`, and `TaskResource` domain models to provide real, provenance-backed study materials instead of raw string prompts.

### P8.3 Practice
- Implement curated formative practice flow prior to formal assessment checks.

### P8.4 Review-aware Planner
- Update the planner algorithm to use a budget-aware priority policy handling both `REVIEW_DUE` and `NEW` items. No hard-coded percentages.

### P8.5 UX Integration
- Connect Learn, Practice, and Check tabs in the Task view.
- Enable multi-track goal switching.

### P8.6 E2E Pedagogical Validation
- Full integration test of the new learner journey.

## Design

### 1. Generic System Architecture Onboarding (P8.1)
- **Mental Map:** A simple, high-level map (Frontend, Backend, Database, Network).
- **Exclusion:** No prerequisite arrows, mastery levels, or planner logic. This is purely an educational onboarding tool.

### 2. Content Model (P8.2)
- **Learn:** Powered by a new `Resource` domain.
  - `ResourceVersion` (title, url, source, language, provenance).
  - `TaskResource` (resourceVersionId, section locator, estimatedMinutes).
- **Practice:** Formative, curated exercises (Level 1/2: Recall and Application).
- **Check:** The existing Phase 7 Assessment flow. Generates evidence, updates Knowledge State, and schedules Review.

### 3. Review-aware Planner (P8.4)
- **Invariant:** If an active goal has due review items, the planner must consider those items as first-class candidates before allocating remaining budget to new knowledge.
- **Policy:** The planner evaluates a unified candidate pool (`REVIEW_DUE` and `NEW`). Trade-offs are made based on urgency. If review is critically overdue, prioritize it. If review exists, reserve minimum capacity. Remaining budget goes to new learning.

### 4. Multi-Track Goal Switching (P8.5)
- Support holding multiple learning goals (e.g., Java Backend, React Frontend).
- Securely pause the current planner state and load the new goal's graph and knowledge state without data loss.

## Dependencies & Rollout

- **Hard Dependency:** Phase 8 domain and planner modifications (P8.2 - P8.6) **CANNOT** begin until Phase 7 is explicitly marked `DONE`.
- **Parallel Work:** P8.1 (Onboarding UI prototype) and documentation updates can proceed immediately.
