package com.skillpath.planner.application;

import java.time.Instant;
import java.util.Optional;

/** Planner-owned durable request/lease operations; callers supply transaction boundaries. */
public interface ReplanRequestStore {
    Optional<Request> claim(String workerId, Instant now);
    Optional<Request> locked(long requestId, String workerId);
    void complete(long requestId, String workerId, ReplanExecution.Outcome outcome, Instant now);
    void fail(long requestId, String workerId, Instant now, String safeErrorCode);

    record Request(long id, String attemptKind, long attemptId, Long sourceEventId,
                   long userId, long goalId, long graphVersionId, int attemptCount) {}
}
