package com.skillpath.knowledge.infrastructure.persistence;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;

@Entity
@Table(name = "knowledge_nodes")
class KnowledgeNodeEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    Long id;

    @Column(name = "graph_version_id", nullable = false)
    Long graphVersionId;

    @Column(nullable = false, length = 120)
    String slug;

    @Column(nullable = false, length = 160)
    String name;

    @Column(nullable = false, length = 2000)
    String description;

    @Column(nullable = false, length = 100)
    String category;

    @Column(nullable = false)
    int difficulty;

    @Column(name = "estimated_minutes", nullable = false)
    int estimatedMinutes;

    @Column(nullable = false, length = 20)
    String status;

    @Column(columnDefinition = "json")
    String metadata;

    @Column(name = "created_at", nullable = false)
    Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    Instant updatedAt;
}
