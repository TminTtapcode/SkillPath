package com.skillpath.assessment.domain;

import java.math.BigDecimal;

public record QuestionMapping(
        long knowledgeNodeId,
        KnowledgeDimension dimension,
        BigDecimal weight,
        BigDecimal maxEvidenceStrength) {

    public QuestionMapping {
        if (knowledgeNodeId <= 0 || dimension == null || weight == null || maxEvidenceStrength == null) {
            throw new IllegalArgumentException("Question mapping is incomplete");
        }
        if (weight.signum() <= 0 || weight.compareTo(BigDecimal.ONE) > 0) {
            throw new IllegalArgumentException("Question mapping weight must be in (0,1]");
        }
        if (maxEvidenceStrength.signum() < 0 || maxEvidenceStrength.compareTo(BigDecimal.ONE) > 0) {
            throw new IllegalArgumentException("Evidence strength must be in [0,1]");
        }
    }
}
