package com.skillpath.knowledge.application;

import com.skillpath.shared.localization.SupportedLocale;
import java.util.List;
import java.util.Set;

public interface AssessmentKnowledgeQueries {

    AssessmentGraph publishedAssessmentGraph(long goalTemplateId);

    List<NodeSummary> nodeSummaries(
            long graphVersionId, Set<Long> nodeIds, SupportedLocale locale);

    record AssessmentGraph(long graphVersionId, String curriculumKey, List<NodeSummary> nodes) {}

    record NodeSummary(long id, String slug, String name) {}
}
