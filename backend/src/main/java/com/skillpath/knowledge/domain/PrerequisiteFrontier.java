package com.skillpath.knowledge.domain;

import java.math.BigDecimal;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

public final class PrerequisiteFrontier {

    private PrerequisiteFrontier() {}

    public static Result derive(
            GraphSnapshot graph,
            Map<Long, BigDecimal> mastery,
            BigDecimal masteryThreshold,
            BigDecimal hardPrerequisiteStrength,
            Set<Long> nodesWithActiveTimeFitTask) {
        Set<Long> mastered = new HashSet<>();
        graph.nodes().forEach(node -> {
            if (mastery.getOrDefault(node.id(), BigDecimal.ZERO).compareTo(masteryThreshold) >= 0) {
                mastered.add(node.id());
            }
        });
        Set<Long> inner = new HashSet<>();
        Set<Long> outer = new HashSet<>();
        Set<Long> blocked = new HashSet<>();
        for (KnowledgeNode node : graph.nodes()) {
            if (mastered.contains(node.id())) {
                boolean unlocks = graph.relations().stream()
                        .anyMatch(edge -> edge.activePrerequisite()
                                && edge.sourceNodeId() == node.id()
                                && !mastered.contains(edge.targetNodeId()));
                if (unlocks) {
                    inner.add(node.id());
                }
                continue;
            }
            boolean unsatisfied = graph.relations().stream()
                    .filter(KnowledgeRelation::activePrerequisite)
                    .filter(edge -> edge.targetNodeId() == node.id())
                    .filter(edge -> edge.strength().compareTo(hardPrerequisiteStrength) >= 0)
                    .anyMatch(edge -> !mastered.contains(edge.sourceNodeId()));
            if (unsatisfied) {
                blocked.add(node.id());
            } else if (nodesWithActiveTimeFitTask.contains(node.id())) {
                outer.add(node.id());
            }
        }
        return new Result(Set.copyOf(inner), Set.copyOf(outer), Set.copyOf(blocked));
    }

    public record Result(Set<Long> innerFringe, Set<Long> outerFringe, Set<Long> blocked) {}
}
