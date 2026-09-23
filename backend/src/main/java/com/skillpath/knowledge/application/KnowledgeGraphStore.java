package com.skillpath.knowledge.application;

import com.skillpath.knowledge.domain.GraphSnapshot;
import java.time.Instant;
import java.util.Map;
import java.util.Optional;

public interface KnowledgeGraphStore {
    Optional<GraphSnapshot> findVersion(long versionId);

    Optional<GraphSnapshot> findPublishedByGoalTemplate(long goalTemplateId);

    Optional<GraphSnapshot> findPublishedByNode(long nodeId);

    Map<Long, NodeTranslation> findNodeTranslations(long graphVersionId, String locale);

    void markValidated(long versionId, long expectedVersion, long actorUserId, String correlationId, Instant now);

    Publication publish(long versionId, long actorUserId, String correlationId, Instant now);

    record Publication(GraphSnapshot graph, boolean replayed) {}

    record NodeTranslation(long nodeId, String name, String description) {}
}
