package com.skillpath.review.application;

import java.time.Instant;
import java.util.List;
import java.util.Set;

/** A bounded, owner-scoped view of Review-owned schedules for other application modules. */
public interface ReviewScheduleQueries {
    List<Schedule> schedules(long userId, long graphVersionId, Set<Long> nodeIds, Instant asOf);

    record Schedule(long graphVersionId, long nodeId, Instant dueAt, String status, long version) {}
}
