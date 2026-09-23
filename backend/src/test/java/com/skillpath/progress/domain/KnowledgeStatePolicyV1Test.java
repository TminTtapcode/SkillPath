package com.skillpath.progress.domain;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import org.junit.jupiter.api.Test;

class KnowledgeStatePolicyV1Test {
    private final KnowledgeStatePolicyV1 policy = new KnowledgeStatePolicyV1();
    private final Instant now = Instant.parse("2026-09-23T00:00:00Z");

    @Test void projectsEvidenceDeterministicallyInSourceOrder() {
        var evidence = List.of(
                evidence("0.8", "0.5", 1),
                evidence("1.0", "0.9", 2));
        var first = policy.project(evidence, now);
        var replay = policy.project(evidence, now);
        assertThat(first).isEqualTo(replay);
        assertThat(first.dimensions().get(KnowledgeStatePolicyV1.Dimension.UNDERSTANDING)).isEqualByComparingTo("0.5437");
        assertThat(first.evidenceCount()).isEqualTo(2);
    }

    @Test void agingChangesOnlyEffectiveSnapshot() {
        var evidence = List.of(evidence("1", "1", 1));
        var initial = policy.project(evidence, now);
        var aged = policy.project(evidence, now.plus(150, ChronoUnit.DAYS));
        assertThat(aged.mastery()).isEqualByComparingTo(initial.mastery());
        assertThat(aged.effectiveMastery()).isLessThan(initial.effectiveMastery());
    }

    @Test void emptyProjectionIsUnknown() {
        var projection = policy.project(List.of(), now);
        assertThat(projection.effectiveStatus()).isEqualTo(KnowledgeStatus.UNKNOWN);
        assertThat(projection.mastery()).isEqualByComparingTo("0.0000");
    }

    private KnowledgeStatePolicyV1.Evidence evidence(String score, String reliability, long id) {
        return new KnowledgeStatePolicyV1.Evidence(KnowledgeStatePolicyV1.Dimension.UNDERSTANDING,
                new BigDecimal(score), new BigDecimal(reliability), now, id);
    }
}
