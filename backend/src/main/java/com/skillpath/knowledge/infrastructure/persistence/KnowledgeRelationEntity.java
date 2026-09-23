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
@Table(name = "knowledge_relations")
class KnowledgeRelationEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    Long id;

    @Column(name = "graph_version_id", nullable = false)
    Long graphVersionId;

    @Column(name = "source_node_id", nullable = false)
    Long sourceNodeId;

    @Column(name = "target_node_id", nullable = false)
    Long targetNodeId;

    @Column(name = "relation_type", nullable = false, length = 30)
    String relationType;

    @Column(nullable = false, precision = 5, scale = 4)
    BigDecimal strength;

    @Column(nullable = false, length = 20)
    String status;

    @Column(nullable = false, length = 1000)
    String rationale;

    @Column(name = "created_at", nullable = false)
    Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    Instant updatedAt;
}
