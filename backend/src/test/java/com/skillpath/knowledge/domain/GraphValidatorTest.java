package com.skillpath.knowledge.domain;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;

class GraphValidatorTest {

    private final GraphValidator validator = new GraphValidator();

    @Test
    void rejectsCycleWithDeterministicClosedPath() {
        GraphSnapshot graph = graph(List.of(edge(1, 1, 2), edge(2, 2, 3), edge(3, 3, 1)));

        GraphValidationResult result = validator.validate(graph);

        assertThat(result.valid()).isFalse();
        assertThat(result.violations())
                .filteredOn(violation -> violation.code().equals("PREREQUISITE_CYCLE"))
                .singleElement()
                .extracting(GraphViolation::nodePath)
                .isEqualTo(List.of(1L, 2L, 3L, 1L));
    }

    @Test
    void topologyIsStableAndNonPrerequisitesDoNotBlock() {
        GraphSnapshot graph = new GraphSnapshot(
                version(),
                nodes(),
                List.of(
                        edge(1, 1, 3),
                        edge(2, 2, 3),
                        new KnowledgeRelation(3, 1, 3, 1, KnowledgeRelationType.RELATED, BigDecimal.ONE, KnowledgeRelationStatus.ACTIVE, "related")),
                mappings());

        assertThat(validator.validate(graph).valid()).isTrue();
        assertThat(GraphAlgorithms.topologicalOrder(graph)).extracting(KnowledgeNode::id).containsExactly(1L, 2L, 3L);
        assertThat(GraphAlgorithms.traverse(graph, 3, true, true, 10)).extracting(KnowledgeNode::id).containsExactly(1L, 2L);
    }

    @Test
    void derivesFrontiersWithoutPersistingThem() {
        GraphSnapshot graph = graph(List.of(edge(1, 1, 2), edge(2, 2, 3)));

        PrerequisiteFrontier.Result result = PrerequisiteFrontier.derive(
                graph,
                Map.of(1L, new BigDecimal("0.80")),
                new BigDecimal("0.75"),
                new BigDecimal("0.80"),
                Set.of(2L, 3L));

        assertThat(result.innerFringe()).containsExactly(1L);
        assertThat(result.outerFringe()).containsExactly(2L);
        assertThat(result.blocked()).containsExactly(3L);
    }

    private GraphSnapshot graph(List<KnowledgeRelation> relations) {
        return new GraphSnapshot(version(), nodes(), relations, mappings());
    }

    private GraphVersion version() {
        return new GraphVersion(1, "JAVA_BACKEND", "test", GraphVersionStatus.DRAFT, 0, null);
    }

    private List<KnowledgeNode> nodes() {
        return List.of(node(1, "a"), node(2, "b"), node(3, "c"));
    }

    private KnowledgeNode node(long id, String slug) {
        return new KnowledgeNode(id, 1, slug, slug, "Outcome " + slug, "TEST", 1, 10, KnowledgeNodeStatus.ACTIVE);
    }

    private KnowledgeRelation edge(long id, long source, long target) {
        return new KnowledgeRelation(id, 1, source, target, KnowledgeRelationType.PREREQUISITE, BigDecimal.ONE, KnowledgeRelationStatus.ACTIVE, "test");
    }

    private List<GoalKnowledge> mappings() {
        return List.of(
                new GoalKnowledge(1, 1, 1, BigDecimal.ONE, new BigDecimal("0.8"), false),
                new GoalKnowledge(1, 1, 2, BigDecimal.ONE, new BigDecimal("0.8"), false),
                new GoalKnowledge(1, 1, 3, BigDecimal.ONE, new BigDecimal("0.8"), true));
    }
}
