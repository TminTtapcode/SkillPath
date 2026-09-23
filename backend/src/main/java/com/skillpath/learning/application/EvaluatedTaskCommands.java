package com.skillpath.learning.application;

import java.time.Instant;

/** Owner-checked task authority exposed to Assessment; no Learning persistence types cross the boundary. */
public interface EvaluatedTaskCommands {
    EvaluatedTask taskForCheck(long userId, long taskId);

    void completeCheckedTask(long userId, long taskId, long attemptId, int actualMinutes, Instant now);

    record EvaluatedTask(long id, long goalId, long graphVersionId, long templateVersionId,
                         String status, String sessionStatus) {}
}
