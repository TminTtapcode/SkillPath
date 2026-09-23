package com.skillpath.goal.infrastructure.persistence;

import com.skillpath.goal.application.GoalStore;
import com.skillpath.goal.domain.GoalTemplate;
import com.skillpath.goal.domain.UserGoal;
import com.skillpath.shared.localization.SupportedLocale;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

@Component
class JpaGoalStore implements GoalStore {

    private final GoalTemplateJpaRepository templateRepository;
    private final UserGoalJpaRepository goalRepository;
    private final JdbcTemplate jdbcTemplate;

    JpaGoalStore(
            GoalTemplateJpaRepository templateRepository,
            UserGoalJpaRepository goalRepository,
            JdbcTemplate jdbcTemplate) {
        this.templateRepository = templateRepository;
        this.goalRepository = goalRepository;
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    public List<GoalTemplate> findActiveTemplates(SupportedLocale locale) {
        List<GoalTemplateEntity> templates = templateRepository.findAllByActiveTrueOrderByIdAsc();
        Map<Long, TemplateTranslation> translations = translations(locale).stream()
                .collect(Collectors.toMap(TemplateTranslation::goalTemplateId, Function.identity()));
        return templates.stream().map(entity -> map(entity, translations.get(entity.id))).toList();
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
    public Optional<UserGoal> findActiveByUserIdForUpdate(long userId) {
        return goalRepository
                .findByUserIdAndStatusForUpdate(userId, UserGoal.Status.ACTIVE)
                .map(this::map);
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
        return map(entity, null);
    }

    private GoalTemplate map(GoalTemplateEntity entity, TemplateTranslation translation) {
        return new GoalTemplate(
                entity.id,
                entity.templateKey,
                translation == null ? entity.displayName : translation.displayName(),
                translation == null ? entity.description : translation.description(),
                entity.active);
    }

    private List<TemplateTranslation> translations(SupportedLocale locale) {
        if (!locale.requiresTranslation()) {
            return List.of();
        }
        return jdbcTemplate.query(
                """
                SELECT goal_template_id, display_name, description
                FROM goal_template_translations
                WHERE locale = ?
                """,
                (resultSet, rowNumber) -> new TemplateTranslation(
                        resultSet.getLong("goal_template_id"),
                        resultSet.getString("display_name"),
                        resultSet.getString("description")),
                locale.tag());
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

    private record TemplateTranslation(long goalTemplateId, String displayName, String description) {}
}
