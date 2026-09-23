package com.skillpath.learning.domain;

import java.util.Map;
import java.util.Set;

public final class LearningTransitionPolicy {
    private static final Map<String, Map<String, String>> TRANSITIONS = Map.of(
            "ASSIGNED", Map.of("start", "IN_PROGRESS", "skip", "SKIPPED"),
            "IN_PROGRESS", Map.of("complete", "COMPLETED", "blocked", "BLOCKED", "abandon", "ABANDONED"),
            "BLOCKED", Map.of("resume", "IN_PROGRESS"));
    private static final Set<String> STOPPED = Set.of("SKIPPED", "ABANDONED");

    private LearningTransitionPolicy() {}

    public static String next(String status, String command) {
        String next = TRANSITIONS.getOrDefault(status, Map.of()).get(command);
        if (next == null) {
            throw new IllegalArgumentException("Invalid learning task transition");
        }
        return next;
    }

    public static boolean stopsSession(String nextStatus) {
        return STOPPED.contains(nextStatus);
    }
}
