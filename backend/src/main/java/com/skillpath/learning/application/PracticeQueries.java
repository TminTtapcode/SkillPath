package com.skillpath.learning.application;

import java.util.List;

public interface PracticeQueries {
    List<PracticeExercise> taskPractices(long taskTemplateVersionId);

    record PracticeExercise(
            long id,
            int position,
            String prompt,
            String starterCode,
            String expectedOutput) {}
}
