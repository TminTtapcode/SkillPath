package com.skillpath.knowledge.domain;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

public final class GraphValidator {

    public GraphValidationResult validate(GraphSnapshot graph) {
        List<GraphViolation> violations = new ArrayList<>();
        Map<Long, KnowledgeNode> nodes = GraphAlgorithms.nodeMap(graph.nodes());
        Set<String> slugs = new HashSet<>();
        for (KnowledgeNode node : graph.nodes()) {
            if (node.graphVersionId() != graph.version().id()) {
                violations.add(GraphViolation.of("NODE_VERSION_MISMATCH", "Node " + node.id() + " belongs to another version."));
            }
            if (!slugs.add(node.slug())) {
                violations.add(GraphViolation.of("DUPLICATE_NODE_SLUG", "Duplicate slug: " + node.slug()));
            }
            if (node.difficulty() < 1 || node.difficulty() > 5 || node.estimatedMinutes() <= 0) {
                violations.add(GraphViolation.of("INVALID_NODE_RANGE", "Node " + node.id() + " has invalid difficulty or duration."));
            }
        }

        Set<String> activeEdges = new HashSet<>();
        for (KnowledgeRelation edge : graph.relations()) {
            if (edge.graphVersionId() != graph.version().id()
                    || !nodes.containsKey(edge.sourceNodeId())
                    || !nodes.containsKey(edge.targetNodeId())) {
                violations.add(GraphViolation.of("DANGLING_OR_CROSS_VERSION_RELATION", "Relation " + edge.id() + " has invalid endpoints."));
            }
            if (edge.sourceNodeId() == edge.targetNodeId()) {
                violations.add(GraphViolation.of("SELF_LOOP", "Relation " + edge.id() + " is a self-loop."));
            }
            if (edge.strength().compareTo(BigDecimal.ZERO) < 0 || edge.strength().compareTo(BigDecimal.ONE) > 0) {
                violations.add(GraphViolation.of("INVALID_RELATION_STRENGTH", "Relation " + edge.id() + " strength is outside [0,1]."));
            }
            if (edge.status() == KnowledgeRelationStatus.ACTIVE) {
                String key = edge.sourceNodeId() + ":" + edge.targetNodeId() + ":" + edge.type();
                if (!activeEdges.add(key)) {
                    violations.add(GraphViolation.of("DUPLICATE_ACTIVE_RELATION", "Duplicate active relation " + key));
                }
            }
        }

        for (GoalKnowledge mapping : graph.goalKnowledge()) {
            KnowledgeNode node = nodes.get(mapping.knowledgeNodeId());
            if (mapping.graphVersionId() != graph.version().id() || node == null) {
                violations.add(GraphViolation.of("INVALID_GOAL_MAPPING", "Goal mapping references an invalid node."));
            } else if (node.status() == KnowledgeNodeStatus.ARCHIVED) {
                violations.add(GraphViolation.of("ARCHIVED_GOAL_NODE", "Archived node " + node.id() + " is mapped to a goal."));
            }
            if (outsideUnit(mapping.relevanceWeight()) || outsideUnit(mapping.requiredMastery())) {
                violations.add(GraphViolation.of("INVALID_GOAL_WEIGHT", "Goal mapping values must be inside [0,1]."));
            }
        }
        if (!graph.goalKnowledge().isEmpty() && graph.goalKnowledge().stream().noneMatch(GoalKnowledge::terminal)) {
            violations.add(GraphViolation.of("GOAL_HAS_NO_TERMINAL", "Each mapped goal requires a terminal node."));
        }

        List<Long> cycle = deterministicCycle(graph, nodes);
        if (!cycle.isEmpty()) {
            violations.add(new GraphViolation("PREREQUISITE_CYCLE", "Active prerequisites contain a cycle.", cycle));
        }
        violations.sort(Comparator.comparing(GraphViolation::code).thenComparing(GraphViolation::detail));
        return new GraphValidationResult(violations);
    }

    private boolean outsideUnit(BigDecimal value) {
        return value == null || value.compareTo(BigDecimal.ZERO) < 0 || value.compareTo(BigDecimal.ONE) > 0;
    }

    private List<Long> deterministicCycle(GraphSnapshot graph, Map<Long, KnowledgeNode> nodes) {
        Map<Long, List<Long>> outgoing = new HashMap<>();
        graph.relations().stream()
                .filter(KnowledgeRelation::activePrerequisite)
                .filter(edge -> nodes.containsKey(edge.sourceNodeId()) && nodes.containsKey(edge.targetNodeId()))
                .forEach(edge -> outgoing.computeIfAbsent(edge.sourceNodeId(), ignored -> new ArrayList<>()).add(edge.targetNodeId()));
        outgoing.values().forEach(list -> list.sort(Long::compareTo));
        Set<Long> visited = new HashSet<>();
        Set<Long> active = new HashSet<>();
        List<Long> path = new ArrayList<>();
        List<Long> starts = nodes.keySet().stream().sorted().toList();
        for (long start : starts) {
            List<Long> cycle = dfs(start, outgoing, visited, active, path);
            if (!cycle.isEmpty()) {
                return cycle;
            }
        }
        return List.of();
    }

    private List<Long> dfs(long node, Map<Long, List<Long>> outgoing, Set<Long> visited, Set<Long> active, List<Long> path) {
        if (active.contains(node)) {
            int start = path.indexOf(node);
            List<Long> cycle = new ArrayList<>(path.subList(start, path.size()));
            cycle.add(node);
            return List.copyOf(cycle);
        }
        if (!visited.add(node)) {
            return List.of();
        }
        active.add(node);
        path.add(node);
        for (long next : outgoing.getOrDefault(node, List.of())) {
            List<Long> cycle = dfs(next, outgoing, visited, active, path);
            if (!cycle.isEmpty()) {
                return cycle;
            }
        }
        active.remove(node);
        path.remove(path.size() - 1);
        return List.of();
    }
}
