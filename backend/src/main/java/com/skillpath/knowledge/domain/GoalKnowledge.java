package com.skillpath.knowledge.domain;

import java.math.BigDecimal;

public record GoalKnowledge(
        long goalTemplateId,
        long graphVersionId,
        long knowledgeNodeId,
        BigDecimal relevanceWeight,
        BigDecimal requiredMastery,
        boolean terminal) {}
