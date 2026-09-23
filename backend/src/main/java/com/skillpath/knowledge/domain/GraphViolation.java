package com.skillpath.knowledge.domain;

import java.util.List;

public record GraphViolation(String code, String detail, List<Long> nodePath) {
    public GraphViolation {
        nodePath = List.copyOf(nodePath);
    }

    public static GraphViolation of(String code, String detail) {
        return new GraphViolation(code, detail, List.of());
    }
}
