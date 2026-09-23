package com.skillpath.knowledge.domain;

import java.util.List;

public record GraphSnapshot(
        GraphVersion version,
        List<KnowledgeNode> nodes,
        List<KnowledgeRelation> relations,
        List<GoalKnowledge> goalKnowledge) {

    public GraphSnapshot {
        nodes = List.copyOf(nodes);
        relations = List.copyOf(relations);
        goalKnowledge = List.copyOf(goalKnowledge);
    }
}
