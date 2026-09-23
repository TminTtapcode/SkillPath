package com.skillpath.assessment.application;

import com.skillpath.assessment.domain.KnowledgeDimension;
import com.skillpath.assessment.domain.ObjectiveQuestion;
import com.skillpath.assessment.domain.ObjectiveScoringPolicyV1;
import com.skillpath.assessment.domain.QuestionOption;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

public interface AssessmentStore {

    Optional<SessionRecord> findCompletedDiagnostic(long goalId);

    Optional<SessionRecord> findActiveDiagnosticForUpdate(long goalId);

    Optional<SessionRecord> findOwnedSession(long sessionId, long userId);

    Optional<SessionRecord> findOwnedSessionForUpdate(long sessionId, long userId);

    void expire(long sessionId, long expectedVersion, Instant now);

    List<ObjectiveQuestion> activeObjectiveQuestions(long graphVersionId);

    StartRecord createDiagnosticIfAbsent(
            long userId,
            long goalId,
            long graphVersionId,
            String policyVersion,
            Instant startedAt,
            Instant expiresAt,
            List<ObjectiveQuestion> questions);

    int totalQuestions(long sessionId);

    int answeredQuestions(long sessionId);

    Optional<SessionQuestionRecord> nextQuestion(long sessionId);

    Optional<QuestionPresentation> findQuestionPresentation(long questionVersionId, String locale);

    Optional<AttemptRecord> findAttemptByIdempotency(long userId, long sessionId, String key);

    Optional<AttemptRecord> findAttemptByQuestion(long sessionId, long sessionQuestionId);

    AttemptRecord saveAttempt(
            SessionRecord session,
            SessionQuestionRecord sessionQuestion,
            long userId,
            String idempotencyKey,
            String requestHash,
            List<String> selectedOptionIds,
            BigDecimal selfConfidence,
            int timeSpentSeconds,
            ObjectiveScoringPolicyV1.Evaluation evaluation,
            Instant submittedAt);

    void complete(long sessionId, long expectedVersion, Instant completedAt);

    ResultRecord result(long sessionId);

    Optional<TaskCheckDefinition> taskCheck(long templateVersionId, long graphVersionId);

    Optional<TaskCheckSubmission> taskCheckSubmission(long taskId, long userId);

    TaskCheckSubmission saveTaskCheck(long taskId, long userId, long goalId,
                                      TaskCheckDefinition definition, String idempotencyKey,
                                      String requestHash, List<String> selectedOptionIds,
                                      int timeSpentSeconds, ObjectiveScoringPolicyV1.Evaluation evaluation,
                                      Instant submittedAt);

    List<TaskCheckHistoryQueries.FailureObservation> recentTaskChecks(long userId,long goalId,
                                                                      long graphVersionId,Instant asOf);

    record TaskCheckDefinition(long templateVersionId, long graphVersionId, String evaluatorVersion,
                               ObjectiveQuestion question) {}

    record TaskCheckSubmission(long attemptId, String idempotencyKey, String requestHash,
                               BigDecimal score) {}

    record SessionRecord(
            long id,
            long userId,
            long goalId,
            long graphVersionId,
            String policyVersion,
            String status,
            Instant startedAt,
            Instant expiresAt,
            Instant completedAt,
            long version) {}

    record SessionQuestionRecord(long id, long sessionId, int position, ObjectiveQuestion question) {}

    record QuestionPresentation(String prompt, List<QuestionOption> options) {}

    record AttemptRecord(
            long id,
            long sessionId,
            long sessionQuestionId,
            String requestHash,
            BigDecimal rawScore,
            int evidenceCount) {}

    record EvidenceRecord(
            long id,
            long attemptId,
            long knowledgeNodeId,
            KnowledgeDimension dimension,
            BigDecimal score,
            BigDecimal reliability,
            String evaluatorVersion,
            Instant createdAt) {}

    record StartRecord(SessionRecord session, boolean created) {}

    record ResultRecord(
            int answeredQuestions,
            int totalQuestions,
            BigDecimal overallScore,
            List<EvidenceRecord> evidence) {}
}
