package com.skillpath.goal.domain;

import java.time.Instant;
import java.time.LocalDate;

public record UserGoal(
        long id,
        long userId,
        long goalTemplateId,
        LocalDate targetDate,
        String timezone,
        int defaultDailyMinutes,
        Status status,
        long version,
        Instant createdAt,
        Instant updatedAt) {

    public enum Status {
        ACTIVE,
        PAUSED,
        COMPLETED,
        CANCELLED
    }
}
