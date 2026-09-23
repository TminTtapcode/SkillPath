package com.skillpath.goal.application;

import java.time.LocalDate;

public interface GoalQueries {

    ActiveGoalView activeGoalForUser(long userId);

    ActiveGoalView lockActiveGoalForUser(long userId);

    PlanningGoal planningGoalForUser(long userId, boolean lock);

    record ActiveGoalView(long id, long goalTemplateId, String status) {}

    record PlanningGoal(long id, long goalTemplateId, LocalDate targetDate,
                        String timezone, int defaultDailyMinutes, String status) {}
}
