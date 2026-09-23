package com.skillpath.planner.domain;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/** Adds evaluated-failure eligibility without changing planner-v1 scoring or its replay. */
public final class PlannerPolicyV2 {
    public static final String VERSION = "planner-v2";
    private static final BigDecimal FAILURE_THRESHOLD = new BigDecimal("0.60");
    private final PlannerPolicyV1 ranking = new PlannerPolicyV1();

    public Result plan(Input input) {
        if (input.variants().size() > 1000 || input.failures().size() > 2000)
            throw new IllegalArgumentException("Planner history exceeds policy bounds");
        Map<FailureKey, Set<Long>> failedAttempts = input.failures().stream()
                .filter(failure -> failure.score().compareTo(FAILURE_THRESHOLD) < 0)
                .collect(Collectors.groupingBy(failure -> new FailureKey(failure.nodeId(),
                                failure.variantGroupKey(), failure.templateVersionId()),
                        Collectors.mapping(Failure::attemptId, Collectors.toSet())));
        Set<FailureKey> ineligible = failedAttempts.entrySet().stream()
                .filter(entry -> entry.getValue().size() >= 2)
                .map(Map.Entry::getKey).collect(Collectors.toSet());
        List<Variant> eligible = input.variants().stream()
                .filter(variant -> !ineligible.contains(new FailureKey(variant.nodeId(),
                        variant.variantGroupKey(), variant.templateVersionId())))
                .toList();
        var v1 = ranking.plan(new PlannerPolicyV1.Input(input.base().projectionAsOf(),
                input.base().availableMinutes(), input.base().nodes(), input.base().edges(),
                input.base().states(), input.base().due(), eligible.stream()
                        .map(variant -> new PlannerPolicyV1.Variant(variant.templateVersionId(),
                                variant.nodeId(), variant.activityType(), variant.difficulty(),
                                variant.minutes())).toList()));
        boolean needsCuration = ineligible.stream().anyMatch(key -> input.variants().stream()
                .noneMatch(variant -> variant.nodeId() == key.nodeId()
                        && variant.variantGroupKey().equals(key.variantGroupKey())
                        && variant.templateVersionId() != key.templateVersionId()
                        && eligible.contains(variant)));
        String reason = v1.selected().isEmpty() && needsCuration
                ? "NEEDS_CURATED_ALTERNATIVE" : v1.reasonCode();
        return new Result(v1, reason, ineligible.stream().map(FailureKey::templateVersionId)
                .sorted().toList());
    }

    public record Variant(long templateVersionId, long nodeId, String variantGroupKey,
                          String activityType, int difficulty, int minutes) {}
    public record Failure(long attemptId, long nodeId, String variantGroupKey,
                          long templateVersionId, BigDecimal score) {}
    public record Input(PlannerPolicyV1.Input base, List<Variant> variants, List<Failure> failures) {
        public Input { variants = List.copyOf(variants); failures = List.copyOf(failures); }
    }
    public record Result(PlannerPolicyV1.Result ranking, String reasonCode,
                         List<Long> ineligibleTemplateVersionIds) {}
    private record FailureKey(long nodeId, String variantGroupKey, long templateVersionId) {}
}
