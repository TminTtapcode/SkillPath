package com.skillpath.goal.application;

import java.time.Instant;

public interface IdempotencyStore {

    Claim claim(
            long userId,
            String operation,
            String key,
            String requestHash,
            Instant now,
            Instant expiresAt);

    void complete(long recordId, long resourceId, Instant now);

    record Claim(long recordId, State state, Long resourceId) {
        public enum State {
            NEW,
            REPLAY,
            HASH_MISMATCH,
            IN_PROGRESS
        }
    }
}
