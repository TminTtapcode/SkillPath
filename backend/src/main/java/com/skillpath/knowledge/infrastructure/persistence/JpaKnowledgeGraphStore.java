package com.skillpath.knowledge.infrastructure.persistence;

import com.skillpath.knowledge.application.KnowledgeGraphStore;
import com.skillpath.knowledge.domain.GoalKnowledge;
import com.skillpath.knowledge.domain.GraphSnapshot;
import com.skillpath.knowledge.domain.GraphVersion;
import com.skillpath.knowledge.domain.GraphVersionStatus;
import com.skillpath.knowledge.domain.KnowledgeNode;
import com.skillpath.knowledge.domain.KnowledgeNodeStatus;
import com.skillpath.knowledge.domain.KnowledgeRelation;
import com.skillpath.knowledge.domain.KnowledgeRelationStatus;
import com.skillpath.knowledge.domain.KnowledgeRelationType;
import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import org.springframework.stereotype.Component;

@Component
class JpaKnowledgeGraphStore implements KnowledgeGraphStore {

    private final GraphVersionJpaRepository versions;
    private final KnowledgeNodeJpaRepository nodes;
    private final KnowledgeRelationJpaRepository relations;
    private final GoalKnowledgeJpaRepository goalKnowledge;
    private final KnowledgeVersionEventJpaRepository events;

    JpaKnowledgeGraphStore(
            GraphVersionJpaRepository versions,
            KnowledgeNodeJpaRepository nodes,
            KnowledgeRelationJpaRepository relations,
            GoalKnowledgeJpaRepository goalKnowledge,
            KnowledgeVersionEventJpaRepository events) {
        this.versions = versions;
        this.nodes = nodes;
        this.relations = relations;
        this.goalKnowledge = goalKnowledge;
        this.events = events;
    }

    @Override
    public Optional<GraphSnapshot> findVersion(long versionId) {
        return versions.findById(versionId).map(this::snapshot);
    }

    @Override
    public Optional<GraphSnapshot> findPublishedByGoalTemplate(long goalTemplateId) {
        return goalKnowledge.findVersionIdsByGoalTemplateId(goalTemplateId).stream()
                .map(versions::findById)
                .flatMap(Optional::stream)
                .filter(version -> version.status.equals(GraphVersionStatus.PUBLISHED.name()))
                .findFirst()
                .map(this::snapshot);
    }

    @Override
    public Optional<GraphSnapshot> findPublishedByNode(long nodeId) {
        return nodes.findById(nodeId)
                .flatMap(node -> versions.findById(node.graphVersionId))
                .filter(version -> version.status.equals(GraphVersionStatus.PUBLISHED.name()))
                .map(this::snapshot);
    }

    @Override
    public void markValidated(
            long versionId, long expectedVersion, long actorUserId, String correlationId, Instant now) {
        GraphVersionEntity entity = versions.findLockedById(versionId).orElseThrow();
        if (!entity.status.equals(GraphVersionStatus.DRAFT.name()) || entity.version != expectedVersion) {
            throw new IllegalStateException("GRAPH_VERSION_STATE_CONFLICT");
        }
        entity.status = GraphVersionStatus.VALIDATED.name();
        entity.validatedAt = now;
        entity.validatedBy = actorUserId;
        entity.updatedAt = now;
        versions.saveAndFlush(entity);
        events.save(event(entity.id, "VALIDATED", actorUserId, correlationId, "DRAFT", "VALIDATED", now));
    }

