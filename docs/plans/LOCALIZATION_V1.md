# Plan: L10 — Vietnamese/English Learner Experience

## Status

`DONE — owner approved, implemented, validated, and documented on 2026-09-23`

## Objective and user story

Deliver one coherent bilingual learner experience for the currently implemented
SkillPath flow.

As a learner, I can switch between Vietnamese and English from the application header
and see both interface text and project-authored learning content in the selected
language. My selection survives a browser reload. Changing presentation language must
not change question identity, answer keys, scoring, evidence, or diagnostic progress.

New registrations and goals default to the IANA timezone `Asia/Ho_Chi_Minh`. Existing
stored profile and goal timezones are not silently rewritten.

## Authoritative references

- `AGENTS.md`
- `DEVELOPMENT_RULES.md`
- `PROJECT_CONTEXT.md`
- `docs/adr/0001-modular-monolith.md`
- `docs/architecture/SYSTEM_ARCHITECTURE.md`
- `docs/architecture/MODULE_BOUNDARIES.md`
- `docs/architecture/DATABASE_DESIGN.md`
- `docs/domain/ASSESSMENT_MODEL.md`
- `docs/domain/KNOWLEDGE_GRAPH.md`
- `docs/api/API_CONTRACT.md`
- `docs/api/openapi-v1.yaml`
- `docs/development/TESTING_STRATEGY.md`
- `docs/plans/PHASE_3_ASSESSMENT.md`

## Current-state evidence

- Phase 3 is implemented through Flyway V9 and the checked-in OpenAPI contract.
- Static frontend labels, validation messages, empty/error states, and diagnostic
  interpretation text are currently English-only.
- The canonical goal template, 17 Java Backend knowledge nodes, and eight diagnostic
  questions/options are stored in English-only base columns.
- No locale field, translation table, locale resolver, or language selector exists.
- The browser API client does not send `Accept-Language`.
- Existing question option IDs and answer-key IDs are opaque and language-neutral, so
  localized labels can be added without changing deterministic scoring.
- The already-applied V9 diagnostic migration must not be edited. Localization requires
  additive forward migrations.
- The owner explicitly requested both UI and content localization on 2026-09-23.
- The timezone-only slice is already present locally: registration and goal setup use a
  shared `DEFAULT_TIMEZONE = 'Asia/Ho_Chi_Minh'`. Frontend format, lint, 7 tests, and
  production build passed after that change.
- The last complete Phase 3 backend regression passed 14 unit/architecture tests and 15
  integration tests. Localization implementation must rerun the full suite.

## Decisions proposed for owner approval

Approval of this plan accepts these v1 policies:

1. Supported BCP 47 locale tags are exactly `vi-VN` and `en`.
2. New browsers default to `vi-VN`; a saved browser choice takes precedence. The
   selector persists in `localStorage`, not the user profile, for this bounded slice.
3. The client sends the selected locale through standard `Accept-Language`; localized
   responses identify the resolved locale with `Content-Language` where applicable.
4. Unknown or unsupported locale values resolve to English. Missing translations fall
   back to the immutable English base content and never make a learning flow unavailable.
5. English remains the canonical base text in existing goal, knowledge, and assessment
   tables. Vietnamese is stored as an immutable translation overlay tied to the same
   goal template, graph/node version, or question version.
6. The initial localization seed covers the active Java Backend goal template, all 17
   published graph nodes, and all eight Phase 3 diagnostic questions/options.
7. Technical identifiers, code, commands, enum values, option IDs, graph IDs, question
   IDs, answer keys, scoring formulas, and evidence are never translated.
8. A learner may switch language during an in-progress diagnostic. The same pinned
   session question is re-rendered in the new locale; progress and accepted answers are
   unchanged.
9. Known frontend validation/error states are translated by stable error code. Unknown
   backend failures use a safe localized generic message plus correlation ID rather
   than exposing implementation details.
10. No new Maven or npm dependency is required. The locale dictionary and provider are
    project-owned TypeScript.

## Scope

### In scope

- Accessible Vietnamese/English selector in the persistent application header.
- Locale provider, typed dictionaries, interpolation, browser persistence, and
  `document.documentElement.lang` synchronization.
- Translation of all currently reachable learner UI: login, registration, goal setup,
  active goal, diagnostic question/result/expired/error states, navigation, and common
  async states.
- `Asia/Ho_Chi_Minh` defaults for new registration and goal creation.
- Locale-aware current goal-template content, diagnostic prompts/options, and result
  concept names.
