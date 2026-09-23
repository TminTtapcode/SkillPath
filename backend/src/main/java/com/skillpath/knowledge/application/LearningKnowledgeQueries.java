package com.skillpath.knowledge.application;

import java.util.Set;

public interface LearningKnowledgeQueries {
    LearningGraph publishedLearningGraph(long goalTemplateId);

    record LearningGraph(long graphVersionId, String curriculumKey, Set<Long> rootNodeIds) {}
}