    @Override
    public Publication publish(long versionId, long actorUserId, String correlationId, Instant now) {
        GraphVersionEntity observedTarget = versions.findById(versionId).orElseThrow();
        if (observedTarget.status.equals(GraphVersionStatus.PUBLISHED.name())) {
            return new Publication(snapshot(observedTarget), true);
        }
        if (!observedTarget.status.equals(GraphVersionStatus.VALIDATED.name())) {
            throw new IllegalStateException("GRAPH_VERSION_STATE_CONFLICT");
        }
        Long expectedPublishedId = versions
                .findByCurriculumKeyAndStatus(observedTarget.curriculumKey, GraphVersionStatus.PUBLISHED.name())
                .map(version -> version.id)
                .orElse(null);
        List<GraphVersionEntity> curriculum = versions.findAllLockedByCurriculumKey(observedTarget.curriculumKey);
        GraphVersionEntity target = curriculum.stream()
                .filter(version -> version.id == versionId)
                .findFirst()
                .orElseThrow();
        Long lockedPublishedId = curriculum.stream()
                .filter(version -> version.status.equals(GraphVersionStatus.PUBLISHED.name()))
                .map(version -> version.id)
                .findFirst()
                .orElse(null);
        if (!Objects.equals(expectedPublishedId, lockedPublishedId)
                || !target.status.equals(GraphVersionStatus.VALIDATED.name())) {
            throw new IllegalStateException("GRAPH_PUBLICATION_CONFLICT");
        }
        for (GraphVersionEntity current : curriculum) {
            if (current.status.equals(GraphVersionStatus.PUBLISHED.name())) {
                current.status = GraphVersionStatus.RETIRED.name();
                current.retiredAt = now;
                current.retiredBy = actorUserId;
                current.updatedAt = now;
                versions.save(current);
                events.save(event(current.id, "RETIRED", actorUserId, correlationId, "PUBLISHED", "RETIRED", now));
            }
        }
        // MySQL enforces one published version through a generated unique key. Flush
        // retirement first so Hibernate cannot reorder the two updates; the enclosing
        // transaction still rolls both changes back if publication fails.
        versions.flush();
        target.status = GraphVersionStatus.PUBLISHED.name();
        target.publishedAt = now;
        target.publishedBy = actorUserId;
        target.updatedAt = now;
        versions.saveAndFlush(target);
        events.save(event(target.id, "PUBLISHED", actorUserId, correlationId, "VALIDATED", "PUBLISHED", now));
        return new Publication(snapshot(target), false);
    }

    private KnowledgeVersionEventEntity event(
            long versionId,
            String type,
            long actor,
            String correlationId,
            String from,
            String to,
            Instant now) {
        KnowledgeVersionEventEntity event = new KnowledgeVersionEventEntity();
        event.graphVersionId = versionId;
        event.eventType = type;
        event.actorUserId = actor;
        event.correlationId = correlationId;
        event.fromStatus = from;
        event.toStatus = to;
        event.summary = "{}";
        event.createdAt = now;
        return event;
    }

    private GraphSnapshot snapshot(GraphVersionEntity version) {
        GraphVersion graphVersion = new GraphVersion(
                version.id,
                version.curriculumKey,
                version.versionLabel,
                GraphVersionStatus.valueOf(version.status),
                version.version,
                version.publishedAt);
        List<KnowledgeNode> graphNodes = nodes.findAllByGraphVersionIdOrderBySlugAscIdAsc(version.id).stream()
                .map(node -> new KnowledgeNode(
                        node.id,
                        node.graphVersionId,
                        node.slug,
                        node.name,
                        node.description,
                        node.category,
                        node.difficulty,
                        node.estimatedMinutes,
                        KnowledgeNodeStatus.valueOf(node.status)))
                .toList();
        List<KnowledgeRelation> graphRelations = relations.findAllByGraphVersionIdOrderByIdAsc(version.id).stream()
                .map(edge -> new KnowledgeRelation(
                        edge.id,
                        edge.graphVersionId,
                        edge.sourceNodeId,
                        edge.targetNodeId,
                        KnowledgeRelationType.valueOf(edge.relationType),
                        edge.strength,
                        KnowledgeRelationStatus.valueOf(edge.status),
                        edge.rationale))
                .toList();
        List<GoalKnowledge> mappings = goalKnowledge.findAllByGraphVersionIdOrderByKnowledgeNodeIdAsc(version.id).stream()
                .map(mapping -> new GoalKnowledge(
                        mapping.goalTemplateId,
                        mapping.graphVersionId,
                        mapping.knowledgeNodeId,
                        mapping.relevanceWeight,
                        mapping.requiredMastery,
                        mapping.terminal))
                .toList();
        return new GraphSnapshot(graphVersion, graphNodes, graphRelations, mappings);
    }
}
