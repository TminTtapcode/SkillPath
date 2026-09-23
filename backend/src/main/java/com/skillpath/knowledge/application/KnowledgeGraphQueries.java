package com.skillpath.knowledge.application;

public interface KnowledgeGraphQueries {
    KnowledgeGraphService.GoalGraphView goalGraph(
            long goalTemplateId, Long anchorNodeId, int depth, int limit, String cursor);
}
