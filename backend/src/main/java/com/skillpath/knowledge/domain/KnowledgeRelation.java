package com.skillpath.knowledge.domain;

import java.math.BigDecimal;

public record KnowledgeRelation(
        long id,
        long graphVersionId,
        long sourceNodeId,
        long targetNodeId,
        KnowledgeRelationType type,
        BigDecimal strength,
        KnowledgeRelationStatus status,
        String rationale) {

    public boolean activePrerequisite() {
        return status == KnowledgeRelationStatus.ACTIVE && type == KnowledgeRelationType.PREREQUISITE;
    }
}
