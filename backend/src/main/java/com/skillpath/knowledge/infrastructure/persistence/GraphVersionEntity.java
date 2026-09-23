package com.skillpath.knowledge.infrastructure.persistence;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.time.Instant;

@Entity
@Table(name = "knowledge_graph_versions")
class GraphVersionEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    Long id;

    @Column(name = "curriculum_key", nullable = false, length = 100)
    String curriculumKey;

    @Column(name = "version_label", nullable = false, length = 50)
    String versionLabel;

    @Column(nullable = false, length = 20)
    String status;

    @Version
    @Column(nullable = false)
    long version;

    @Column(name = "validated_at")
    Instant validatedAt;

    @Column(name = "validated_by")
    Long validatedBy;

    @Column(name = "published_at")
    Instant publishedAt;

    @Column(name = "published_by")
    Long publishedBy;

    @Column(name = "retired_at")
    Instant retiredAt;

    @Column(name = "retired_by")
    Long retiredBy;

    @Column(name = "created_at", nullable = false)
    Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    Instant updatedAt;
}
