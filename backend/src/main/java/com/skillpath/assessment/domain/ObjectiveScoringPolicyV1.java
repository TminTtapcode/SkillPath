package com.skillpath.assessment.domain;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public final class ObjectiveScoringPolicyV1 {

    public static final String VERSION = "assessment-objective-v1";
    private static final BigDecimal SINGLE_RELIABILITY = new BigDecimal("0.4500");
    private static final BigDecimal MULTIPLE_RELIABILITY = new BigDecimal("0.5500");

    public Evaluation evaluate(ObjectiveQuestion question, List<String> selectedOptionIds) {
        if (selectedOptionIds == null || selectedOptionIds.isEmpty()) {
            throw new IllegalArgumentException("At least one option must be selected");
        }
        Set<String> selected = new HashSet<>(selectedOptionIds);
        if (selected.size() != selectedOptionIds.size()) {
            throw new IllegalArgumentException("Selected option IDs must be unique");
        }
        Set<String> allowed = question.options().stream()
                .map(QuestionOption::id)
                .collect(java.util.stream.Collectors.toSet());
        if (!allowed.containsAll(selected)) {
            throw new IllegalArgumentException("Selected option is not part of the question");
        }
        if (question.type() == QuestionType.SINGLE_CHOICE && selected.size() != 1) {
            throw new IllegalArgumentException("Single choice requires exactly one selection");
        }

        BigDecimal score = question.type() == QuestionType.SINGLE_CHOICE
                ? singleScore(question.correctOptionIds(), selected)
                : multipleScore(question.correctOptionIds(), selected);
        BigDecimal baseReliability = question.type() == QuestionType.SINGLE_CHOICE
                ? SINGLE_RELIABILITY
                : MULTIPLE_RELIABILITY;
        List<Evidence> evidence = question.mappings().stream()
                .map(mapping -> new Evidence(
                        mapping.knowledgeNodeId(),
                        mapping.dimension(),
                        score,
                        scale(baseReliability.min(mapping.maxEvidenceStrength())
                                .multiply(mapping.weight()))))
                .toList();
        return new Evaluation(score, evidence);
    }

    private BigDecimal singleScore(Set<String> correct, Set<String> selected) {
        return correct.equals(selected) ? BigDecimal.ONE.setScale(4) : BigDecimal.ZERO.setScale(4);
    }

    private BigDecimal multipleScore(Set<String> correct, Set<String> selected) {
        long correctSelections = selected.stream().filter(correct::contains).count();
        long incorrectSelections = selected.size() - correctSelections;
        BigDecimal numerator = BigDecimal.valueOf(correctSelections - incorrectSelections);
        if (numerator.signum() <= 0) {
            return BigDecimal.ZERO.setScale(4);
        }
        return scale(numerator.divide(BigDecimal.valueOf(correct.size()), 8, RoundingMode.HALF_UP)
                .min(BigDecimal.ONE));
    }

    private BigDecimal scale(BigDecimal value) {
        return value.max(BigDecimal.ZERO).min(BigDecimal.ONE).setScale(4, RoundingMode.HALF_UP);
    }

    public record Evaluation(BigDecimal score, List<Evidence> evidence) {}

    public record Evidence(
            long knowledgeNodeId,
            KnowledgeDimension dimension,
            BigDecimal score,
            BigDecimal reliability) {}
}
