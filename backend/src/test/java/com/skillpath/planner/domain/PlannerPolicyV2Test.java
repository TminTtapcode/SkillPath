package com.skillpath.planner.domain;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;

class PlannerPolicyV2Test {
    private final PlannerPolicyV2 policy = new PlannerPolicyV2();
    private static final Instant NOW = Instant.parse("2026-09-23T08:00:00Z");

    @Test void twoDistinctFailuresExcludeOnlyThatVersionWhenAlternateExists() {
        var result = policy.plan(input(List.of(
                failure(1, 10, "0.20"), failure(2, 10, "0.59")), true));
        assertThat(result.ineligibleTemplateVersionIds()).containsExactly(10L);
        assertThat(result.ranking().selected()).extracting(choice -> choice.variant().templateVersionId())
                .containsExactly(11L);
    }

    @Test void duplicateAttemptDoesNotCountAsTwoFailures() {
        var result = policy.plan(input(List.of(
                failure(1, 10, "0.20"), failure(1, 10, "0.20")), true));
        assertThat(result.ineligibleTemplateVersionIds()).isEmpty();
    }

    @Test void noAlternateDoesNotRepeatFailedVersionOrInventRemediation() {
        var result = policy.plan(input(List.of(
                failure(1, 10, "0.00"), failure(2, 10, "0.10")), false));
        assertThat(result.ranking().selected()).isEmpty();
        assertThat(result.reasonCode()).isEqualTo("NEEDS_CURATED_ALTERNATIVE");
    }

    private static PlannerPolicyV2.Input input(List<PlannerPolicyV2.Failure> failures,
                                               boolean alternate) {
        var base = new PlannerPolicyV1.Input(NOW, 20,
                List.of(new PlannerPolicyV1.Node(1, "ACTIVE", new BigDecimal("1"),
                        new BigDecimal("0.8"), 0)), List.of(), List.of(
                        new PlannerPolicyV1.State(1, new BigDecimal("0.2"),
                                new BigDecimal("0.3"), 2, "LEARNING")), List.of(), List.of());
        var first = new PlannerPolicyV2.Variant(10, 1, "practice-flow", "PRACTICE", 1, 10);
        var variants = alternate ? List.of(first,
                new PlannerPolicyV2.Variant(11, 1, "practice-flow", "PRACTICE", 1, 10))
                : List.of(first);
        return new PlannerPolicyV2.Input(base, variants, failures);
    }

    private static PlannerPolicyV2.Failure failure(long attempt, long version, String score) {
        return new PlannerPolicyV2.Failure(attempt, 1, "practice-flow", version,
                new BigDecimal(score));
    }
}
