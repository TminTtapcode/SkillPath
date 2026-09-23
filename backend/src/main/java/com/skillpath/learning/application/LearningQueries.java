package com.skillpath.learning.application;

import java.util.List;

/** Immutable catalog and assignment views for the future planner. No selection policy lives here. */
public interface LearningQueries {
    List<LearningStore.SequenceDefinition> activeCatalog(long graphVersionId);
    List<LearningStore.TaskRow> assignedTasks(long userId, long goalId);
    List<PlannerVariant> activeVariants(long graphVersionId);
    List<AdaptiveVariant> activeAdaptiveVariants(long graphVersionId);
    AssignedSession activeAssignment(long userId, long goalId);
    List<AssignedTask> sessionTasks(long userId, long sessionId);
    List<PlannerTaskState> plannerTaskStates(long userId,long sessionId);
    long assignPlanner(long userId, long goalId, long graphVersionId,
                       List<PlannerSelection> selections, java.time.Instant now);
    long assignAdaptivePlanner(long userId,long goalId,long graphVersionId,
                               List<PlannerSelection> selections,java.time.Instant now);
    void supersedeUnstarted(long userId, long sessionId, java.time.Instant now);
    List<AssignedTask> revisePlannerAssignment(long userId,long sessionId,long graphVersionId,
                                               List<Long> expireTaskIds,List<PlannerSelection> selections,
                                               java.time.Instant now);

    record PlannerSelection(long decisionId, long templateVersionId, int position) {}
    record PlannerVariant(long templateVersionId, long nodeId, String activityType,
                          int difficulty, int minutes) {}
    record AdaptiveVariant(long templateVersionId,long nodeId,String activityType,
                           int difficulty,int minutes,String variantGroupKey,String evaluationMode) {}
    record AssignedSession(long id, String assignmentSource) {}
    record AssignedTask(long id, int position, String status, int plannedMinutes,
                        String titleEn, String titleVi) {}
    record PlannerTaskState(long id,int position,long templateVersionId,String status,
                            int plannedMinutes,Integer actualMinutes) {}
}
