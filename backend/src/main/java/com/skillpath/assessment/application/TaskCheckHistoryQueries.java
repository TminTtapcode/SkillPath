package com.skillpath.assessment.application;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

/** Bounded, owner-scoped evaluated attempt history for planner-v2 eligibility. */
public interface TaskCheckHistoryQueries {
    List<FailureObservation> recentTaskChecks(long userId,long goalId,long graphVersionId,Instant asOf);

    record FailureObservation(long attemptId,long templateVersionId,BigDecimal score) {}
}
