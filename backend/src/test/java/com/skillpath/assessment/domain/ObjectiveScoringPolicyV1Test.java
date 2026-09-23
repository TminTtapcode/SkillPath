package com.skillpath.assessment.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;

class ObjectiveScoringPolicyV1Test {

    private final ObjectiveScoringPolicyV1 policy = new ObjectiveScoringPolicyV1();

    @Test
    void scoresSingleChoiceExactlyAndCapsReliability() {
        ObjectiveQuestion question = question(
                QuestionType.SINGLE_CHOICE,
                Set.of("a"),
                List.of(new QuestionMapping(
                        1001, KnowledgeDimension.RECOGNITION, BigDecimal.ONE, new BigDecimal("0.3000"))));

        ObjectiveScoringPolicyV1.Evaluation correct = policy.evaluate(question, List.of("a"));
        ObjectiveScoringPolicyV1.Evaluation wrong = policy.evaluate(question, List.of("b"));

        assertThat(correct.score()).isEqualByComparingTo("1.0000");
        assertThat(correct.evidence().getFirst().reliability()).isEqualByComparingTo("0.3000");
        assertThat(wrong.score()).isEqualByComparingTo("0.0000");
    }

    @Test
    void scoresMultipleChoiceWithPartialCreditAndPenalty() {
        ObjectiveQuestion question = question(
                QuestionType.MULTIPLE_CHOICE,
                Set.of("a", "b"),
                List.of(new QuestionMapping(
                        1003, KnowledgeDimension.UNDERSTANDING, BigDecimal.ONE, BigDecimal.ONE)));

        assertThat(policy.evaluate(question, List.of("a", "b")).score()).isEqualByComparingTo("1.0000");
        assertThat(policy.evaluate(question, List.of("a")).score()).isEqualByComparingTo("0.5000");
        assertThat(policy.evaluate(question, List.of("a", "c")).score()).isEqualByComparingTo("0.0000");
        assertThat(policy.evaluate(question, List.of("a", "b", "c")).score()).isEqualByComparingTo("0.5000");
    }

    @Test
    void rejectsUnknownDuplicateOrInvalidObjectiveEvidence() {
        ObjectiveQuestion question = question(
                QuestionType.SINGLE_CHOICE,
                Set.of("a"),
                List.of(new QuestionMapping(
                        1001, KnowledgeDimension.RECOGNITION, BigDecimal.ONE, BigDecimal.ONE)));

        assertThatThrownBy(() -> policy.evaluate(question, List.of("unknown")))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> policy.evaluate(question, List.of("a", "a")))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> question(
                        QuestionType.SINGLE_CHOICE,
                        Set.of("a"),
                        List.of(new QuestionMapping(
                                1001, KnowledgeDimension.APPLICATION, BigDecimal.ONE, BigDecimal.ONE))))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void scalesReliabilityByConceptWeightDeterministically() {
        ObjectiveQuestion question = question(
                QuestionType.MULTIPLE_CHOICE,
                Set.of("a", "b"),
                List.of(
                        new QuestionMapping(
                                1003,
                                KnowledgeDimension.UNDERSTANDING,
                                new BigDecimal("0.2500"),
                                BigDecimal.ONE),
                        new QuestionMapping(
                                1004,
                                KnowledgeDimension.UNDERSTANDING,
                                new BigDecimal("0.7500"),
                                new BigDecimal("0.5000"))));

        ObjectiveScoringPolicyV1.Evaluation first = policy.evaluate(question, List.of("a"));
        ObjectiveScoringPolicyV1.Evaluation replay = policy.evaluate(question, List.of("a"));

        assertThat(first).isEqualTo(replay);
        assertThat(first.evidence().get(0).reliability()).isEqualByComparingTo("0.1375");
        assertThat(first.evidence().get(1).reliability()).isEqualByComparingTo("0.3750");
    }

    private ObjectiveQuestion question(
            QuestionType type, Set<String> correct, List<QuestionMapping> mappings) {
        return new ObjectiveQuestion(
                1,
                type,
                "Prompt",
                1,
                45,
                List.of(
                        new QuestionOption("a", "A"),
                        new QuestionOption("b", "B"),
                        new QuestionOption("c", "C")),
                correct,
                mappings);
    }
}