- Locale-aware public knowledge read DTOs so later learner graph UI does not need a
  parallel translation mechanism.
- Additive MySQL localization tables and canonical Vietnamese seed data.
- Standard locale headers in the checked-in OpenAPI contract and generated client.
- Fallback, answer-key secrecy, ownership, idempotency, and translation-parity tests.
- Documentation and `PROJECT_CONTEXT.md` updates.

### Out of scope

- Machine translation, AI translation, curator translation UI, or external translation
  management services.
- Arbitrary locale upload or any locale beyond Vietnamese and English.
- Persisting locale to the user profile or synchronizing it across browsers/devices.
- Translating learner-entered names, emails, IDs, code, Git/HTTP/SQL commands, or
  historical answer payloads/evidence.
- Translating admin/curator-only screens that do not currently exist.
- Rewriting existing profile/goal timezone rows.
- Changing assessment scoring, evidence strength, mastery authority, or planner policy.

## Design

### Checkpoint L10.0 — Contract and locale policy lock

- Approve the ten policy decisions above.
- Add a small shared backend `SupportedLocale` value type/resolver that accepts only
  `vi-VN` and `en` and never trusts arbitrary locale input as a table/column name.
- Keep locale as presentation context passed through public application contracts; it
  must not enter scoring or evidence calculations.
- Extend architecture tests if needed to keep translation persistence private to its
  owning module.

Gate: owner-approved plan, no locale-dependent domain authority, and no new dependency.

### Checkpoint L10.1 — Additive localization schema and canonical seed

Add `V10__create_localization_schema.sql` with module-owned tables:

- `goal_template_translations(goal_template_id, locale, display_name, description,
  created_at)`;
- `knowledge_node_translations(graph_version_id, knowledge_node_id, locale, name,
  description, created_at)` with the existing same-version composite node foreign key;
- `question_version_translations(question_version_id, locale, prompt, options,
  created_at)`.

Each table has a unique identity over its owning version plus locale, a locale check
limited to `vi-VN`, JSON validity for localized question options, and foreign keys to
the immutable source row.

Add `V11__seed_vietnamese_localization.sql` with project-authored translations for the
active goal template, all 17 Java Backend nodes, and eight diagnostic questions. The
localized question option array must contain exactly the same opaque option IDs as its
English source; only labels change.

Gate: clean V1-to-V11 and upgrade V9-to-V11 migrations pass on MySQL 8.4; duplicate
locale rows, orphan translations, unsupported locales, and option-ID drift are rejected
by database/application validation as applicable.

### Checkpoint L10.2 — Locale-aware application contracts and reads

- Goal-template reads overlay localized display name/description with English fallback.
- Knowledge reads overlay localized node name/description with English fallback while
  preserving version, slug, topology, and cursor semantics.
- Assessment next-question reads overlay prompt and option labels for the requested
  locale after verifying option-ID parity. Submission continues to load canonical
  server-side options/answer keys and ignores locale.
- Assessment result composes localized concept names through the knowledge application
  contract. Scores/evidence remain byte-for-byte locale-independent.
- Localized responses set `Content-Language` to the resolved locale.

Gate: the same session/question/option IDs, score, evidence, and outbox payload are
produced in both locales; missing localization deterministically falls back to English.

### Checkpoint L10.3 — API and generated client contract

- Document optional `Accept-Language` on learner-facing content reads and diagnostic
  question/result endpoints.
- Restrict documented values to `vi-VN` and `en`; unsupported input uses the documented
  English fallback rather than a server error.
- Add `Content-Language` response headers to localized response contracts.
- Regenerate `frontend/src/shared/api/schema.d.ts` and make the central API client attach
  the selected locale without changing individual feature calls.

Gate: OpenAPI generation is deterministic, contract drift is clean, and answer-key or
translation-internal fields are absent from public schemas.

### Checkpoint L10.4 — Bilingual frontend and language switching

- Add a dependency-free typed localization provider with `vi-VN` and `en` dictionaries.
- Place an accessible language selector in the header on every route; persist only the
  supported locale token in `localStorage`.
- Translate all reachable UI strings, form labels, validation messages, buttons,
  loading/empty/error states, evidence-boundary language, and accessibility labels.
- Update the HTML `lang` attribute and preserve keyboard/focus behavior.
- On language change, invalidate locale-dependent queries. An in-progress diagnostic
  keeps the same session and selected option IDs while labels are refetched.
