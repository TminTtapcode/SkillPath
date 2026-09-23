package com.skillpath.learning.application;

import java.util.List;

/** Immutable catalog and assignment views for the future planner. No selection policy lives here. */
public interface LearningQueries {
    List<LearningStore.SequenceDefinition> activeCatalog(long graphVersionId);
    List<LearningStore.TaskRow> assignedTasks(long userId, long goalId);
}
