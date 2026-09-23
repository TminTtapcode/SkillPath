package com.skillpath.knowledge.application;

import com.skillpath.knowledge.domain.GoalKnowledge;
import com.skillpath.knowledge.domain.GraphAlgorithms;
import com.skillpath.knowledge.domain.GraphSnapshot;
import com.skillpath.knowledge.domain.GraphValidationResult;
import com.skillpath.knowledge.domain.GraphValidator;
import com.skillpath.knowledge.domain.KnowledgeNode;
import com.skillpath.knowledge.domain.KnowledgeRelation;
import com.skillpath.shared.api.ApiException;
import com.skillpath.shared.localization.SupportedLocale;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Instant;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.springframework.dao.DataAccessException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class KnowledgeGraphService implements KnowledgeGraphQueries, AssessmentKnowledgeQueries,
        LearningKnowledgeQueries, PlannerKnowledgeQueries {

    private static final int MAX_LIMIT = 200;
    private static final int MAX_DEPTH = 10;
    private static final Comparator<KnowledgeNode> NODE_ORDER =
            Comparator.comparing(KnowledgeNode::slug).thenComparingLong(KnowledgeNode::id);

    private final KnowledgeGraphStore store;
    private final Clock clock;
    private final GraphValidator validator = new GraphValidator();

    public KnowledgeGraphService(KnowledgeGraphStore store, Clock clock) {
        this.store = store;
        this.clock = clock;
    }

    @Override
    @Transactional(readOnly = true)
    public PlanningGraph planningGraph(long goalTemplateId) {
        GraphSnapshot graph = store.findPublishedByGoalTemplate(goalTemplateId)
                .orElseThrow(() -> notFound("PUBLISHED_GOAL_GRAPH_NOT_FOUND", "Published goal graph was not found."));
        if (graph.nodes().size() > MAX_LIMIT) {
            throw new ApiException(HttpStatus.UNPROCESSABLE_ENTITY, "PLANNING_GRAPH_TOO_LARGE",
                    "This graph exceeds the bounded planner snapshot.");
        }
        Map<Long, GoalKnowledge> members = new HashMap<>();
        graph.goalKnowledge().stream().filter(mapping -> mapping.goalTemplateId() == goalTemplateId)
                .forEach(mapping -> members.put(mapping.knowledgeNodeId(), mapping));
        Map<Long, KnowledgeGraphStore.NodeTranslation> vi =
                store.findNodeTranslations(graph.version().id(), "vi-VN");
        List<KnowledgeNode> ordered = GraphAlgorithms.topologicalOrder(graph);
        List<PlannerKnowledgeQueries.Node> nodes = new ArrayList<>();
        for (int index = 0; index < ordered.size(); index++) {
            KnowledgeNode node = ordered.get(index);
            GoalKnowledge mapping = members.get(node.id());
            if (mapping == null) continue;
            var translated = vi.get(node.id());
            nodes.add(new PlannerKnowledgeQueries.Node(node.id(), node.slug(), node.name(),
                    translated == null ? node.name() : translated.name(), node.status().name(),
                    mapping.relevanceWeight(), mapping.requiredMastery(), mapping.terminal(), index));
        }
        Set<Long> ids = nodes.stream().map(PlannerKnowledgeQueries.Node::id)
                .collect(java.util.stream.Collectors.toSet());
        List<PlannerKnowledgeQueries.Edge> edges = graph.relations().stream()
                .filter(edge -> ids.contains(edge.sourceNodeId()) && ids.contains(edge.targetNodeId()))
                .map(edge -> new PlannerKnowledgeQueries.Edge(edge.sourceNodeId(), edge.targetNodeId(),
                        edge.type().name(), edge.strength())).toList();
        return new PlanningGraph(graph.version().id(), nodes, edges);
    }

    @Transactional(readOnly = true)
    public NodeView node(long nodeId) {
        return node(nodeId, SupportedLocale.ENGLISH);
    }

    @Transactional(readOnly = true)
    public NodeView node(long nodeId, SupportedLocale locale) {
        GraphSnapshot graph = publishedByNode(nodeId);
        KnowledgeNode node = graph.nodes().stream()
                .filter(candidate -> candidate.id() == nodeId)
                .findFirst()
                .orElseThrow(() -> notFound("KNOWLEDGE_NODE_NOT_FOUND", "Knowledge node was not found."));
        return NodeView.from(localize(node, translations(graph, locale)), graph.version().id());
    }

    @Transactional(readOnly = true)
    public List<NodeView> prerequisites(long nodeId, boolean transitive, int limit, boolean dependents) {
        return prerequisites(nodeId, transitive, limit, dependents, SupportedLocale.ENGLISH);
    }

    @Transactional(readOnly = true)
    public List<NodeView> prerequisites(
            long nodeId,
            boolean transitive,
            int limit,
            boolean dependents,
            SupportedLocale locale) {
        validateBounds(0, limit);
        GraphSnapshot graph = publishedByNode(nodeId);
        Map<Long, KnowledgeGraphStore.NodeTranslation> translations = translations(graph, locale);
        return GraphAlgorithms.traverse(graph, nodeId, !dependents, transitive, limit).stream()
                .map(node -> NodeView.from(localize(node, translations), graph.version().id()))
                .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public GoalGraphView goalGraph(
            long goalTemplateId,
            Long anchorNodeId,
            int depth,
            int limit,
            String cursor,
            SupportedLocale locale) {
        validateBounds(depth, limit);
        GraphSnapshot graph = store.findPublishedByGoalTemplate(goalTemplateId)
                .orElseThrow(() -> notFound("PUBLISHED_GOAL_GRAPH_NOT_FOUND", "Published goal graph was not found."));
        Set<Long> memberIds = new HashSet<>();
        Map<Long, GoalKnowledge> mappings = new HashMap<>();
        graph.goalKnowledge().stream()
                .filter(mapping -> mapping.goalTemplateId() == goalTemplateId)
                .forEach(mapping -> {
                    memberIds.add(mapping.knowledgeNodeId());
                    mappings.put(mapping.knowledgeNodeId(), mapping);
                });
        List<KnowledgeNode> candidates = boundedNodes(graph, memberIds, anchorNodeId, depth);
        int offset = decodeCursor(cursor, graph.version().id());
        if (offset > candidates.size()) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "INVALID_GRAPH_CURSOR", "Graph cursor is invalid.");
        }
        int end = Math.min(offset + limit, candidates.size());
        List<KnowledgeNode> page = candidates.subList(offset, end);
        Map<Long, KnowledgeGraphStore.NodeTranslation> translations = translations(graph, locale);
        Set<Long> pageIds = page.stream().map(KnowledgeNode::id).collect(java.util.stream.Collectors.toSet());
        List<EdgeView> edges = graph.relations().stream()
                .filter(edge -> pageIds.contains(edge.sourceNodeId()) && pageIds.contains(edge.targetNodeId()))
                .map(EdgeView::from)
                .toList();
        List<GoalNodeView> nodes = page.stream()
                .map(node -> GoalNodeView.from(localize(node, translations), mappings.get(node.id())))
                .toList();
        String nextCursor = end < candidates.size() ? encodeCursor(graph.version().id(), end) : null;
        return new GoalGraphView(
                Long.toString(graph.version().id()),
                graph.version().curriculumKey(),
                graph.version().versionLabel(),
                graph.version().publishedAt(),
                nodes,
                edges,
                nextCursor != null,
                nextCursor);
    }

    @Override
    @Transactional(readOnly = true)
    public AssessmentGraph publishedAssessmentGraph(long goalTemplateId) {
        GraphSnapshot graph = store.findPublishedByGoalTemplate(goalTemplateId)
                .orElseThrow(() -> notFound(
                        "PUBLISHED_GOAL_GRAPH_NOT_FOUND", "Published goal graph was not found."));
        Set<Long> memberIds = graph.goalKnowledge().stream()
                .filter(mapping -> mapping.goalTemplateId() == goalTemplateId)
                .map(GoalKnowledge::knowledgeNodeId)
                .collect(java.util.stream.Collectors.toSet());
        List<NodeSummary> nodes = graph.nodes().stream()
                .filter(node -> memberIds.contains(node.id()))
                .sorted(NODE_ORDER)
                .map(node -> new NodeSummary(node.id(), node.slug(), node.name()))
                .toList();
        return new AssessmentGraph(graph.version().id(), graph.version().curriculumKey(), nodes);
    }

    @Override
    @Transactional(readOnly = true)
    public LearningGraph publishedLearningGraph(long goalTemplateId) {
        GraphSnapshot graph = store.findPublishedByGoalTemplate(goalTemplateId)
                .orElseThrow(() -> notFound(
                        "PUBLISHED_GOAL_GRAPH_NOT_FOUND", "Published goal graph was not found."));
        Set<Long> members = graph.goalKnowledge().stream()
                .filter(mapping -> mapping.goalTemplateId() == goalTemplateId)
                .map(GoalKnowledge::knowledgeNodeId)
                .collect(java.util.stream.Collectors.toSet());
        Set<Long> blockedByPrerequisite = graph.relations().stream()
                .filter(KnowledgeRelation::activePrerequisite)
                .filter(edge -> members.contains(edge.sourceNodeId()) && members.contains(edge.targetNodeId()))
                .map(KnowledgeRelation::targetNodeId)
                .collect(java.util.stream.Collectors.toSet());
        Set<Long> roots = members.stream()
                .filter(id -> !blockedByPrerequisite.contains(id))
                .collect(java.util.stream.Collectors.toUnmodifiableSet());
        return new LearningGraph(graph.version().id(), graph.version().curriculumKey(), roots);
    }

    @Override
    @Transactional(readOnly = true)
    public List<NodeSummary> nodeSummaries(
            long graphVersionId, Set<Long> nodeIds, SupportedLocale locale) {
        GraphSnapshot graph = store.findVersion(graphVersionId)
                .orElseThrow(() -> notFound("GRAPH_VERSION_NOT_FOUND", "Graph version was not found."));
        Map<Long, KnowledgeGraphStore.NodeTranslation> translations = translations(graph, locale);
        return graph.nodes().stream()
                .filter(node -> nodeIds.contains(node.id()))
                .sorted(NODE_ORDER)
                .map(node -> localize(node, translations))
                .map(node -> new NodeSummary(node.id(), node.slug(), node.name()))
                .toList();
    }

    private Map<Long, KnowledgeGraphStore.NodeTranslation> translations(
            GraphSnapshot graph, SupportedLocale locale) {
        if (!locale.requiresTranslation()) {
            return Map.of();
        }
        return store.findNodeTranslations(graph.version().id(), locale.tag());
    }

    private KnowledgeNode localize(
            KnowledgeNode node, Map<Long, KnowledgeGraphStore.NodeTranslation> translations) {
        KnowledgeGraphStore.NodeTranslation translation = translations.get(node.id());
        if (translation == null) {
            return node;
        }
        return new KnowledgeNode(
                node.id(),
                node.graphVersionId(),
                node.slug(),
                translation.name(),
                translation.description(),
                node.category(),
                node.difficulty(),
                node.estimatedMinutes(),
                node.status());
    }

    @Transactional
    public ValidationView validate(long versionId, long actorUserId, String correlationId) {
        GraphSnapshot graph = store.findVersion(versionId)
                .orElseThrow(() -> notFound("GRAPH_VERSION_NOT_FOUND", "Graph version was not found."));
        if (graph.version().status() != com.skillpath.knowledge.domain.GraphVersionStatus.DRAFT) {
            throw new ApiException(HttpStatus.CONFLICT, "GRAPH_VERSION_STATE_CONFLICT", "Only a draft can be validated.");
        }
        GraphValidationResult result = validator.validate(graph);
        if (!result.valid()) {
            return ValidationView.from(graph, result, false);
        }
        try {
            store.markValidated(versionId, graph.version().version(), actorUserId, safeCorrelation(correlationId), clock.instant());
        } catch (IllegalStateException exception) {
            throw new ApiException(HttpStatus.CONFLICT, "GRAPH_VERSION_STATE_CONFLICT", "Graph version changed during validation.");
        }
        GraphSnapshot validated = store.findVersion(versionId).orElseThrow();
        return ValidationView.from(validated, result, true);
    }

    @Transactional
    public PublicationView publish(long versionId, long actorUserId, String correlationId) {
        GraphSnapshot graph = store.findVersion(versionId)
                .orElseThrow(() -> notFound("GRAPH_VERSION_NOT_FOUND", "Graph version was not found."));
        if (graph.version().status() != com.skillpath.knowledge.domain.GraphVersionStatus.VALIDATED
                && graph.version().status() != com.skillpath.knowledge.domain.GraphVersionStatus.PUBLISHED) {
            throw new ApiException(HttpStatus.CONFLICT, "GRAPH_VERSION_STATE_CONFLICT", "Only a validated graph can be published.");
        }
        GraphValidationResult result = validator.validate(graph);
        if (!result.valid()) {
            throw new ApiException(HttpStatus.UNPROCESSABLE_ENTITY, "GRAPH_VALIDATION_FAILED", "Graph is no longer valid.");
        }
        try {
            KnowledgeGraphStore.Publication publication =
                    store.publish(versionId, actorUserId, safeCorrelation(correlationId), clock.instant());
            return PublicationView.from(publication.graph(), publication.replayed());
        } catch (IllegalStateException | DataAccessException exception) {
            throw new ApiException(HttpStatus.CONFLICT, "GRAPH_PUBLICATION_CONFLICT", "Another graph publication won the race.");
        }
    }

    private GraphSnapshot publishedByNode(long nodeId) {
        return store.findPublishedByNode(nodeId)
                .orElseThrow(() -> notFound("KNOWLEDGE_NODE_NOT_FOUND", "Knowledge node was not found."));
    }

    private List<KnowledgeNode> boundedNodes(GraphSnapshot graph, Set<Long> members, Long anchor, int depth) {
        List<KnowledgeNode> all = graph.nodes().stream().filter(node -> members.contains(node.id())).sorted(NODE_ORDER).toList();
        if (anchor == null) {
            return all;
        }
        if (!members.contains(anchor)) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "INVALID_GRAPH_ANCHOR", "Anchor node is outside the goal graph.");
        }
        Map<Long, List<Long>> adjacency = new HashMap<>();
        for (KnowledgeRelation edge : graph.relations()) {
            if (members.contains(edge.sourceNodeId()) && members.contains(edge.targetNodeId())) {
                adjacency.computeIfAbsent(edge.sourceNodeId(), ignored -> new ArrayList<>()).add(edge.targetNodeId());
                adjacency.computeIfAbsent(edge.targetNodeId(), ignored -> new ArrayList<>()).add(edge.sourceNodeId());
            }
        }
        Set<Long> selected = new HashSet<>();
        ArrayDeque<NodeDepth> queue = new ArrayDeque<>();
        queue.add(new NodeDepth(anchor, 0));
        while (!queue.isEmpty()) {
            NodeDepth current = queue.removeFirst();
            if (!selected.add(current.id()) || current.depth() >= depth) {
                continue;
            }
            adjacency.getOrDefault(current.id(), List.of()).stream().sorted().forEach(id -> queue.addLast(new NodeDepth(id, current.depth() + 1)));
        }
        return all.stream().filter(node -> selected.contains(node.id())).toList();
    }

    private void validateBounds(int depth, int limit) {
        if (depth < 0 || depth > MAX_DEPTH || limit < 1 || limit > MAX_LIMIT) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "INVALID_GRAPH_BOUNDS", "Depth must be 0-10 and limit must be 1-200.");
        }
    }

    private String encodeCursor(long versionId, int offset) {
        return Base64.getUrlEncoder().withoutPadding().encodeToString((versionId + ":" + offset).getBytes(StandardCharsets.UTF_8));
    }

    private int decodeCursor(String cursor, long versionId) {
        if (cursor == null || cursor.isBlank()) {
            return 0;
        }
        try {
            String[] parts = new String(Base64.getUrlDecoder().decode(cursor), StandardCharsets.UTF_8).split(":", -1);
            if (parts.length != 2 || Long.parseLong(parts[0]) != versionId) {
                throw new IllegalArgumentException();
            }
            int offset = Integer.parseInt(parts[1]);
            if (offset < 0) {
                throw new IllegalArgumentException();
            }
            return offset;
        } catch (IllegalArgumentException exception) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "INVALID_GRAPH_CURSOR", "Graph cursor is invalid.");
        }
    }

    private String safeCorrelation(String value) {
        return value == null || value.isBlank() ? "unavailable" : value.substring(0, Math.min(value.length(), 100));
    }

    private ApiException notFound(String code, String detail) {
        return new ApiException(HttpStatus.NOT_FOUND, code, detail);
    }

    private record NodeDepth(long id, int depth) {}

    public record NodeView(
            String id,
            String graphVersionId,
            String slug,
            String name,
            String description,
            String category,
            int difficulty,
            int estimatedMinutes,
            String status) {
        static NodeView from(KnowledgeNode node, long versionId) {
            return new NodeView(Long.toString(node.id()), Long.toString(versionId), node.slug(), node.name(), node.description(), node.category(), node.difficulty(), node.estimatedMinutes(), node.status().name());
        }
    }

    public record GoalNodeView(
            String id,
            String slug,
            String name,
            String description,
            String category,
            int difficulty,
            int estimatedMinutes,
            String status,
            BigDecimal relevanceWeight,
            BigDecimal requiredMastery,
            boolean terminal) {
        static GoalNodeView from(KnowledgeNode node, GoalKnowledge mapping) {
            return new GoalNodeView(Long.toString(node.id()), node.slug(), node.name(), node.description(), node.category(), node.difficulty(), node.estimatedMinutes(), node.status().name(), mapping.relevanceWeight(), mapping.requiredMastery(), mapping.terminal());
        }
    }

    public record EdgeView(String id, String sourceNodeId, String targetNodeId, String type, BigDecimal strength, String status, String rationale) {
        static EdgeView from(KnowledgeRelation edge) {
            return new EdgeView(Long.toString(edge.id()), Long.toString(edge.sourceNodeId()), Long.toString(edge.targetNodeId()), edge.type().name(), edge.strength(), edge.status().name(), edge.rationale());
        }
    }

    public record GoalGraphView(
            String graphVersionId,
            String curriculumKey,
            String versionLabel,
            Instant publishedAt,
            List<GoalNodeView> nodes,
            List<EdgeView> edges,
            boolean truncated,
            String nextCursor) {}

    public record ViolationView(String code, String detail, List<String> nodePath) {}

    public record ValidationView(String graphVersionId, String status, boolean valid, boolean transitioned, List<ViolationView> violations) {
        static ValidationView from(GraphSnapshot graph, GraphValidationResult result, boolean transitioned) {
            return new ValidationView(
                    Long.toString(graph.version().id()),
                    graph.version().status().name(),
                    result.valid(),
                    transitioned,
                    result.violations().stream().map(v -> new ViolationView(v.code(), v.detail(), v.nodePath().stream().map(Object::toString).toList())).toList());
        }
    }

    public record PublicationView(String graphVersionId, String curriculumKey, String versionLabel, String status, Instant publishedAt, boolean replayed) {
        static PublicationView from(GraphSnapshot graph, boolean replayed) {
            return new PublicationView(Long.toString(graph.version().id()), graph.version().curriculumKey(), graph.version().versionLabel(), graph.version().status().name(), graph.version().publishedAt(), replayed);
        }
    }
}