- Keep `Asia/Ho_Chi_Minh` as the visible default in registration and goal setup; users
  may still enter another valid IANA timezone.

Gate: route navigation and session state survive switching; no mixed-language learner
screen remains when all canonical translations exist.

### Checkpoint L10.5 — Audit, documentation, and container smoke

- Run backend unit/integration/architecture/migration/security regression.
- Run OpenAPI generation, frontend format/lint/tests/build, Compose config, dependency
  audit, and secret/diff checks.
- Rebuild the local app containers and smoke both locales against the V11 schema.
- Update API, database, module, assessment/knowledge, testing, roadmap/context, and this
  plan with exact validation evidence.

Gate: both locales complete the same diagnostic with identical authoritative records;
all applicable gates pass before this plan becomes `DONE`.

## Security, privacy, and failure behavior

- Locale is allowlisted before use; SQL selects locale through bound parameters only.
- Translation lookup never influences authentication, ownership, answer validation,
  scoring, idempotency hashes, evidence, or outbox payloads.
- Answer keys remain canonical server-only data and are not copied into translation
  rows or localized responses.
- `localStorage` contains only the non-sensitive locale tag.
- Malformed localized option JSON or option-ID mismatch fails safe to canonical English
  content and is surfaced to tests/operations; it cannot alter scoring.
- Missing Vietnamese content falls back per field/resource to English and returns the
  actual `Content-Language` resolution.
- Static UI dictionaries are complete at build/test time; missing keys fail tests rather
  than silently displaying a key in production.

## Expected files

### Backend and migrations

- `backend/src/main/resources/db/migration/V10__create_localization_schema.sql`
- `backend/src/main/resources/db/migration/V11__seed_vietnamese_localization.sql`
- shared supported-locale value/resolver under `backend/src/main/java/com/skillpath/shared/**`
- locale-aware goal application/persistence reads
- locale-aware knowledge application/persistence reads
- locale-aware assessment question/result reads
- affected controllers for request/response locale headers

### Frontend

- `frontend/src/shared/i18n/**`
- `frontend/src/shared/api/client.ts`
- `frontend/src/app/App.tsx`
- all currently reachable feature pages/components containing learner-visible text
- `frontend/src/shared/styles/global.css`
- focused localization/frontend tests
- generated `frontend/src/shared/api/schema.d.ts`

### Contracts and documentation

- `docs/api/openapi-v1.yaml`
- `docs/api/API_CONTRACT.md`
- `docs/architecture/DATABASE_DESIGN.md`
- `docs/architecture/MODULE_BOUNDARIES.md`
- `docs/domain/ASSESSMENT_MODEL.md`
- `docs/domain/KNOWLEDGE_GRAPH.md`
- `docs/development/TESTING_STRATEGY.md`
- `PROJECT_CONTEXT.md`
- this plan

Any user-profile locale persistence, external i18n dependency/service, AI translation,
or schema change outside these translation overlays is a material deviation requiring
owner review.

## Tests

### Unit

- Supported/unsupported locale normalization and English fallback.
- Vietnamese/English dictionary key parity and interpolation.
- Localized question option-ID parity, duplicate/unknown/missing IDs, and malformed JSON.
- Same canonical question and selections produce identical score/evidence in both locales.

### MySQL migration/integration

- Clean V1-to-V11 and upgrade V9-to-V11.
- Translation uniqueness, locale checks, ownership foreign keys, and same-version node
  constraints.
- Exactly 1 Vietnamese goal template, 17 node, and 8 question translation rows.
- Source content remains unchanged and English fallback survives a missing translation.

### API/security

- `vi-VN`, `en`, unsupported, malformed, absent, and weighted `Accept-Language` values.
- Correct `Content-Language` and stable IDs/cursors across locales.
- Localized next-question/result without answer key or mapping exposure.
- Submit identical answers after switching locale: one attempt/evidence/outbox outcome.
- Existing unauthenticated, CSRF, ownership concealment, expiry, and idempotency behavior
  remains green.

### Frontend

- Default Vietnamese selector and HTML `lang`.
- Switch to English and back; persistence across remount/reload.
- Login/register/goal/diagnostic/result and common error states in both languages.
- Locale header attached to content requests.
- In-progress diagnostic keeps session/question/selection identity while labels change.
- `Asia/Ho_Chi_Minh` default in registration and goal setup.
- Keyboard-accessible selector with an explicit accessible name and visible focus.

### Regression commands

