# Phase 6 visual roadmap spike

Date: 2026-09-23. Classification: `SPIKE` only; no graph package was added to
SkillPath's frontend dependencies or lockfile.

## Decision

Keep the dependency-free SVG diagram with the synchronized semantic concept list
for the current published graph and the 200-node server bound. This avoids a new
dependency while preserving explicit keyboard selection, names, statuses, and
prerequisite explanations in the list. The viewport can scroll horizontally on a
narrow screen; the list remains the primary small-screen and screen-reader route.
The roadmap fetches bounded pages and refuses to merge differing graph, policy,
progress/review, plan, revision, or `projectionAsOf` stamps. This decision does not
claim the current SVG is a general-purpose large-graph editor or an accessible
substitute for all library features. Revisit visualization if a future graph exceeds
the bounded view or user testing finds the list/scroll interaction insufficient.

Neither React Flow nor Cytoscape.js is approved as a production dependency by this
spike. Adding either still needs owner approval and a dependency/license audit.

## Common fixture and method

- Exact spike packages: `@xyflow/react@12.11.6`, `cytoscape@3.34.3`, and build-only
  `esbuild@0.27.0`, installed in a disposable directory outside `frontend`.
- Both browser prototypes used the same 50 numbered concepts, 49 directed
  prerequisite edges in a chain, and the same fixed 10-column node coordinates.
  A chain exercises both direct and transitive prerequisites. The existing SkillPath
  renderer and frontend tests additionally exercise `knowledgeStatus`, independent
  current/ready/blocked overlays, selected-node explanation, and page expansion.
- Chromium headless rendered the prototypes from a local HTTP server in a 1200×720
  desktop viewport. Timing began before mounting and ended when 50 nodes and 49
  edges were present (React Flow DOM) or the graph render settled (Cytoscape canvas).
  Three successful measurements per package; timings are noisy developer-machine
  observations, not product latency guarantees.
- Browser bundles were built with esbuild, minified, then gzipped in memory. The
  React Flow *incremental* figure excludes React and ReactDOM, which SkillPath
  already ships. The full React prototype figure includes both; numbers are not a
  Vite production delta. No network calls, learner data, or curriculum content were
  used in the fixture.

| Candidate | License | Prototype render samples, ms | Median, ms | Minified JS | Gzip JS |
|---|---|---:|---:|---:|---:|
| React Flow `12.11.6` | MIT | 68.0, 41.7, 40.8 | 41.7 | 186,852 B incremental | 60,966 B incremental |
| Cytoscape.js `3.34.3` | MIT | 79.9, 47.5, 61.2 | 61.2 | 444,568 B | 142,064 B |

The full React prototype with React and ReactDOM measured 406,102 B minified and
128,247 B gzipped. One React run remained pending under the first virtual-time
budget; it was rerun successfully with a longer budget and excluded from the three
completed samples. The sample is too small to infer a reliable performance winner.

## UX and integration comparison

| Concern | React Flow | Cytoscape.js | Current SVG + semantic list |
|---|---|---|---|
| Keyboard/screen reader | Official docs describe tabbable nodes/edges, keyboard activation and navigation, ARIA labels. A SkillPath-specific equivalent list is still needed for dense graphs. | Canvas rendering needs an explicit semantic DOM companion for this product; the official core API does not by itself establish equivalent node-by-node screen-reader navigation. | Buttons in the list and keyboard-selectable diagram nodes expose names, state, and selected detail. List works without interpreting spatial layout. |
| Placement/expansion | Fixed positions and controlled nodes fit the snapshot model; built-in viewport controls. | `preset` positions were deterministic; core pan/zoom and graph methods are strong for exploration. | Stable topological columns; selected detail and bounded cursor expansion. Horizontal scroll replaces pan/zoom; no minimap. |
| Mobile | Zoom/fit can help, but 50-node touch and readable labels need device testing. | Canvas gestures can help, but readable labels and semantic fallback need device testing. | CSS keeps a semantic single-column list at narrow widths; the diagram is horizontally scrollable. Real-device usability remains to be tested. |
| Maintainability | React component model fits frontend, but adds graph-specific state/CSS and bundle cost. | Separate imperative canvas integration and accessibility bridge are more work for this read-only view. | No new runtime package; custom layout is deliberately bounded and not intended for unbounded graphs. |
| Export/attribution | MIT; check React Flow's attribution/support terms before adoption. SVG export is possible with additional handling. | MIT; canvas export APIs exist, but not required by P6. | Native SVG can be saved/rendered by browsers; P6 does not expose an export command. |

React Flow's [accessibility documentation](https://reactflow.dev/learn/advanced-use/accessibility)
and [package source](https://github.com/xyflow/xyflow/blob/main/packages/react/package.json),
plus Cytoscape.js's [official API and license page](https://js.cytoscape.org/),
were checked for the comparison. The absence of a documented Cytoscape core
screen-reader node workflow is an inference, not a claim that one cannot be built.
No source code, styles, or curriculum content from either project were copied into
SkillPath.

## Limits and follow-up

- The browser benchmark did not implement identical selection, overlays, minimap,
  semantic fallback, or mobile interactions inside both disposable prototypes. It
  measures rendering/bundle cost of the common graph fixture; those UX capabilities
  were assessed from official APIs/docs and against SkillPath's actual read-only UI.
- The 50-node fixture covers the requested 25–50 visible range. Backend policy/API
  tests separately exercise a 50-node paged roadmap and the 200-node graph bound;
  they are correctness tests, not a production load test.
- A headless Chromium check of the built SkillPath frontend with a synthetic
  50-concept roadmap loaded its first 25-concept page in a narrow window. Chromium
  reported a minimum effective layout width of 512 px even when requested at 390 px:
  `bodyScrollWidth=497`, `panelWidth=465`, `viewportWidth=423`,
  `viewportScrollWidth=5068`, and one semantic-list column. Thus page-level overflow
  was absent at 512 px while the graph itself remained independently scrollable.
  This is not a verified 390 px device or assistive-technology test.
- Run real-device and assistive-technology checks before a public accessibility
  claim. If the graph grows past 200 nodes, design server-side neighborhood traversal
  and progressive layout rather than increasing the current bound blindly.
