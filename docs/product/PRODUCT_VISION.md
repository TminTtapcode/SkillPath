# Product Vision

## Problem

Learners often know their destination but not the next highest-value action. Static roadmaps overwhelm them, ignore prior knowledge, and become stale when available time or performance changes. Generic AI plans are easy to generate but rarely maintain evidence-backed state or replan consistently.

## Vision

SkillPath turns a large goal into the shortest defensible learning route and shows only the work that matters now. It continuously reroutes from actual evidence without punishing missed days.

## Product principles

1. **Next action over giant roadmap.** The primary screen is Today, not a long curriculum.
2. **Learning compression.** Exclude low-ROI topics that are unnecessary for the selected goal.
3. **Evidence over self-report alone.** Self-assessment starts the process; observed work refines it.
4. **Learning by doing.** Prefer learn → practice → recall → apply.
5. **Adaptive, not chaotic.** Replanning follows versioned deterministic rules.
6. **Explainable.** The learner can understand why a task was selected or blocked.
7. **Respect reality.** Plans adapt to actual time and missed work without shame mechanics.

## Initial target user

A student or junior developer who wants to become ready for a Java Backend Internship, has uneven Java/Spring/SQL/Web knowledge, can study 20–180 minutes per day, and needs guidance on what to do next.

Java Backend is the first curated track used to validate the adaptive engine. The
long-term product may support other IT specializations such as frontend, mobile,
DevOps/cloud, data, QA, security, and adjacent roles. New tracks reuse the same
versioned graph, evidence, progress, learning-task, and planner contracts; they must
not add subject-specific branches to the planner core.

## Learner orientation

Today remains the primary action screen. A secondary visual goal map helps the learner
understand where they are, what is complete, what is ready, what is blocked, which
prerequisites cause the block, and how the current recommendation advances the goal.
The map is an explainable projection of the same versioned graph and knowledge state,
not a separate source of prerequisite or completion truth.

## Value proposition

Given a goal, target date, available time, and observed performance, SkillPath chooses the next task with the highest expected learning value that the learner is ready to attempt.

## Success outcomes

- User reaches the first useful Today plan within 10 minutes.
- At least 70% of generated daily plans fit the declared time budget without manual deletion.
- Replanning responds to completion, failure, and reduced time on the next plan.
- Recommendations expose a reason code and prerequisite explanation.
- A learner can locate the current task and its blocking/unlocked path on the visual goal map without reading the full curriculum specification.
- Dogfood user completes at least 14 days and reports reduced time deciding what to study.

These are validation targets, not guaranteed business metrics.

## Non-goals

SkillPath is not an LMS, course marketplace, video host, social network, job board,
generic chatbot, or replacement for authoritative learning resources. Supporting
multiple IT tracks later does not turn the MVP into an uncurated learn-anything
platform.
