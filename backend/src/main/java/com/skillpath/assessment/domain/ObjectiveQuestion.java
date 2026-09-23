package com.skillpath.assessment.domain;

import java.math.BigDecimal;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public record ObjectiveQuestion(
        long versionId,
        QuestionType type,
        String prompt,
        int difficulty,
        int estimatedSeconds,
        List<QuestionOption> options,
        Set<String> correctOptionIds,
        List<QuestionMapping> mappings) {

    public ObjectiveQuestion {
        if (versionId <= 0 || type == null || prompt == null || prompt.isBlank()) {
            throw new IllegalArgumentException("Objective question is incomplete");
        }
        if (difficulty < 1 || difficulty > 5 || estimatedSeconds <= 0) {
            throw new IllegalArgumentException("Question difficulty or estimate is invalid");
        }
        options = List.copyOf(options);
        correctOptionIds = Set.copyOf(correctOptionIds);
        mappings = List.copyOf(mappings);
        if (options.size() < 2 || mappings.isEmpty() || correctOptionIds.isEmpty()) {
            throw new IllegalArgumentException("Objective question requires options, answers, and mappings");
        }
        Set<String> optionIds = new HashSet<>();
        for (QuestionOption option : options) {
            if (!optionIds.add(option.id())) {
                throw new IllegalArgumentException("Question option IDs must be unique");
            }
        }
        if (!optionIds.containsAll(correctOptionIds)) {
            throw new IllegalArgumentException("Answer key contains an unknown option");
        }
        if (type == QuestionType.SINGLE_CHOICE && correctOptionIds.size() != 1) {
            throw new IllegalArgumentException("Single choice requires exactly one correct option");
        }
        BigDecimal totalWeight = mappings.stream()
                .map(QuestionMapping::weight)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        if (totalWeight.compareTo(new BigDecimal("1.0000")) != 0) {
            throw new IllegalArgumentException("Question mapping weights must sum to 1.0000");
        }
        for (QuestionMapping mapping : mappings) {
            if (mapping.dimension() == KnowledgeDimension.RECALL
                    || mapping.dimension() == KnowledgeDimension.APPLICATION) {
                throw new IllegalArgumentException("Objective questions cannot emit recall or application evidence");
            }
        }
    }
}
