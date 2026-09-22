package com.skillpath.auth.domain;

import java.time.Duration;
import java.time.Instant;
import java.util.Set;

public class AuthCredential {

    private final long userId;
    private final String normalizedEmail;
    private final String passwordHash;
    private int failedAttempts;
    private Instant lockedUntil;
    private final Set<String> roles;
    private final Instant createdAt;
    private Instant updatedAt;

    public AuthCredential(
            long userId,
            String normalizedEmail,
            String passwordHash,
            int failedAttempts,
            Instant lockedUntil,
            Set<String> roles,
            Instant createdAt,
            Instant updatedAt) {
        this.userId = userId;
        this.normalizedEmail = normalizedEmail;
        this.passwordHash = passwordHash;
        this.failedAttempts = failedAttempts;
        this.lockedUntil = lockedUntil;
        this.roles = Set.copyOf(roles);
        this.createdAt = createdAt;
        this.updatedAt = updatedAt;
    }

    public boolean isLocked(Instant now) {
        return lockedUntil != null && lockedUntil.isAfter(now);
    }

    public void recordFailure(Instant now, int maximumAttempts, Duration lockDuration) {
        if (lockedUntil != null && !lockedUntil.isAfter(now)) {
            failedAttempts = 0;
            lockedUntil = null;
        }
        failedAttempts++;
        if (failedAttempts >= maximumAttempts) {
            failedAttempts = 0;
            lockedUntil = now.plus(lockDuration);
        }
        updatedAt = now;
    }

    public void recordSuccess(Instant now) {
        failedAttempts = 0;
        lockedUntil = null;
        updatedAt = now;
    }

    public long userId() {
        return userId;
    }

    public String normalizedEmail() {
        return normalizedEmail;
    }

    public String passwordHash() {
        return passwordHash;
    }

    public int failedAttempts() {
        return failedAttempts;
    }

    public Instant lockedUntil() {
        return lockedUntil;
    }

    public Set<String> roles() {
        return roles;
    }

    public Instant createdAt() {
        return createdAt;
    }

    public Instant updatedAt() {
        return updatedAt;
    }
}
