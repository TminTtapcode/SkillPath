package com.skillpath.review.application;

import java.time.Instant;
import java.util.List;
import java.util.Set;

public interface PlannerReviewQueries {
    Snapshot snapshot(long userId, long graphVersionId, Set<Long> nodeIds, Instant projectionAsOf);

    record Due(long nodeId, Instant dueAt, int intervalIndex) {}
    record Snapshot(String digest, boolean compatible, List<Due> due) {
        public Snapshot { due = List.copyOf(due); }
    }
}
