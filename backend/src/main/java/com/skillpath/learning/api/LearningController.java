package com.skillpath.learning.api;

import com.skillpath.auth.infrastructure.security.AuthenticatedUser;
import com.skillpath.learning.application.LearningService;
import com.skillpath.shared.api.ApiException;
import com.skillpath.shared.localization.SupportedLocale;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.List;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/learning")
public class LearningController {
    private final LearningService service;

    public LearningController(LearningService service) { this.service = service; }

    @GetMapping("/sequences")
    ResponseEntity<List<LearningService.SequenceView>> sequences(
            @AuthenticationPrincipal AuthenticatedUser user,
            @RequestHeader(value = HttpHeaders.ACCEPT_LANGUAGE, required = false) String language) {
        SupportedLocale locale = SupportedLocale.resolve(language);
        return ResponseEntity.ok().header(HttpHeaders.CONTENT_LANGUAGE, locale.tag())
                .body(service.sequences(user.userId(), locale));
    }

    @GetMapping("/sequences/{key}")
    ResponseEntity<LearningService.SequenceView> sequence(
            @AuthenticationPrincipal AuthenticatedUser user, @PathVariable String key,
            @RequestHeader(value = HttpHeaders.ACCEPT_LANGUAGE, required = false) String language) {
        SupportedLocale locale = SupportedLocale.resolve(language);
        return ResponseEntity.ok().header(HttpHeaders.CONTENT_LANGUAGE, locale.tag())
                .body(service.sequence(user.userId(), key, locale));
    }

    @PostMapping("/sequences/{key}/sessions")
    ResponseEntity<StartResponse> start(
            @AuthenticationPrincipal AuthenticatedUser user, @PathVariable String key,
            @RequestHeader("Idempotency-Key") String idempotencyKey) {
        var result = service.start(user.userId(), key, idempotencyKey);
        return ResponseEntity.status(result.created() ? HttpStatus.CREATED : HttpStatus.OK)
                .body(new StartResponse(Long.toString(result.sessionId()), result.replayed()));
    }

    @GetMapping("/sessions/active")
    ResponseEntity<LearningService.SessionView> active(
            @AuthenticationPrincipal AuthenticatedUser user,
            @RequestHeader(value = HttpHeaders.ACCEPT_LANGUAGE, required = false) String language) {
        SupportedLocale locale = SupportedLocale.resolve(language);
        var session = service.activeSession(user.userId(), locale);
        return session == null ? ResponseEntity.noContent().build()
                : ResponseEntity.ok().header(HttpHeaders.CONTENT_LANGUAGE, locale.tag()).body(session);
    }

    @GetMapping("/sessions/{id}")
    ResponseEntity<LearningService.SessionView> session(
            @AuthenticationPrincipal AuthenticatedUser user, @PathVariable String id,
            @RequestHeader(value = HttpHeaders.ACCEPT_LANGUAGE, required = false) String language) {
        SupportedLocale locale = SupportedLocale.resolve(language);
        return ResponseEntity.ok().header(HttpHeaders.CONTENT_LANGUAGE, locale.tag())
                .body(service.session(user.userId(), parseId(id), locale));
    }

    @PostMapping("/tasks/{id}/start")
    ResponseEntity<CommandResponse> startTask(@AuthenticationPrincipal AuthenticatedUser user,
            @PathVariable String id, @RequestHeader("Idempotency-Key") String key) {
        return command(user, id, "start", key, null, null);
    }

    @PostMapping("/tasks/{id}/complete")
    ResponseEntity<CommandResponse> complete(@AuthenticationPrincipal AuthenticatedUser user,
            @PathVariable String id, @RequestHeader("Idempotency-Key") String key,
            @Valid @RequestBody CompleteRequest body) {
        return command(user, id, "complete", key,
                new LearningService.Completion(body.actualMinutes(), body.completedStepIds()), null);
    }

    @PostMapping("/tasks/{id}/skip")
    ResponseEntity<CommandResponse> skip(@AuthenticationPrincipal AuthenticatedUser user,
            @PathVariable String id, @RequestHeader("Idempotency-Key") String key,
            @Valid @RequestBody ReasonRequest body) {
        return command(user, id, "skip", key, null, body.reasonCode());
    }

    @PostMapping("/tasks/{id}/blocked")
    ResponseEntity<CommandResponse> blocked(@AuthenticationPrincipal AuthenticatedUser user,
            @PathVariable String id, @RequestHeader("Idempotency-Key") String key,
            @Valid @RequestBody ReasonRequest body) {
        return command(user, id, "blocked", key, null, body.reasonCode());
    }

    @PostMapping("/tasks/{id}/resume")
    ResponseEntity<CommandResponse> resume(@AuthenticationPrincipal AuthenticatedUser user,
            @PathVariable String id, @RequestHeader("Idempotency-Key") String key) {
        return command(user, id, "resume", key, null, null);
    }

    @PostMapping("/tasks/{id}/abandon")
    ResponseEntity<CommandResponse> abandon(@AuthenticationPrincipal AuthenticatedUser user,
            @PathVariable String id, @RequestHeader("Idempotency-Key") String key,
            @Valid @RequestBody ReasonRequest body) {
        return command(user, id, "abandon", key, null, body.reasonCode());
    }

    private ResponseEntity<CommandResponse> command(AuthenticatedUser user, String id, String command,
            String key, LearningService.Completion completion, String reason) {
        var result = service.command(user.userId(), parseId(id), command, key, completion, reason);
        return ResponseEntity.ok(new CommandResponse(Long.toString(result.taskId()), result.status(), result.replayed()));
    }

    private long parseId(String value) {
        try {
            long id = Long.parseLong(value);
            if (id > 0) return id;
        } catch (NumberFormatException ignored) { }
        throw new ApiException(HttpStatus.BAD_REQUEST, "INVALID_LEARNING_ID", "ID is invalid.");
    }

    public record StartResponse(String sessionId, boolean replayed) {}
    public record CommandResponse(String taskId, String status, boolean replayed) {}
    public record CompleteRequest(@Min(0) @Max(360) int actualMinutes,
                                  @NotNull @Size(max = 20) List<@NotBlank String> completedStepIds) {}
    public record ReasonRequest(@NotBlank String reasonCode) {}
}
