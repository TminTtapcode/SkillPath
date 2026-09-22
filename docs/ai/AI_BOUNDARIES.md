# AI Boundaries and Workflow

## Role

AI is an untrusted intelligence adapter used where language generation or semantic evaluation adds value. Deterministic domain services retain authority.

## Allowed operations

- Generate draft questions/exercises for curator review or constrained runtime use.
- Evaluate free text/code explanation against a supplied rubric.
- Produce learner-friendly explanations from approved context.
- Summarize bounded resource sections with attribution metadata.
- Suggest misconception codes from an allowed taxonomy.

## Forbidden authority

AI cannot directly:

- write mastery or user knowledge;
- declare prerequisite satisfied;
- select final planner candidate;
- complete a goal;
- grant roles/permissions;
- execute SQL or migrations;
- invent curriculum node IDs or resources and persist them without validation.

## Request contract

Every operation specifies purpose, allowed IDs, rubric, output JSON schema, locale, model/config version, timeout, and privacy classification. Send minimum necessary user data and avoid secrets/private code unless explicitly required and permitted.

## Response validation

Validate:

- syntactic JSON/schema;
- enums and numeric ranges;
- node/question IDs against request allowlist;
- evidence count/weights;
- forbidden content and prompt-injection indicators;
- provider/model/evaluator version;
- maximum reliability allowed by operation.

Invalid output creates no domain evidence.

The structured assessment response includes the requested `evaluatorVersion` and it
must match exactly. Provider, model, prompt/config, latency, and request/response hash
are trusted adapter audit metadata populated from runtime configuration; domain code
does not trust identity fields merely echoed by a model.

## Reliability

- Timeouts and bounded retries only for safe/idempotent operations.
- Use a circuit breaker/failure budget if the provider becomes unstable.
- Persist request metadata/hash and response/evaluation audit according to privacy policy, not hidden reasoning.
- Provide deterministic fallback for planner and objective grading.
- Human/curator review is required for high-impact curriculum publishing and disputed evaluation.

## Prompt/version governance

Prompts are versioned application assets. Behavioral prompt change increments evaluator/generator version and requires regression examples. Never depend on provider-specific hidden behavior without a contract test.

## Evaluation quality

Maintain a small gold dataset covering correct, partial, misconception, irrelevant, adversarial, and multilingual answers. Track agreement, invalid-output rate, latency, and cost before increasing AI evidence reliability.
