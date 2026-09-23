package com.skillpath.planner.application;

/**
 * The worker locks the owned goal before calling the P7.4 implementation.
 * Execution and acknowledgement share one transaction; a retry either observes
 * the committed request or rolls back the entire immutable revision.
 */
public interface ReplanExecution {
    Outcome execute(ReplanRequestStore.Request request);

    enum ResultCode { REVISION_CREATED, NO_CURRENT_PLAN, STALE_GOAL, MANUAL_SESSION_BLOCKED }

    record Outcome(ResultCode code, Long planId) {
        public Outcome {
            if (code == null || (code == ResultCode.REVISION_CREATED
                    ? planId == null || planId <= 0 : planId != null))
                throw new IllegalArgumentException("Invalid replan outcome");
        }
    }
}
