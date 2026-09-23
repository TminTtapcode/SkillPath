package com.skillpath.knowledge.infrastructure.persistence;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.Instant;

@Entity
@Table(name = "goal_knowledge")
class GoalKnowledgeEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    Long id;

    @Column(name = "graph_version_id", nullable = false)
    Long graphVersionId;

    @Column(name = "goal_template_id", nullable = false)
    Long goalTemplateId;

    @Column(name = "knowledge_node_id", nullable = false)
    Long knowledgeNodeId;

    @Column(name = "relevance_weight", nullable = false, precision = 5, scale = 4)
    BigDecimal relevanceWeight;

    @Column(name = "required_mastery", nullable = false, precision = 5, scale = 4)
    BigDecimal requiredMastery;

    @Column(name = "is_terminal", nullable = false)
    boolean terminal;

    @Column(name = "created_at", nullable = false)
    Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    Instant updatedAt;
}
