package com.skillpath.knowledge.application;

import com.skillpath.shared.localization.SupportedLocale;

public interface KnowledgeGraphQueries {
    KnowledgeGraphService.GoalGraphView goalGraph(
            long goalTemplateId,
            Long anchorNodeId,
            int depth,
            int limit,
            String cursor,
            SupportedLocale locale);
}
