package com.skillpath.assessment.api;

import com.skillpath.assessment.application.AssessmentService;
import com.skillpath.auth.infrastructure.security.AuthenticatedUser;
import com.skillpath.shared.api.ApiException;
import com.skillpath.shared.localization.SupportedLocale;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.util.List;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@ConditionalOnProperty(name = "skillpath.phase7.task-check-enabled", havingValue = "true")
@RequestMapping("/api/v1/learning/tasks/{taskId}/check")
public class TaskCheckController {
    private final AssessmentService assessments;

    public TaskCheckController(AssessmentService assessments) { this.assessments = assessments; }

    @GetMapping
    ResponseEntity<AssessmentService.TaskCheckView> read(
            @AuthenticationPrincipal AuthenticatedUser principal,
            @PathVariable String taskId,
            @RequestHeader(value=HttpHeaders.ACCEPT_LANGUAGE,required=false) String acceptLanguage) {
        SupportedLocale locale=SupportedLocale.resolve(acceptLanguage);
        return ResponseEntity.ok().header(HttpHeaders.CONTENT_LANGUAGE,locale.tag())
                .body(assessments.taskCheck(principal.userId(),id(taskId),locale));
    }

    @PostMapping("/attempts")
    ResponseEntity<AssessmentService.TaskCheckAttemptView> submit(
            @AuthenticationPrincipal AuthenticatedUser principal,
            @PathVariable String taskId,
            @RequestHeader("Idempotency-Key") String key,
            @Valid @RequestBody AnswerRequest request) {
        var result=assessments.submitTaskCheck(principal.userId(),id(taskId),key,
                new AssessmentService.TaskCheckAnswer(request.selectedOptionIds(),
                        request.timeSpentSeconds(),request.actualMinutes()));
        return ResponseEntity.status(result.replayed()?HttpStatus.OK:HttpStatus.CREATED).body(result);
    }

    private long id(String text) {
        try { long value=Long.parseLong(text); if(value>0)return value; }
        catch(NumberFormatException ignored) { }
        throw new ApiException(HttpStatus.BAD_REQUEST,"INVALID_LEARNING_TASK_ID","Task ID is invalid.");
    }

    public record AnswerRequest(@NotEmpty @Size(max=20) List<@NotBlank @Size(max=120) String> selectedOptionIds,
                                @Min(0) @Max(3600) int timeSpentSeconds,
                                @Min(0) @Max(360) int actualMinutes) {}
}
