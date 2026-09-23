package com.skillpath.learning.api;

import com.skillpath.learning.application.PracticeQueries;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/v1/learning")
public class PracticeController {

    private final PracticeQueries practiceQueries;

    public PracticeController(PracticeQueries practiceQueries) {
        this.practiceQueries = practiceQueries;
    }

    @GetMapping("/tasks/{taskTemplateVersionId}/practices")
    public ResponseEntity<List<PracticeQueries.PracticeExercise>> getTaskPractices(
            @PathVariable long taskTemplateVersionId) {
        return ResponseEntity.ok(practiceQueries.taskPractices(taskTemplateVersionId));
    }
}
