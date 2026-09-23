package com.skillpath.knowledge.domain;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.PriorityQueue;
import java.util.Set;

public final class GraphAlgorithms {

    private static final Comparator<KnowledgeNode> STABLE_NODE_ORDER =
            Comparator.comparing(KnowledgeNode::slug).thenComparingLong(KnowledgeNode::id);

    private GraphAlgorithms() {}

    public static List<KnowledgeNode> topologicalOrder(GraphSnapshot graph) {
        Map<Long, KnowledgeNode> nodes = nodeMap(graph.nodes());
        Map<Long, Integer> indegree = new HashMap<>();
        Map<Long, List<Long>> outgoing = new HashMap<>();
        nodes.keySet().forEach(id -> indegree.put(id, 0));
        for (KnowledgeRelation edge : graph.relations()) {
            if (!edge.activePrerequisite() || !nodes.containsKey(edge.sourceNodeId()) || !nodes.containsKey(edge.targetNodeId())) {
                continue;
            }
            outgoing.computeIfAbsent(edge.sourceNodeId(), ignored -> new ArrayList<>()).add(edge.targetNodeId());
            indegree.computeIfPresent(edge.targetNodeId(), (ignored, value) -> value + 1);
        }
        PriorityQueue<KnowledgeNode> ready = new PriorityQueue<>(STABLE_NODE_ORDER);
        nodes.values().stream().filter(node -> indegree.get(node.id()) == 0).forEach(ready::add);
        List<KnowledgeNode> ordered = new ArrayList<>();
        while (!ready.isEmpty()) {
            KnowledgeNode node = ready.remove();
            ordered.add(node);
            for (Long target : outgoing.getOrDefault(node.id(), List.of())) {
                int next = indegree.computeIfPresent(target, (ignored, value) -> value - 1);
                if (next == 0) {
                    ready.add(nodes.get(target));
                }
            }
        }
        return List.copyOf(ordered);
    }

    public static List<KnowledgeNode> traverse(
            GraphSnapshot graph, long nodeId, boolean prerequisites, boolean transitive, int limit) {
        if (limit < 1) {
            return List.of();
        }
        Map<Long, KnowledgeNode> nodes = nodeMap(graph.nodes());
        Map<Long, List<Long>> adjacency = new HashMap<>();
        for (KnowledgeRelation edge : graph.relations()) {
            if (!edge.activePrerequisite()) {
                continue;
            }
            long from = prerequisites ? edge.targetNodeId() : edge.sourceNodeId();
            long to = prerequisites ? edge.sourceNodeId() : edge.targetNodeId();
            adjacency.computeIfAbsent(from, ignored -> new ArrayList<>()).add(to);
        }
        Set<Long> visited = new LinkedHashSet<>();
        ArrayDeque<Long> queue = new ArrayDeque<>(adjacency.getOrDefault(nodeId, List.of()));
        while (!queue.isEmpty() && visited.size() < limit) {
            long current = queue.removeFirst();
            if (!visited.add(current) || !transitive) {
                continue;
            }
            adjacency.getOrDefault(current, List.of()).stream()
                    .sorted(Comparator.comparing(id -> nodes.get(id), STABLE_NODE_ORDER))
                    .forEach(queue::addLast);
        }
        return visited.stream().map(nodes::get).filter(java.util.Objects::nonNull).sorted(STABLE_NODE_ORDER).toList();
    }

    static Map<Long, KnowledgeNode> nodeMap(Collection<KnowledgeNode> nodes) {
        Map<Long, KnowledgeNode> result = new HashMap<>();
        nodes.forEach(node -> result.put(node.id(), node));
        return result;
    }

    static Set<Long> reachableFrom(long start, Map<Long, List<Long>> adjacency) {
        Set<Long> visited = new HashSet<>();
        ArrayDeque<Long> queue = new ArrayDeque<>();
        queue.add(start);
        while (!queue.isEmpty()) {
            long current = queue.removeFirst();
            if (visited.add(current)) {
                adjacency.getOrDefault(current, List.of()).forEach(queue::addLast);
            }
        }
        return visited;
    }
}
