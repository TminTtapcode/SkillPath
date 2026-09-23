package com.skillpath.goal.application;

import com.skillpath.goal.domain.GoalTemplate;
import com.skillpath.goal.domain.UserGoal;
import com.skillpath.shared.localization.SupportedLocale;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

public interface GoalStore {

    List<GoalTemplate> findActiveTemplates(SupportedLocale locale);

    Optional<GoalTemplate> findActiveTemplate(long templateId);

    Optional<UserGoal> findActiveByUserId(long userId);

    Optional<UserGoal> findActiveByUserIdForUpdate(long userId);

    Optional<UserGoal> findByIdAndUserId(long goalId, long userId);

    UserGoal create(
            long userId,
            long templateId,
            LocalDate targetDate,
            String timezone,
            int defaultDailyMinutes,
            Instant now);
}
