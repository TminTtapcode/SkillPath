package com.skillpath.goal.infrastructure.persistence;

import com.skillpath.goal.domain.UserGoal;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.time.Instant;
import java.time.LocalDate;

@Entity
@Table(name = "user_goals")
class UserGoalEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    Long id;

    @Column(name = "user_id", nullable = false)
    Long userId;

    @Column(name = "goal_template_id", nullable = false)
    Long goalTemplateId;

    @Column(name = "target_date", nullable = false)
    LocalDate targetDate;

    @Column(nullable = false, length = 64)
    String timezone;

    @Column(name = "default_daily_minutes", nullable = false)
    int defaultDailyMinutes;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    UserGoal.Status status;

    @Column(name = "active_owner_id", insertable = false, updatable = false)
    Long activeOwnerId;

    @Version
    long version;

    @Column(name = "created_at", nullable = false)
    Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    Instant updatedAt;

    protected UserGoalEntity() {}

    UserGoalEntity(
            long userId,
            long goalTemplateId,
            LocalDate targetDate,
            String timezone,
            int defaultDailyMinutes,
            Instant now) {
        this.userId = userId;
        this.goalTemplateId = goalTemplateId;
        this.targetDate = targetDate;
        this.timezone = timezone;
        this.defaultDailyMinutes = defaultDailyMinutes;
        this.status = UserGoal.Status.ACTIVE;
        this.createdAt = now;
        this.updatedAt = now;
    }
}
