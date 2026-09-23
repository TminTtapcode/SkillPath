package com.skillpath.knowledge.domain;

import java.time.Instant;

public record GraphVersion(
        long id,
        String curriculumKey,
        String versionLabel,
        GraphVersionStatus status,
        long version,
        Instant publishedAt) {}
