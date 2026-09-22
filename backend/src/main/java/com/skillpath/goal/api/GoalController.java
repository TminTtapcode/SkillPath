package com.skillpath.goal.api;

import com.skillpath.auth.infrastructure.security.AuthenticatedUser;
import com.skillpath.goal.application.GoalService;
import com.skillpath.goal.domain.GoalTemplate;
import com.skillpath.goal.domain.UserGoal;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.LocalDate;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1")
public class GoalController {

    private final GoalService goalService;

    public GoalController(GoalService goalService) {
        this.goalService = goalService;
    }

    @GetMapping("/goal-templates")
    List<GoalTemplateResponse> templates() {
        return goalService.listTemplates().stream().map(GoalTemplateResponse::from).toList();
    }

    @PostMapping("/goals")
    ResponseEntity<GoalResponse> create(
            @AuthenticationPrincipal AuthenticatedUser principal,
            @RequestHeader("Idempotency-Key") String idempotencyKey,
            @Valid @RequestBody CreateGoalRequest body) {
        GoalService.CreationResult result = goalService.create(
                principal.userId(),
                idempotencyKey,
                new GoalService.CreateGoal(
                        body.goalTemplateId(),
                        body.targetDate(),
                        body.timezone(),
                        body.defaultDailyMinutes()));
        return ResponseEntity.status(result.replayed() ? HttpStatus.OK : HttpStatus.CREATED)
                .body(GoalResponse.from(result.goal()));
    }

    @GetMapping("/goals/active")
    GoalResponse active(@AuthenticationPrincipal AuthenticatedUser principal) {
        return GoalResponse.from(goalService.activeGoal(principal.userId()));
    }

    public record CreateGoalRequest(
            @NotBlank String goalTemplateId,
            @NotNull LocalDate targetDate,
            @NotBlank @Size(max = 64) String timezone,
            @Min(30) @Max(180) int defaultDailyMinutes) {}

    public record GoalTemplateResponse(
            String id, String key, String displayName, String description) {
        static GoalTemplateResponse from(GoalTemplate template) {
            return new GoalTemplateResponse(
                    Long.toString(template.id()),
                    template.key(),
                    template.displayName(),
                    template.description());
        }
    }

    public record GoalResponse(
            String id,
            String goalTemplateId,
            LocalDate targetDate,
            String timezone,
            int defaultDailyMinutes,
            String status,
            long version) {
        static GoalResponse from(UserGoal goal) {
            return new GoalResponse(
                    Long.toString(goal.id()),
                    Long.toString(goal.goalTemplateId()),
                    goal.targetDate(),
                    goal.timezone(),
                    goal.defaultDailyMinutes(),
                    goal.status().name(),
                    goal.version());
        }
    }
}
