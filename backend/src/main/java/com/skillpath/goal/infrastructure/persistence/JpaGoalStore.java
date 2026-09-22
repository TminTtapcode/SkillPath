package com.skillpath.goal.infrastructure.persistence;

import com.skillpath.goal.application.GoalStore;
import com.skillpath.goal.domain.GoalTemplate;
import com.skillpath.goal.domain.UserGoal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import org.springframework.stereotype.Component;

@Component
class JpaGoalStore implements GoalStore {

    private final GoalTemplateJpaRepository templateRepository;
    private final UserGoalJpaRepository goalRepository;

    JpaGoalStore(
            GoalTemplateJpaRepository templateRepository, UserGoalJpaRepository goalRepository) {
        this.templateRepository = templateRepository;
        this.goalRepository = goalRepository;
    }

    @Override
    public List<GoalTemplate> findActiveTemplates() {
        return templateRepository.findAllByActiveTrueOrderByIdAsc().stream().map(this::map).toList();
    }

    @Override
    public Optional<GoalTemplate> findActiveTemplate(long templateId) {
        return templateRepository.findByIdAndActiveTrue(templateId).map(this::map);
    }

    @Override
    public Optional<UserGoal> findActiveByUserId(long userId) {
        return goalRepository.findByUserIdAndStatus(userId, UserGoal.Status.ACTIVE).map(this::map);
    }

    @Override
    public Optional<UserGoal> findByIdAndUserId(long goalId, long userId) {
        return goalRepository.findByIdAndUserId(goalId, userId).map(this::map);
    }

    @Override
    public UserGoal create(
            long userId,
            long templateId,
            LocalDate targetDate,
            String timezone,
            int defaultDailyMinutes,
            Instant now) {
        return map(goalRepository.saveAndFlush(new UserGoalEntity(
                userId, templateId, targetDate, timezone, defaultDailyMinutes, now)));
    }

    private GoalTemplate map(GoalTemplateEntity entity) {
        return new GoalTemplate(
                entity.id, entity.templateKey, entity.displayName, entity.description, entity.active);
    }

    private UserGoal map(UserGoalEntity entity) {
        return new UserGoal(
                entity.id,
                entity.userId,
                entity.goalTemplateId,
                entity.targetDate,
                entity.timezone,
                entity.defaultDailyMinutes,
                entity.status,
                entity.version,
                entity.createdAt,
                entity.updatedAt);
    }
}
