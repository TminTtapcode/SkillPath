package com.skillpath.assessment.application;

import com.skillpath.assessment.application.AssessmentStore.AttemptRecord;
import com.skillpath.assessment.application.AssessmentStore.ResultRecord;
import com.skillpath.assessment.application.AssessmentStore.SessionQuestionRecord;
import com.skillpath.assessment.application.AssessmentStore.SessionRecord;
import com.skillpath.assessment.domain.ObjectiveQuestion;
import com.skillpath.assessment.domain.ObjectiveScoringPolicyV1;
import com.skillpath.goal.application.GoalQueries;
import com.skillpath.knowledge.application.AssessmentKnowledgeQueries;
import com.skillpath.shared.api.ApiException;
import com.skillpath.shared.localization.SupportedLocale;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AssessmentService {

    private static final int DIAGNOSTIC_QUESTION_COUNT = 8;
    private static final Duration SESSION_DURATION = Duration.ofDays(7);
    private static final Pattern IDEMPOTENCY_KEY = Pattern.compile("[A-Za-z0-9._:-]{1,128}");

    private final AssessmentStore store;
    private final GoalQueries goalQueries;
    private final AssessmentKnowledgeQueries knowledgeQueries;
    private final Clock clock;
    private final ObjectiveScoringPolicyV1 scoringPolicy = new ObjectiveScoringPolicyV1();

    public AssessmentService(
            AssessmentStore store,
            GoalQueries goalQueries,
            AssessmentKnowledgeQueries knowledgeQueries,
            Clock clock) {
        this.store = store;
        this.goalQueries = goalQueries;
        this.knowledgeQueries = knowledgeQueries;
        this.clock = clock;
    }

    @Transactional
    public SessionView startDiagnostic(long userId) {
        GoalQueries.ActiveGoalView goal = goalQueries.lockActiveGoalForUser(userId);
        Instant now = clock.instant();
        SessionRecord completed = store.findCompletedDiagnostic(goal.id()).orElse(null);
        if (completed != null) {
            return sessionView(completed, false);
        }

        SessionRecord active = store.findActiveDiagnosticForUpdate(goal.id()).orElse(null);
        if (active != null && !active.expiresAt().isAfter(now)) {
            store.expire(active.id(), active.version(), now);
            active = null;
        }
        if (active != null) {
            return sessionView(active, false);
        }

        completed = store.findCompletedDiagnostic(goal.id()).orElse(null);
        if (completed != null) {
            return sessionView(completed, false);
        }

        AssessmentKnowledgeQueries.AssessmentGraph graph =
                knowledgeQueries.publishedAssessmentGraph(goal.goalTemplateId());
        Set<Long> allowedNodeIds = graph.nodes().stream()
                .map(AssessmentKnowledgeQueries.NodeSummary::id)
                .collect(Collectors.toSet());
        List<ObjectiveQuestion> questions = store.activeObjectiveQuestions(graph.graphVersionId()).stream()
                .filter(question -> question.mappings().stream()
                        .allMatch(mapping -> allowedNodeIds.contains(mapping.knowledgeNodeId())))
                .sorted(Comparator.comparingLong(ObjectiveQuestion::versionId))
                .limit(DIAGNOSTIC_QUESTION_COUNT)
                .toList();
        if (questions.size() != DIAGNOSTIC_QUESTION_COUNT) {
            throw new ApiException(
                    HttpStatus.UNPROCESSABLE_ENTITY,
                    "DIAGNOSTIC_UNAVAILABLE_FOR_GRAPH_VERSION",
                    "A compatible diagnostic is not available for the published graph.");
        }

        AssessmentStore.StartRecord start = store.createDiagnosticIfAbsent(
                userId,
                goal.id(),
                graph.graphVersionId(),
                ObjectiveScoringPolicyV1.VERSION,
                now,
                now.plus(SESSION_DURATION),
                questions);
        return sessionView(start.session(), start.created());
    }

    @Transactional(noRollbackFor = AssessmentSessionExpiredException.class)
    public QuestionView nextQuestion(long userId, long sessionId) {
        return nextQuestion(userId, sessionId, SupportedLocale.ENGLISH);
    }

    @Transactional(noRollbackFor = AssessmentSessionExpiredException.class)
    public QuestionView nextQuestion(
            long userId, long sessionId, SupportedLocale locale) {
        SessionRecord session = ownedSessionForUpdate(sessionId, userId);
        ensureNotExpired(session);
        if ("COMPLETED".equals(session.status())) {
            return null;
        }
        SessionQuestionRecord next = store.nextQuestion(sessionId)
                .orElseThrow(() -> new ApiException(
                        HttpStatus.CONFLICT,
                        "ASSESSMENT_STATE_CONFLICT",
                        "The assessment has no available next question."));
        int total = store.totalQuestions(sessionId);
        AssessmentStore.QuestionPresentation presentation = presentation(next.question(), locale);
        return QuestionView.from(session, next, total, presentation);
    }

    @Transactional(noRollbackFor = AssessmentSessionExpiredException.class)
    public AttemptView submit(long userId, long sessionId, String idempotencyKey, SubmitAnswer command) {
        validateIdempotencyKey(idempotencyKey);
        validateCommand(command);
        String requestHash = hash(command);
        SessionRecord session = ownedSessionForUpdate(sessionId, userId);
        AttemptRecord existing = store.findAttemptByIdempotency(userId, sessionId, idempotencyKey).orElse(null);
        if (existing != null) {
            return replay(existing, requestHash, sessionId, userId);
        }
        ensureNotExpired(session);
        if (store.findAttemptByQuestion(sessionId, command.sessionQuestionId()).isPresent()) {
            throw new ApiException(
                    HttpStatus.CONFLICT, "QUESTION_ALREADY_ANSWERED", "The diagnostic question was already answered.");
        }
        if (!"IN_PROGRESS".equals(session.status())) {
            throw new ApiException(
                    HttpStatus.CONFLICT, "ASSESSMENT_ALREADY_COMPLETED", "The assessment is already completed.");
        }

        SessionQuestionRecord next = store.nextQuestion(sessionId)
                .orElseThrow(() -> new ApiException(
                        HttpStatus.CONFLICT, "ASSESSMENT_STATE_CONFLICT", "No question is available for submission."));
        if (next.id() != command.sessionQuestionId()) {
            throw new ApiException(
                    HttpStatus.CONFLICT,
                    "QUESTION_OUT_OF_ORDER",
                    "Only the current diagnostic question can be submitted.");
        }
        ObjectiveScoringPolicyV1.Evaluation evaluation;
        try {
            evaluation = scoringPolicy.evaluate(next.question(), command.selectedOptionIds());
        } catch (IllegalArgumentException exception) {
            throw new ApiException(HttpStatus.UNPROCESSABLE_ENTITY, "INVALID_ANSWER_SELECTION", exception.getMessage());
        }

        Instant now = clock.instant();
        AttemptRecord attempt = store.saveAttempt(
                session,
                next,
                userId,
                idempotencyKey,
                requestHash,
                command.selectedOptionIds(),
                command.selfConfidence(),
                command.timeSpentSeconds(),
                evaluation,
                now);
        int answered = store.answeredQuestions(sessionId);
        int total = store.totalQuestions(sessionId);
        boolean completed = answered == total;
        if (completed) {
            store.complete(sessionId, session.version(), now);
        }
        return new AttemptView(Long.toString(attempt.id()), completed ? "COMPLETED" : "IN_PROGRESS", false);
    }

    @Transactional(readOnly = true)
    public ResultView result(long userId, long sessionId) {
        return result(userId, sessionId, SupportedLocale.ENGLISH);
    }

    @Transactional(readOnly = true)
    public ResultView result(long userId, long sessionId, SupportedLocale locale) {
        SessionRecord session = store.findOwnedSession(sessionId, userId)
                .orElseThrow(this::sessionNotFound);
        if (!"COMPLETED".equals(session.status())) {
            throw new ApiException(
                    HttpStatus.CONFLICT, "ASSESSMENT_NOT_COMPLETED", "The assessment is not completed.");
        }
        ResultRecord result = store.result(sessionId);
        Set<Long> nodeIds = result.evidence().stream()
                .map(AssessmentStore.EvidenceRecord::knowledgeNodeId)
                .collect(Collectors.toSet());
        Map<Long, AssessmentKnowledgeQueries.NodeSummary> nodes = knowledgeQueries
                .nodeSummaries(session.graphVersionId(), nodeIds, locale)
                .stream()
                .collect(Collectors.toMap(AssessmentKnowledgeQueries.NodeSummary::id, Function.identity()));
        List<EvidenceView> evidence = result.evidence().stream()
                .map(item -> {
                    AssessmentKnowledgeQueries.NodeSummary node = nodes.get(item.knowledgeNodeId());
                    if (node == null) {
                        throw new IllegalStateException("Assessment evidence references a missing knowledge node");
                    }
                    return new EvidenceView(
                            Long.toString(item.id()),
                            Long.toString(item.attemptId()),
                            Long.toString(item.knowledgeNodeId()),
                            node.slug(),
                            node.name(),
                            item.dimension().name(),
                            item.score(),
                            item.reliability(),
                            item.evaluatorVersion(),
                            item.createdAt());
                })
                .toList();
        return new ResultView(
                Long.toString(session.id()),
                Long.toString(session.graphVersionId()),
                session.policyVersion(),
                session.startedAt(),
                session.completedAt(),
                result.answeredQuestions(),
                result.totalQuestions(),
                result.overallScore(),
                evidence,
                locale == SupportedLocale.VIETNAMESE
                        ? "Bằng chứng diagnostic mô tả điều các lần trả lời đã thể hiện; đây không phải mức độ thành thạo có thẩm quyền."
                        : "Diagnostic evidence describes what these attempts demonstrated; it is not authoritative mastery.");
    }

    private AssessmentStore.QuestionPresentation presentation(
            ObjectiveQuestion question, SupportedLocale locale) {
        if (!locale.requiresTranslation()) {
            return new AssessmentStore.QuestionPresentation(question.prompt(), question.options());
        }
        AssessmentStore.QuestionPresentation translated = store
                .findQuestionPresentation(question.versionId(), locale.tag())
                .orElse(null);
        if (translated == null || !sameOptionIds(question.options(), translated.options())) {
            return new AssessmentStore.QuestionPresentation(question.prompt(), question.options());
        }
        return translated;
    }

    private boolean sameOptionIds(
            List<com.skillpath.assessment.domain.QuestionOption> canonical,
            List<com.skillpath.assessment.domain.QuestionOption> translated) {
        if (canonical.size() != translated.size()) {
            return false;
        }
        Set<String> canonicalIds = canonical.stream()
                .map(com.skillpath.assessment.domain.QuestionOption::id)
                .collect(Collectors.toSet());
        Set<String> translatedIds = translated.stream()
                .map(com.skillpath.assessment.domain.QuestionOption::id)
                .collect(Collectors.toSet());
        return canonicalIds.size() == canonical.size()
                && translatedIds.size() == translated.size()
                && canonicalIds.equals(translatedIds);
    }

    private SessionRecord ownedSessionForUpdate(long sessionId, long userId) {
        return store.findOwnedSessionForUpdate(sessionId, userId).orElseThrow(this::sessionNotFound);
    }

    private void ensureNotExpired(SessionRecord session) {
        Instant now = clock.instant();
        if ("EXPIRED".equals(session.status())
                || ("IN_PROGRESS".equals(session.status()) && !session.expiresAt().isAfter(now))) {
            if ("IN_PROGRESS".equals(session.status())) {
                store.expire(session.id(), session.version(), now);
            }
            throw new AssessmentSessionExpiredException();
        }
    }

    private AttemptView replay(AttemptRecord attempt, String requestHash, long sessionId, long userId) {
        if (!attempt.requestHash().equals(requestHash)) {
            throw new ApiException(
                    HttpStatus.CONFLICT,
                    "IDEMPOTENCY_KEY_REUSED",
                    "Idempotency key was used with a different answer.");
        }
        SessionRecord session = store.findOwnedSession(sessionId, userId).orElseThrow(this::sessionNotFound);
        return new AttemptView(Long.toString(attempt.id()), session.status(), true);
    }

    private SessionView sessionView(SessionRecord session, boolean created) {
        return new SessionView(
                Long.toString(session.id()),
                Long.toString(session.goalId()),
                Long.toString(session.graphVersionId()),
                session.policyVersion(),
                session.status(),
                session.startedAt(),
                session.expiresAt(),
                session.completedAt(),
                store.answeredQuestions(session.id()),
                store.totalQuestions(session.id()),
                created,
                !created && "IN_PROGRESS".equals(session.status()));
    }

    private void validateIdempotencyKey(String key) {
        if (key == null || !IDEMPOTENCY_KEY.matcher(key).matches()) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "INVALID_IDEMPOTENCY_KEY", "Idempotency-Key is invalid.");
        }
    }

    private void validateCommand(SubmitAnswer command) {
        if (command == null || command.sessionQuestionId() <= 0) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "INVALID_SESSION_QUESTION_ID", "Session question ID is invalid.");
        }
        if (command.selectedOptionIds() == null || command.selectedOptionIds().isEmpty()
                || command.selectedOptionIds().size() > 20
                || command.selectedOptionIds().stream().anyMatch(id -> id == null || id.isBlank() || id.length() > 120)) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "INVALID_ANSWER_SELECTION", "Selected options are invalid.");
        }
        if (command.selfConfidence() != null
                && (command.selfConfidence().signum() < 0 || command.selfConfidence().compareTo(BigDecimal.ONE) > 0)) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "INVALID_SELF_CONFIDENCE", "Self-confidence must be in [0,1].");
        }
        if (command.timeSpentSeconds() < 0 || command.timeSpentSeconds() > 3600) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "INVALID_TIME_SPENT", "Time spent must be between 0 and 3600 seconds.");
        }
    }

    private String hash(SubmitAnswer command) {
        List<String> sorted = new ArrayList<>(command.selectedOptionIds());
        sorted.sort(String::compareTo);
        String confidence = command.selfConfidence() == null
                ? "null"
                : command.selfConfidence().stripTrailingZeros().toPlainString();
        String canonical = command.sessionQuestionId() + "|" + String.join(",", sorted) + "|"
                + confidence + "|" + command.timeSpentSeconds();
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(canonical.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is required by the Java platform", exception);
        }
    }

    private ApiException sessionNotFound() {
        return new ApiException(
                HttpStatus.NOT_FOUND, "ASSESSMENT_SESSION_NOT_FOUND", "Assessment session was not found.");
    }

    public record SubmitAnswer(
            long sessionQuestionId,
            List<String> selectedOptionIds,
            BigDecimal selfConfidence,
            int timeSpentSeconds) {}

    public record SessionView(
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
            boolean resumed) {}

    public record QuestionOptionView(String id, String label) {}

    public record QuestionView(
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
            List<QuestionOptionView> options) {
        static QuestionView from(
                SessionRecord session,
                SessionQuestionRecord item,
                int total,
                AssessmentStore.QuestionPresentation presentation) {
            ObjectiveQuestion question = item.question();
            return new QuestionView(
                    Long.toString(session.id()),
                    Long.toString(item.id()),
                    Long.toString(question.versionId()),
                    question.type().name(),
                    presentation.prompt(),
                    question.difficulty(),
                    question.estimatedSeconds(),
                    item.position(),
                    total,
                    session.expiresAt(),
                    presentation.options().stream()
                            .map(option -> new QuestionOptionView(option.id(), option.label()))
                            .toList());
        }
    }

    public record AttemptView(String attemptId, String sessionStatus, boolean replayed) {}

    public record EvidenceView(
            String evidenceId,
            String attemptId,
            String knowledgeNodeId,
            String knowledgeNodeSlug,
            String knowledgeNodeName,
            String dimension,
            BigDecimal score,
            BigDecimal reliability,
            String evaluatorVersion,
            Instant observedAt) {}

    public record ResultView(
            String sessionId,
            String graphVersionId,
            String assessmentPolicyVersion,
            Instant startedAt,
            Instant completedAt,
            int answeredQuestions,
            int totalQuestions,
            BigDecimal overallObjectiveScore,
            List<EvidenceView> evidence,
            String interpretation) {}
}