```text
Maven 3.9.16 -o -Dmaven.repo.local=C:/Users/Dell/.m2/repository -f backend/pom.xml clean verify
npm --prefix frontend run api:generate
npm --prefix frontend run format
npm --prefix frontend run lint
npm --prefix frontend run test -- --run
npm --prefix frontend run build
docker compose config --quiet
scripts/audit.ps1
docker compose --profile app up -d --build
```

## Acceptance criteria

1. Given a new browser with no saved preference, when SkillPath loads, then the UI and
   canonical learner content render in Vietnamese and `<html lang="vi-VN">` is set.
2. Given the learner selects English, when any current learner route renders or the page
   reloads, then UI and server-authored content render in English.
3. Given an in-progress diagnostic, when the learner switches language, then the same
   session question and option IDs remain active and no attempt/evidence row is created.
4. Given equivalent selected option IDs in either locale, when submitted, then score,
   evidence, evaluator version, and outbox payload are identical.
5. Given a missing/unsupported translation, when content is requested, then the API
   returns safe canonical English without a 500 or mixed authoritative state.
6. Given Vietnamese question delivery, then no response/error/log exposes the canonical
   answer key or question-to-knowledge mapping.
7. Given goal templates, graph nodes, and diagnostic content, then every canonical Java
   Backend item in the approved seed has a Vietnamese representation.
8. Given a new registration or goal form, then timezone defaults to
   `Asia/Ho_Chi_Minh`; existing stored rows remain unchanged.
9. Given keyboard-only navigation, then the language selector is operable, focused, and
   announced with its current selection.
10. Existing Phase 1–3 behavior and all security/idempotency/concurrency tests remain green.

## Rollout and rollback

- Deploy V10/V11 before application code. Both migrations are additive and retain the
  English base columns used by older application versions.
- A rolling rollback to the pre-localization application safely ignores translation
  tables; database rollback remains restore/forward-fix, never editing applied migrations.
- The frontend defaults to English content fallback if the localized backend is not yet
  available, but deployment should still publish backend before the bilingual frontend.
- Translation corrections require a forward migration or a new immutable source version;
  applied seed migrations are never edited.

## Risks and open decisions

- Vietnamese curriculum wording needs pedagogical/native-speaker review before efficacy
  claims; technical English terms may intentionally remain inside Vietnamese sentences.
- Locale persistence is browser-local in v1. Cross-device preference requires the future
  profile-preferences endpoint and a separately approved data change.
- Supporting more locales will require new allowlist values, complete frontend dictionary
  parity, canonical content seeds, and compatibility tests.
- Public knowledge endpoint localization broadens read-contract behavior but not IDs,
  topology, cursor, or publication authority.
- Flyway 11.7.2/MySQL 8.4 compatibility warning remains and must be rechecked for V10/V11.

## Validation results

Completed on 2026-09-23:

- Backend Maven `clean verify`: exit `0`, `BUILD SUCCESS`; 16 unit/architecture tests
  and 17 MySQL integration/migration tests passed with zero failures/errors/skips.
- Flyway validated 11 migrations. Clean V1-to-V11 application startup and the explicit
  V9-to-V11 localization upgrade passed on MySQL 8.4. The seed assertions verified
  1 goal template, 17 knowledge nodes, and 8 question-version translations.
- OpenAPI generation: exit `0`; generated TypeScript contains the locale header contract.
- Frontend Prettier check, ESLint, Vitest, and production build: exit `0`; 3 test files /
  8 tests passed and Vite built 244 modules.
- `docker compose config --quiet`: exit `0`. Docker still warns that the sandbox cannot
  read `C:\Users\Dell\.docker\config.json`; configuration validation succeeds.
- `scripts/audit.ps1`: exit `0`; npm reported 0 vulnerabilities and `git diff --check`
  passed.
- Both app images rebuilt successfully and the MySQL/backend/frontend containers became
  healthy. Container smoke through `http://localhost:5173` returned Vietnamese content
  with `Content-Language: vi-VN`, English content with `Content-Language: en`, and
  unsupported `fr-FR` with the documented English fallback.

Known warning: Flyway 11.7.2 reports that MySQL 8.4 is newer than its last declared
tested line (8.1). Clean and historical migration paths passed; keep the compatibility
warning open for dependency upgrades.

## Documentation updates

During implementation, update the localization contract, physical schema/migration
history, module contracts, assessment/knowledge presentation rules, test gates, and
`PROJECT_CONTEXT.md` in the same change.
