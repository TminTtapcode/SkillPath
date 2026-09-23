# Open-Source Adoption Research

## Status and authority

- Status: `REFERENCE`
- Last verified: 2026-09-23 for the Phase 6 visual-map candidates
- Scope: implementation acceleration, algorithm research, visual roadmap, assessment,
  review, and AI/tutor patterns.

This document is a research register, not a source of domain truth. Accepted ADRs,
architecture documents, and domain specifications take precedence. A repository's
README claim is not evidence that its algorithm, security, scalability, or learning
outcomes are valid for SkillPath.

No project in this register is approved as a dependency merely by being listed.
Dependency, code adaptation, vendoring, or forking requires the workflow and approval
gate below.

Phase 1 adopted only general framework/tool dependencies approved by its engineering
plan. Their exact versions, declared licenses, purpose, and exit paths are in the root
`DEPENDENCIES.md`; `THIRD_PARTY_NOTICES.md` records that no source or curriculum
content from the tutor/graph research register has been copied. The visual graph
libraries remain spike-only and are not present in the npm lockfile.

The Phase 6 same-fixture comparison and dependency-free renderer decision are
recorded in [PHASE_6_VISUAL_MAP_SPIKE.md](PHASE_6_VISUAL_MAP_SPIKE.md). A later
library adoption still requires separate owner approval.

## Adoption strategy

SkillPath keeps its own curriculum-neutral adaptive engine on Java 21, Spring Boot,
MySQL, React, and TypeScript. Open source may reduce implementation effort in bounded
areas, but must not become a second authority for mastery, prerequisites, planner
priority, goal completion, authorization, or persistence.

Classify every candidate as exactly one of:

| Classification | Meaning |
|---|---|
| `REFERENCE` | Read architecture, UX, tests, or algorithms; copy no implementation code |
| `SPIKE` | Build a disposable comparison/prototype before any production dependency decision |
| `DEPENDENCY_CANDIDATE` | May be proposed in an approved feature plan with version, license, security, and replacement strategy |
| `ADAPT_CODE_CANDIDATE` | Specific files/functions may be ported only after provenance and license review |
| `REJECTED` | Do not use or copy under current evidence |

Forking an entire tutor is not the default. A fork is allowed only when its product
boundary, architecture, license obligations, upgrade path, and operational model fit
better than implementing SkillPath's bounded module contracts.

## Verified candidate register

Repository facts and license must be rechecked at the exact tag/commit before use.

