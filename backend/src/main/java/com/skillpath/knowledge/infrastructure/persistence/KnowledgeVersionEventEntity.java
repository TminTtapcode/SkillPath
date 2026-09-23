package com.skillpath.knowledge.infrastructure.persistence;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;

@Entity
@Table(name = "knowledge_version_events")
class KnowledgeVersionEventEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    Long id;

    @Column(name = "graph_version_id", nullable = false)
    Long graphVersionId;

    @Column(name = "event_type", nullable = false, length = 30)
    String eventType;

    @Column(name = "actor_user_id")
    Long actorUserId;

    @Column(name = "correlation_id", nullable = false, length = 100)
    String correlationId;

    @Column(name = "from_status", length = 20)
    String fromStatus;

    @Column(name = "to_status", nullable = false, length = 20)
    String toStatus;

    @Column(columnDefinition = "json")
    String summary;

    @Column(name = "created_at", nullable = false)
    Instant createdAt;
}
