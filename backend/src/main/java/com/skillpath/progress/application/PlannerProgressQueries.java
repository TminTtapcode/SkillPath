package com.skillpath.progress.application;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Set;

/** Owner-scoped effective state at the planner's single projection instant. */
public interface PlannerProgressQueries {
    Snapshot snapshot(long userId, long graphVersionId, Set<Long> nodeIds, Instant projectionAsOf);

    record Node(long nodeId, BigDecimal effectiveMastery, BigDecimal confidence,
                int evidenceCount, String status) {}
    record Snapshot(String policyVersion, String digest, boolean compatible, List<Node> nodes) {
        public Snapshot { nodes = List.copyOf(nodes); }
    }
}
