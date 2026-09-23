package com.skillpath.knowledge.domain;

import java.util.List;

public record GraphValidationResult(List<GraphViolation> violations) {
    public GraphValidationResult {
        violations = List.copyOf(violations);
    }

    public boolean valid() {
        return violations.isEmpty();
    }
}
