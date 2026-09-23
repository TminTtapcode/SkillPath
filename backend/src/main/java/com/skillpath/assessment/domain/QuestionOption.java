package com.skillpath.assessment.domain;

public record QuestionOption(String id, String label) {
    public QuestionOption {
        if (id == null || id.isBlank() || label == null || label.isBlank()) {
            throw new IllegalArgumentException("Question options require nonblank IDs and labels");
        }
    }
}
