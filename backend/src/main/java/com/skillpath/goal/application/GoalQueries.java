package com.skillpath.goal.application;

public interface GoalQueries {

    ActiveGoalView activeGoalForUser(long userId);

    ActiveGoalView lockActiveGoalForUser(long userId);

    record ActiveGoalView(long id, long goalTemplateId, String status) {}
}
