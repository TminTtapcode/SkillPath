package com.skillpath.knowledge.domain;

public record KnowledgeNode(
        long id,
        long graphVersionId,
        String slug,
        String name,
        String description,
        String category,
        int difficulty,
        int estimatedMinutes,
        KnowledgeNodeStatus status) {}
