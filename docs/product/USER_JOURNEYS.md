# User Journeys

## Journey 1 — First useful plan

1. User creates account and chooses `Java Backend Intern`.
2. User sets target date and default daily time.
3. User completes a 5–10 minute diagnostic.
4. System shows knowledge gaps and confidence, without pretending precision.
5. User receives today's 1–3 tasks with durations and reasons.

Acceptance:

- Abandoning diagnostic preserves progress safely.
- User identity is server-derived.
- No recommendation violates a hard prerequisite.
- Total planned minutes fit the budget within configured tolerance.

## Journey 2 — Complete and replan

1. User starts a task.
2. User reads a selected resource section and performs practice.
3. User submits recall/application response.
4. Evaluation creates validated evidence.
5. State updates once and planner creates the next decision.

Acceptance:

- Repeated submission is idempotent.
- AI failure does not lose the attempt; it can become `NEEDS_REVIEW` or retry safely.
- Recommendation explanation references updated evidence/review state.

## Journey 3 — “I only have 30 minutes”

1. User changes today's time from 90 to 30 minutes.
2. Planner preserves completed work and replaces only unstarted plan items.
3. System chooses short task variants or micro-assessment.

Acceptance:

- No punishment or permanent reduction of mastery.
- In-progress task is not silently discarded.
- Plan revision history remains auditable.

## Journey 4 — Learner struggles

1. User fails or partially completes similar evidence twice.
2. Planner avoids repeating the identical variant indefinitely.
3. System inserts prerequisite review or remedial task.
4. Successful verification can resolve the misconception.

## Journey 5 — Missed day

1. User returns after one or more missed days.
2. Overdue tasks are not all copied forward.
3. Planner recomputes from current time, review urgency, and evidence.
4. User sees a fresh achievable plan.

## Journey 6 — Goal completion

1. Required terminal concepts meet mastery and confidence thresholds.
2. A mastery check verifies critical application concepts.
3. System marks goal completed and explains remaining optional gaps.
4. Completion can be reversed only through explicit policy/review decay, not an arbitrary AI decision.

## Journey 7 — Understand the route

1. User opens the visual goal map from Today.
2. The map highlights the current task, mastered concepts, ready concepts, review-due
   concepts, and blocked concepts.
3. User selects a blocked concept and sees the prerequisite path and current blocking
   reason.
4. User returns to Today without losing task or map context.

Acceptance:

- Map state comes from the same graph version and knowledge snapshot used by the planner.
- Visual color is not the only status cue; labels/icons and keyboard-accessible detail are provided.
- Large graphs support progressive disclosure rather than rendering an unreadable full curriculum by default.
- The map does not allow the client to mark mastery, waive prerequisites, or complete a goal.
