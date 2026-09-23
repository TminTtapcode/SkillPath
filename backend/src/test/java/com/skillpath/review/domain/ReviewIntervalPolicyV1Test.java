package com.skillpath.review.domain;

import static org.assertj.core.api.Assertions.assertThat;
import java.math.BigDecimal;
import java.time.Duration;
import org.junit.jupiter.api.Test;

class ReviewIntervalPolicyV1Test {
    private final ReviewIntervalPolicyV1 policy = new ReviewIntervalPolicyV1();
    @Test void failedReviewResetsToFirstInterval(){var result=policy.next(4,new BigDecimal("0.59"));assertThat(result.intervalIndex()).isZero();assertThat(result.interval()).isEqualTo(Duration.ofDays(1));}
    @Test void partialReviewMovesBackOne(){assertThat(policy.next(3,new BigDecimal("0.70")).intervalIndex()).isEqualTo(2);}
    @Test void strongReviewAdvancesAndCaps(){assertThat(policy.next(5,new BigDecimal("0.80")).intervalIndex()).isEqualTo(5);}
}
