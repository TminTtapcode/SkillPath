package com.skillpath.learning.application;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

public interface LearningStore {
    List<SequenceDefinition> activeSequences(long graphVersionId);
    List<PlannerVariant> activeVariants(long graphVersionId);
    List<PlannerVariant> activeAdaptiveVariants(long graphVersionId);
    Optional<SequenceDefinition> activeSequence(long graphVersionId, String key);
    Optional<SessionRow> activeSession(long userId, long goalId);
    Optional<SessionRow> session(long userId, long sessionId, boolean lock);
    Optional<Long> sessionIdForTask(long userId, long taskId);
    List<TaskRow> tasks(long userId, long sessionId);
    Optional<TaskRow> task(long userId, long taskId, boolean lock);
    long createSession(long userId, long goalId, SequenceDefinition sequence, Instant now);
    long createPlannerSession(long userId, long goalId, long graphVersionId,
                             List<PlannerAssignedTask> tasks, Instant now);
    List<TaskRow> appendPlannerTasks(long userId,long sessionId,List<PlannerAssignedTask> tasks,Instant now);
    boolean transitionTask(long taskId, long version, String status, Integer actualMinutes, Instant now);
    void closeSession(long sessionId, String status, Instant now);
    void addEvent(long taskId, long userId, long commandId, String from, String to, String reason, Instant now);
    Optional<Receipt> receipt(long userId, String key);
    long addReceipt(long userId, String key, String command, String hash, long resourceId, String outcome, Instant now);

    record Step(String id, String label) {}
    record LocalizedText(String en, String vi) {
        public String forVietnamese(boolean vietnamese) { return vietnamese ? vi : en; }
    }
    record CatalogTask(long templateVersionId, int position, String activityType, String evaluationMode,
                       int minutes, long primaryNodeId, LocalizedText title, LocalizedText instructions,
                       LocalizedText resourceTitle, LocalizedText resourceBody,
                       List<Step> checklistEn, List<Step> checklistVi) {}
    record PlannerVariant(CatalogTask content, int difficulty, String variantGroupKey) {}
    record PlannerAssignedTask(long decisionId, CatalogTask content) {}
    record SequenceDefinition(long id, String key, int version, long graphVersionId,
                              LocalizedText title, LocalizedText description, List<CatalogTask> tasks) {
        public int totalMinutes() { return tasks.stream().mapToInt(CatalogTask::minutes).sum(); }
    }
    record SessionRow(long id, long userId, long goalId, long sequenceId, long graphVersionId,
                      String sequenceKey, LocalizedText title, String status, String assignmentSource, Instant startedAt,
                      Instant completedAt, long version) {}
    record TaskRow(long id, long sessionId, int position, long templateVersionId, String activityType,
                   String evaluationMode, int plannedMinutes, Integer actualMinutes, String status,
                   long version, LocalizedText title, LocalizedText instructions,
                   LocalizedText resourceTitle, LocalizedText resourceBody,
                   List<Step> checklistEn, List<Step> checklistVi) {}
    record Receipt(long id, String command, String hash, long resourceId, String outcome) {}
}