| Project | Verified license | Relevant capability | Classification | SkillPath use |
|---|---|---|---|---|
| [LearnGraph](https://github.com/fenago/LearnGraph) | MIT | Prerequisite graph, learner overlay, gaps, ZPD/ready-to-learn, review, React graph visualization | `REFERENCE` | Study graph UX, explanation, and test scenarios. Do not import its psychometric/learning-style model or LevelDB architecture. |
| [Vanderbilt Knowledge Spaces](https://github.com/vanderbilt-data-science/knowledge-spaces) | MIT | Knowledge Space Theory, inner/outer fringe, graph validation, paths | `REFERENCE`, possible `ADAPT_CODE_CANDIDATE` | Use theory and fixtures to validate eligibility/frontier behavior. Never enumerate all possible states for a large production graph. |
| [LearningMAP](https://github.com/ai-for-edu/LearningMAP) | Apache-2.0 | Knowledge graph → quiz → diagnosis → lesson → review UX loop | `REFERENCE` | Study orchestration and recovery UX. Its generated graph/memory output cannot become trusted SkillPath state without validation. |
| [OpenTutor](https://github.com/zijinz456/OpenTutor) | MIT | Content ingestion, quiz workspace, provider adapter, FSRS, grounded tutor | `REFERENCE` | Study learner UX and AI adapter boundaries. Its graph-driven LOOM/LECTOR flows are marked experimental and are not MVP policy. |
| [OATutor](https://github.com/CAHLR/OATutor) | MIT code; content attribution varies and includes CC BY 4.0 | BKT, authored hints/scaffolds, accessible assessment UX | `REFERENCE` | Research BKT and assessment design after SkillPath has calibration data. Treat code and content licenses separately. |
| [Tutor MCP](https://github.com/ArnaudGuiovanna/tutor-mcp) | MIT | Auditable evidence, deterministic pedagogy, prerequisite/review engine, MCP boundary | `REFERENCE` | Compare domain contracts, audit records, and future MCP adapter behavior. Do not replace the web backend core. |
| [Open Spaced Repetition Java-FSRS](https://github.com/open-spaced-repetition/java-fsrs) | Verify at selected release | Java FSRS scheduler available from Maven Central | `DEPENDENCY_CANDIDATE` for post-MVP evaluation | Keep deterministic interval policy v1. Evaluate FSRS offline only after sufficient review history and an approved policy migration. |
| [React Flow (`@xyflow/react`)](https://github.com/xyflow/xyflow) | MIT | Custom interactive React node/edge UI | `SPIKE`, preferred visual-map candidate | Compare accessibility, progressive disclosure, bundle/runtime cost, layout integration, and read-only roadmap UX. |
| [Cytoscape.js](https://github.com/cytoscape/cytoscape.js) | MIT | Graph analysis, layouts, and visualization | `SPIKE`, alternative visual-map candidate | Compare performance and layout quality for large graph neighborhoods against React Flow. |
| [JGraphT](https://github.com/jgrapht/jgrapht) | LGPL-2.1-or-later OR EPL-2.0 | Java graph structures and algorithms | `REFERENCE`; dependency only with explicit license review | Prefer small domain-owned DAG functions for MVP unless measured complexity justifies the dependency. |
| [adaptive-engine](https://github.com/esen-dashyam/adaptive-engine) | No repository license was visible when verified | Neo4j, IRT, BKT, LLM assessment experiments | `REJECTED` for code use | Publicly readable source is not permission to copy. Ideas require independent specification and primary research support. |
| [Moodle](https://github.com/moodle/moodle) | GPL family; verify selected release | Competency frameworks, learning plans, activity completion | `REFERENCE` | Study vocabulary/workflows only. Do not fork Moodle as the SkillPath foundation. |

## Integration map

| SkillPath area | Primary references | Permitted near-term outcome |
|---|---|---|
| Knowledge graph | LearnGraph, Knowledge Spaces | Domain fixtures for cycle, traversal, stable topology, prerequisite frontier, and visualization overlays |
| Assessment | OATutor, LearningMAP | Question/evidence scenarios and accessible assessment UX; no BKT in MVP |
| User knowledge state | Knowledge Spaces, OATutor | Compare replay fixtures and future calibrated models; keep v1 deterministic projection |
| Planner | Knowledge Spaces, LearnGraph, Tutor MCP | Treat outer fringe as derived eligibility, preserve SkillPath's versioned scoring and stable tie-breakers |
| Review | OpenTutor, Java-FSRS | Keep interval policy v1; prepare an offline benchmark contract for later FSRS evaluation |
| AI adapter | LearningMAP, OpenTutor, Tutor MCP | Provider-neutral structured contracts, timeout/fallback patterns, and audit ideas only |
| Visual roadmap | LearnGraph, React Flow, Cytoscape.js | Run a bounded read-only graph UI spike before selecting a library |

## Frontier terminology

Knowledge Space Theory terminology may be exposed in engineering/debug documentation,
but does not add new persisted authority:

```text
innerFringe = mastered nodes adjacent to at least one not-yet-mastered dependent

outerFringe = not-yet-mastered nodes whose hard prerequisites meet the current
              policy threshold and that have at least one active time-fit task

blocked     = not-yet-mastered nodes with at least one unsatisfied hard prerequisite
```

These sets are derived from a versioned graph, progress/review snapshot, planner
policy, task catalog, and `projectionAsOf`. They may be included in the roadmap read
model as overlays. Do not store them as independent mastery or prerequisite truth.

## Visual-map spike

Before adding a graph UI dependency, create an approved bounded spike comparing React
Flow and Cytoscape.js using the same roadmap fixture.

Required fixture:

- 25–50 visible nodes from a larger Java Backend goal graph;
- direct and transitive prerequisite paths;
- `knowledgeStatus` plus separate `current`, `ready`, and `blocked` overlays;
- selected-node explanation and expansion of one bounded neighborhood;
- keyboard navigation, focus order, screen-reader labels, and non-color status cues;
- pan/zoom/minimap behavior on desktop and a usable reduced mobile view;
- no client mutation of mastery, prerequisite waiver, or goal completion.

Record bundle impact, render/interaction latency, layout determinism, testability,
maintenance activity, license, attribution, export needs, and fallback behavior. The
winning library still requires approval as a material dependency in the implementation
plan.

## Review-model evolution

Do not replace review policy v1 with FSRS because another application uses it.

1. Ship the deterministic `1, 3, 7, 14, 30, 60`-day policy and record review events.
2. Define an anonymized/reproducible offline evaluation dataset and metrics.
3. Compare v1 with Java-FSRS using fixed clocks and replayable histories.
4. Evaluate calibration, workload, retention proxy, cold-start behavior, and failure
   paths.
5. Adopt only through a versioned review-policy migration, tests, documentation, and
   an approved ADR/plan. Historical decisions retain their original policy version.

The same evidence gate applies to BKT, IRT, DKT, reinforcement learning, and learned
ranking. They are research candidates, not MVP dependencies.

## License and provenance workflow

Before adding a dependency or adapting code:

1. Identify the exact repository, owner, tag/commit, package coordinates, and intended
   files/capability.
2. Read the license file at that exact revision. A public repository without a license
   is `REFERENCE` only; no code may be copied.
3. Check code and bundled data/content separately. Record attribution, notice,
   copyleft, redistribution, model-weight, dataset, and trademark obligations.
4. Confirm architecture fit, maintenance/activity, releases, security policy,
   transitive dependencies, known vulnerabilities, network/data behavior, and exit
   strategy.
5. Choose `REFERENCE`, `SPIKE`, `DEPENDENCY_CANDIDATE`, `ADAPT_CODE_CANDIDATE`, or
   `REJECTED` and record the evidence in the feature plan.
6. Obtain approval for material dependencies, license obligations, new infrastructure,
   or domain-policy changes before implementation.
7. Pin a supported version, retain copyright/license notices, and update a future
   `THIRD_PARTY_NOTICES`/SBOM as applicable.
8. Add contract, deterministic, security, migration, fallback, and removal tests.
9. Re-verify license/security on upgrades; do not assume the latest revision keeps the
   same terms.

This workflow is engineering due diligence, not legal advice. Escalate uncertain or
commercially material license questions to qualified review.

## Rejection rules

Reject or keep reference-only when any of the following applies:

- no license or incompatible obligations;
- AI directly writes mastery, satisfies prerequisites, selects final planner output,
  completes goals, or bypasses validated contracts;
- project requires Neo4j, PostgreSQL, Redis, a broker, or microservices without an
  accepted SkillPath ADR;
- learner/profile data collection exceeds SkillPath's purpose or privacy policy;
- algorithm has no deterministic replay/version strategy;
- adopting it is more costly than a small tested domain-owned implementation;
- source, model, dataset, or curriculum provenance is unclear.

## Maintenance

Recheck this register immediately before each related phase begins and record the
selected tag/commit in its approved plan. Do not update architecture merely because a
candidate README adds a new feature.
