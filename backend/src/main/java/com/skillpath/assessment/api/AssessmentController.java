package com.skillpath.assessment.api;

import com.skillpath.assessment.application.AssessmentService;
import com.skillpath.auth.infrastructure.security.AuthenticatedUser;
import com.skillpath.shared.api.ApiException;
import com.skillpath.shared.localization.SupportedLocale;
import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpHeaders;
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
@RequestMapping("/api/v1/assessments")
public class AssessmentController {

    private final AssessmentService service;

    public AssessmentController(AssessmentService service) {
        this.service = service;
    }

    @PostMapping("/diagnostic")
    ResponseEntity<SessionResponse> start(@AuthenticationPrincipal AuthenticatedUser principal) {
        AssessmentService.SessionView view = service.startDiagnostic(principal.userId());
        return ResponseEntity.status(view.created() ? HttpStatus.CREATED : HttpStatus.OK)
                .body(SessionResponse.from(view));
    }

    @GetMapping("/{sessionId}/next-question")
    ResponseEntity<QuestionResponse> nextQuestion(
            @AuthenticationPrincipal AuthenticatedUser principal,
            @PathVariable String sessionId,
            @RequestHeader(value = HttpHeaders.ACCEPT_LANGUAGE, required = false) String acceptLanguage) {
        SupportedLocale locale = SupportedLocale.resolve(acceptLanguage);
        AssessmentService.QuestionView view =
                service.nextQuestion(
                        principal.userId(),
                        parseId(sessionId, "INVALID_ASSESSMENT_SESSION_ID"),
                        locale);
        return view == null
                ? ResponseEntity.noContent().build()
                : ResponseEntity.ok()
                        .header(HttpHeaders.CONTENT_LANGUAGE, locale.tag())
                        .body(QuestionResponse.from(view));
    }

    @PostMapping("/{sessionId}/attempts")
    ResponseEntity<AttemptResponse> submit(
            @AuthenticationPrincipal AuthenticatedUser principal,
            @PathVariable String sessionId,
            @RequestHeader("Idempotency-Key") String idempotencyKey,
            @Valid @RequestBody SubmitAttemptRequest request) {
        AssessmentService.AttemptView view = service.submit(
                principal.userId(),
                parseId(sessionId, "INVALID_ASSESSMENT_SESSION_ID"),
                idempotencyKey,
                new AssessmentService.SubmitAnswer(
                        parseId(request.sessionQuestionId(), "INVALID_SESSION_QUESTION_ID"),
                        request.selectedOptionIds(),
                        request.selfConfidence(),
                        request.timeSpentSeconds()));
        return ResponseEntity.status(view.replayed() ? HttpStatus.OK : HttpStatus.CREATED)
                .body(AttemptResponse.from(view));
    }

    @GetMapping("/{sessionId}/result")
    ResponseEntity<ResultResponse> result(
            @AuthenticationPrincipal AuthenticatedUser principal,
            @PathVariable String sessionId,
            @RequestHeader(value = HttpHeaders.ACCEPT_LANGUAGE, required = false) String acceptLanguage) {
        SupportedLocale locale = SupportedLocale.resolve(acceptLanguage);
        ResultResponse body = ResultResponse.from(service.result(
                principal.userId(),
                parseId(sessionId, "INVALID_ASSESSMENT_SESSION_ID"),
                locale));
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_LANGUAGE, locale.tag())
                .body(body);
    }

    private long parseId(String value, String code) {
        try {
            long id = Long.parseLong(value);
            if (id <= 0) {
                throw new NumberFormatException();
            }
            return id;
        } catch (NumberFormatException exception) {
            throw new ApiException(HttpStatus.BAD_REQUEST, code, "ID is invalid.");
        }
    }

    public record SubmitAttemptRequest(
            @NotBlank String sessionQuestionId,
            @NotEmpty @Size(max = 20) List<@NotBlank @Size(max = 120) String> selectedOptionIds,
            @DecimalMin("0.0") @DecimalMax("1.0") BigDecimal selfConfidence,
            @Min(0) @Max(3600) int timeSpentSeconds) {}

    public record SessionResponse(
            String id,
            String goalId,
            String graphVersionId,
            String assessmentPolicyVersion,
            String status,
            Instant startedAt,
            Instant expiresAt,
            Instant completedAt,
            int answeredQuestions,
            int totalQuestions,
            boolean created,
            boolean resumed) {
        static SessionResponse from(AssessmentService.SessionView view) {
            return new SessionResponse(
                    view.id(),
                    view.goalId(),
                    view.graphVersionId(),
                    view.assessmentPolicyVersion(),
                    view.status(),
                    view.startedAt(),
                    view.expiresAt(),
                    view.completedAt(),
                    view.answeredQuestions(),
                    view.totalQuestions(),
                    view.created(),
                    view.resumed());
        }
    }

    public record QuestionOptionResponse(String id, String label) {}

    public record QuestionResponse(
            String sessionId,
            String sessionQuestionId,
            String questionVersionId,
            String type,
            String prompt,
            int difficulty,
            int estimatedSeconds,
            int position,
            int totalQuestions,
            Instant expiresAt,
            List<QuestionOptionResponse> options) {
        static QuestionResponse from(AssessmentService.QuestionView view) {
            return new QuestionResponse(
                    view.sessionId(),
                    view.sessionQuestionId(),
                    view.questionVersionId(),
                    view.type(),
                    view.prompt(),
                    view.difficulty(),
                    view.estimatedSeconds(),
                    view.position(),
                    view.totalQuestions(),
                    view.expiresAt(),
                    view.options().stream()
                            .map(option -> new QuestionOptionResponse(option.id(), option.label()))
                            .toList());
        }
    }

    public record AttemptResponse(String attemptId, String sessionStatus, boolean replayed) {
        static AttemptResponse from(AssessmentService.AttemptView view) {
            return new AttemptResponse(view.attemptId(), view.sessionStatus(), view.replayed());
        }
    }

    public record EvidenceResponse(
            String evidenceId,
            String attemptId,
            String knowledgeNodeId,
            String knowledgeNodeSlug,
            String knowledgeNodeName,
            String dimension,
            BigDecimal score,
            BigDecimal reliability,
            String evaluatorVersion,
            Instant observedAt) {
        static EvidenceResponse from(AssessmentService.EvidenceView view) {
            return new EvidenceResponse(
                    view.evidenceId(),
                    view.attemptId(),
                    view.knowledgeNodeId(),
                    view.knowledgeNodeSlug(),
                    view.knowledgeNodeName(),
                    view.dimension(),
                    view.score(),
                    view.reliability(),
                    view.evaluatorVersion(),
                    view.observedAt());
        }
    }

    public record ResultResponse(
            String sessionId,
            String graphVersionId,
            String assessmentPolicyVersion,
            Instant startedAt,
            Instant completedAt,
            int answeredQuestions,
            int totalQuestions,
            BigDecimal overallObjectiveScore,
            List<EvidenceResponse> evidence,
            String interpretation) {
        static ResultResponse from(AssessmentService.ResultView view) {
            return new ResultResponse(
                    view.sessionId(),
                    view.graphVersionId(),
                    view.assessmentPolicyVersion(),
                    view.startedAt(),
                    view.completedAt(),
                    view.answeredQuestions(),
                    view.totalQuestions(),
                    view.overallObjectiveScore(),
                    view.evidence().stream().map(EvidenceResponse::from).toList(),
                    view.interpretation());
        }
    }
}
