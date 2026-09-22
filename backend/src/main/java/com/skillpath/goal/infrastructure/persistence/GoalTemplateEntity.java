package com.skillpath.goal.infrastructure.persistence;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;

@Entity
@Table(name = "goal_templates")
class GoalTemplateEntity {

    @Id
    Long id;

    @Column(name = "template_key", nullable = false, unique = true, length = 100)
    String templateKey;

    @Column(name = "display_name", nullable = false, length = 160)
    String displayName;

    @Column(nullable = false, length = 1000)
    String description;

    @Column(nullable = false)
    boolean active;

    @Column(name = "created_at", nullable = false)
    Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    Instant updatedAt;

    protected GoalTemplateEntity() {}
}
