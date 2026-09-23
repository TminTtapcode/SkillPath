package com.skillpath.review.domain;

import java.math.BigDecimal;
import java.time.Duration;

public final class ReviewIntervalPolicyV1 {
    public static final String VERSION = "review-interval-v1";
    private static final int[] DAYS = {1, 3, 7, 14, 30, 60};

    public Decision next(int currentIndex, BigDecimal score) {
        int next = score.compareTo(new BigDecimal("0.60")) < 0 ? 0
                : score.compareTo(new BigDecimal("0.80")) < 0 ? Math.max(0, currentIndex - 1)
                : Math.min(DAYS.length - 1, currentIndex + 1);
        return new Decision(next, Duration.ofDays(DAYS[next]));
    }

    public Duration firstInterval() { return Duration.ofDays(DAYS[0]); }
    public record Decision(int intervalIndex, Duration interval) {}
}
