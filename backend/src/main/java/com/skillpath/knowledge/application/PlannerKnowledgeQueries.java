package com.skillpath.knowledge.application;

import java.math.BigDecimal;
import java.util.List;

/** Version-pinned, immutable goal graph for planner calculations. */
public interface PlannerKnowledgeQueries {
    PlanningGraph planningGraph(long goalTemplateId);

    record Node(long id, String slug, String name, String nameVi, String status,
                BigDecimal relevance, BigDecimal requiredMastery, boolean terminal, int topologicalOrder) {}
    record Edge(long sourceId, long targetId, String type, BigDecimal strength) {}
    record PlanningGraph(long graphVersionId, List<Node> nodes, List<Edge> edges) {
        public PlanningGraph {
            nodes = List.copyOf(nodes);
            edges = List.copyOf(edges);
        }
    }
}
