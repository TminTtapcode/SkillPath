package com.skillpath.user.domain;

import java.time.Instant;

public record UserProfile(
        Long id, String displayName, String timezone, long version, Instant createdAt, Instant updatedAt) {}
