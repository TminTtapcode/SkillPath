package com.skillpath.assessment.infrastructure.persistence;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.skillpath.assessment.application.AssessmentStore;
import com.skillpath.assessment.application.TaskCheckHistoryQueries;
import com.skillpath.assessment.domain.KnowledgeDimension;
import com.skillpath.assessment.domain.ObjectiveQuestion;
import com.skillpath.assessment.domain.ObjectiveScoringPolicyV1;
import com.skillpath.assessment.domain.QuestionMapping;
import com.skillpath.assessment.domain.QuestionOption;
import com.skillpath.assessment.domain.QuestionType;
import java.math.BigDecimal;
import java.sql.PreparedStatement;
import java.sql.Statement;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.stereotype.Component;

@Component
class JdbcAssessmentStore implements AssessmentStore {

    private static final String SESSION_COLUMNS = """
            id, user_id, goal_id, graph_version_id, assessment_policy_version,
            status, started_at, expires_at, completed_at, version
            """;

    private final JdbcTemplate jdbcTemplate;
    private final ObjectMapper objectMapper;

    JdbcAssessmentStore(JdbcTemplate jdbcTemplate, ObjectMapper objectMapper) {
        this.jdbcTemplate = jdbcTemplate;
        this.objectMapper = objectMapper;
    }

    @Override
    public Optional<SessionRecord> findCompletedDiagnostic(long goalId) {
        return oneSession(
                "SELECT " + SESSION_COLUMNS + " FROM assessment_sessions "
                        + "WHERE goal_id = ? AND purpose = 'DIAGNOSTIC' AND status = 'COMPLETED'",
                goalId);
    }

    @Override
    public Optional<SessionRecord> findActiveDiagnosticForUpdate(long goalId) {
        return oneSession(
                "SELECT " + SESSION_COLUMNS + " FROM assessment_sessions "
                        + "WHERE goal_id = ? AND purpose = 'DIAGNOSTIC' AND status = 'IN_PROGRESS' FOR UPDATE",
                goalId);
    }

    @Override
    public Optional<SessionRecord> findOwnedSession(long sessionId, long userId) {
        return oneSession(
                "SELECT " + SESSION_COLUMNS + " FROM assessment_sessions WHERE id = ? AND user_id = ?",
                sessionId,
                userId);
    }

    @Override
    public Optional<SessionRecord> findOwnedSessionForUpdate(long sessionId, long userId) {
        return oneSession(
                "SELECT " + SESSION_COLUMNS
                        + " FROM assessment_sessions WHERE id = ? AND user_id = ? FOR UPDATE",
                sessionId,
                userId);
    }

    private Optional<SessionRecord> oneSession(String sql, Object... arguments) {
        return jdbcTemplate.query(sql, (resultSet, rowNumber) -> new SessionRecord(
                        resultSet.getLong("id"),
                        resultSet.getLong("user_id"),
                        resultSet.getLong("goal_id"),
                        resultSet.getLong("graph_version_id"),
                        resultSet.getString("assessment_policy_version"),
                        resultSet.getString("status"),
                        resultSet.getTimestamp("started_at").toInstant(),
                        resultSet.getTimestamp("expires_at").toInstant(),
                        resultSet.getTimestamp("completed_at") == null
                                ? null
                                : resultSet.getTimestamp("completed_at").toInstant(),
                        resultSet.getLong("version")),
                arguments).stream().findFirst();
    }

    @Override
    public void expire(long sessionId, long expectedVersion, Instant now) {
        int changed = jdbcTemplate.update(
                """
                UPDATE assessment_sessions
                SET status = 'EXPIRED', version = version + 1, updated_at = ?
                WHERE id = ? AND status = 'IN_PROGRESS' AND version = ?
                """,
                Timestamp.from(now),
                sessionId,
                expectedVersion);
        if (changed != 1) {
            throw new IllegalStateException("Assessment session changed during expiry");
        }
    }

    @Override
    public List<ObjectiveQuestion> activeObjectiveQuestions(long graphVersionId) {
        List<Long> ids = jdbcTemplate.queryForList(
                """
                SELECT DISTINCT qv.id
                FROM question_versions qv
                JOIN question_knowledge qk ON qk.question_version_id = qv.id
                WHERE qv.status = 'ACTIVE'
                  AND qv.scoring_strategy = 'EXACT'
                  AND qv.type IN ('SINGLE_CHOICE', 'MULTIPLE_CHOICE')
                  AND qk.graph_version_id = ?
                ORDER BY qv.id
                """,
                Long.class,
                graphVersionId);
        return ids.stream().map(this::loadQuestion).toList();
    }

    @Override
    public StartRecord createDiagnosticIfAbsent(
            long userId,
            long goalId,
            long graphVersionId,
            String policyVersion,
            Instant startedAt,
            Instant expiresAt,
            List<ObjectiveQuestion> questions) {
        KeyHolder keyHolder = new GeneratedKeyHolder();
        try {
            int inserted = jdbcTemplate.update(connection -> {
                PreparedStatement statement = connection.prepareStatement(
                        """
                        INSERT INTO assessment_sessions (
                            user_id, goal_id, purpose, status, graph_version_id,
                            assessment_policy_version, started_at, expires_at, completed_at,
                            version, created_at, updated_at)
                        VALUES (?, ?, 'DIAGNOSTIC', 'IN_PROGRESS', ?, ?, ?, ?, NULL, 0, ?, ?)
                        """,
                        Statement.RETURN_GENERATED_KEYS);
                statement.setLong(1, userId);
                statement.setLong(2, goalId);
                statement.setLong(3, graphVersionId);
                statement.setString(4, policyVersion);
                statement.setTimestamp(5, Timestamp.from(startedAt));
                statement.setTimestamp(6, Timestamp.from(expiresAt));
                statement.setTimestamp(7, Timestamp.from(startedAt));
                statement.setTimestamp(8, Timestamp.from(startedAt));
                return statement;
            }, keyHolder);
            if (inserted != 1 || keyHolder.getKey() == null) {
                throw new IllegalStateException("Assessment session was not created");
            }
            long sessionId = keyHolder.getKey().longValue();
            for (int index = 0; index < questions.size(); index++) {
                jdbcTemplate.update(
                        """
                        INSERT INTO assessment_session_questions
                            (session_id, question_version_id, position, created_at)
                        VALUES (?, ?, ?, ?)
                        """,
                        sessionId,
                        questions.get(index).versionId(),
                        index + 1,
                        Timestamp.from(startedAt));
            }
            SessionRecord session = findOwnedSession(sessionId, userId).orElseThrow();
            return new StartRecord(session, true);
        } catch (DuplicateKeyException exception) {
            SessionRecord existing = findActiveDiagnosticForUpdate(goalId)
                    .or(() -> findCompletedDiagnostic(goalId))
                    .orElseThrow(() -> new IllegalStateException(
                            "Diagnostic uniqueness conflict has no existing session", exception));
            return new StartRecord(existing, false);
        }
    }

    @Override
    public int totalQuestions(long sessionId) {
        Integer count = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM assessment_session_questions WHERE session_id = ?",
                Integer.class,
                sessionId);
        return count == null ? 0 : count;
    }

    @Override
    public int answeredQuestions(long sessionId) {
        Integer count = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM answer_attempts WHERE session_id = ? AND evaluation_status = 'EVALUATED'",
                Integer.class,
                sessionId);
        return count == null ? 0 : count;
    }

    @Override
    public Optional<SessionQuestionRecord> nextQuestion(long sessionId) {
        return jdbcTemplate.query(
                """
                SELECT sq.id, sq.session_id, sq.position, sq.question_version_id
                FROM assessment_session_questions sq
                WHERE sq.session_id = ?
                  AND NOT EXISTS (
                    SELECT 1 FROM answer_attempts aa
                    WHERE aa.session_id = sq.session_id
                      AND aa.session_question_id = sq.id
                      AND aa.evaluation_status = 'EVALUATED'
                  )
                ORDER BY sq.position
                LIMIT 1
                """,
                (resultSet, rowNumber) -> new SessionQuestionRecord(
                        resultSet.getLong("id"),
                        resultSet.getLong("session_id"),
                        resultSet.getInt("position"),
                        loadQuestion(resultSet.getLong("question_version_id"))),
                sessionId).stream().findFirst();
    }

    @Override
    public Optional<QuestionPresentation> findQuestionPresentation(
            long questionVersionId, String locale) {
        return jdbcTemplate.query(
                        """
                        SELECT prompt, CAST(options AS CHAR) options
                        FROM question_version_translations
                        WHERE question_version_id = ? AND locale = ?
                        """,
                        (resultSet, rowNumber) -> new QuestionPresentation(
                                resultSet.getString("prompt"),
                                parseOptions(resultSet.getString("options"))),
                        questionVersionId,
                        locale)
                .stream()
                .findFirst();
    }

    @Override
    public Optional<AttemptRecord> findAttemptByIdempotency(
            long userId, long sessionId, String key) {
        return oneAttempt(
                """
                SELECT aa.id, aa.session_id, aa.session_question_id, aa.request_hash, aa.raw_score,
                       (SELECT COUNT(*) FROM attempt_evidence ae WHERE ae.attempt_id = aa.id) evidence_count
                FROM answer_attempts aa
                WHERE aa.user_id = ? AND aa.session_id = ? AND aa.idempotency_key = ?
                """,
                userId,
                sessionId,
                key);
    }

    @Override
    public Optional<AttemptRecord> findAttemptByQuestion(long sessionId, long sessionQuestionId) {
        return oneAttempt(
                """
                SELECT aa.id, aa.session_id, aa.session_question_id, aa.request_hash, aa.raw_score,
                       (SELECT COUNT(*) FROM attempt_evidence ae WHERE ae.attempt_id = aa.id) evidence_count
                FROM answer_attempts aa
                WHERE aa.session_id = ? AND aa.session_question_id = ?
                """,
                sessionId,
                sessionQuestionId);
    }

    private Optional<AttemptRecord> oneAttempt(String sql, Object... arguments) {
        return jdbcTemplate.query(sql, (resultSet, rowNumber) -> new AttemptRecord(
                        resultSet.getLong("id"),
                        resultSet.getLong("session_id"),
                        resultSet.getLong("session_question_id"),
                        resultSet.getString("request_hash"),
                        resultSet.getBigDecimal("raw_score"),
                        resultSet.getInt("evidence_count")),
                arguments).stream().findFirst();
    }

    @Override
    public AttemptRecord saveAttempt(
            SessionRecord session,
            SessionQuestionRecord sessionQuestion,
            long userId,
            String idempotencyKey,
            String requestHash,
            List<String> selectedOptionIds,
            BigDecimal selfConfidence,
            int timeSpentSeconds,
            ObjectiveScoringPolicyV1.Evaluation evaluation,
            Instant submittedAt) {
        long attemptId = insertAttempt(
                session,
                sessionQuestion,
                userId,
                idempotencyKey,
                requestHash,
                selectedOptionIds,
                selfConfidence,
                timeSpentSeconds,
                evaluation.score(),
                submittedAt, ObjectiveScoringPolicyV1.VERSION);
        for (ObjectiveScoringPolicyV1.Evidence evidence : evaluation.evidence()) {
            long evidenceId = insertEvidence(attemptId, session.graphVersionId(), evidence, submittedAt,
                    ObjectiveScoringPolicyV1.VERSION);
            insertOutbox(session, attemptId, evidenceId, evidence, submittedAt,"DIAGNOSTIC",
                    evaluation.evidence().size(),false,ObjectiveScoringPolicyV1.VERSION);
        }
        return findAttemptByIdempotency(userId, session.id(), idempotencyKey).orElseThrow();
    }

    private long insertAttempt(
            SessionRecord session,
            SessionQuestionRecord sessionQuestion,
            long userId,
            String idempotencyKey,
            String requestHash,
            List<String> selectedOptionIds,
            BigDecimal selfConfidence,
            int timeSpentSeconds,
            BigDecimal rawScore,
            Instant submittedAt, String evaluatorVersion) {
        KeyHolder keys = new GeneratedKeyHolder();
        String payload = writeJson(Map.of("selectedOptionIds", selectedOptionIds));
        jdbcTemplate.update(connection -> {
            PreparedStatement statement = connection.prepareStatement(
                    """
                    INSERT INTO answer_attempts (
                        session_id, session_question_id, user_id, idempotency_key,
                        request_hash, answer_payload, raw_score, self_confidence,
                        time_spent_seconds, evaluation_status, evaluator_version, submitted_at)
                    VALUES (?, ?, ?, ?, ?, CAST(? AS JSON), ?, ?, ?, 'EVALUATED', ?, ?)
                    """,
                    Statement.RETURN_GENERATED_KEYS);
            statement.setLong(1, session.id());
            statement.setLong(2, sessionQuestion.id());
            statement.setLong(3, userId);
            statement.setString(4, idempotencyKey);
            statement.setString(5, requestHash);
            statement.setString(6, payload);
            statement.setBigDecimal(7, rawScore);
            statement.setBigDecimal(8, selfConfidence);
            statement.setInt(9, timeSpentSeconds);
            statement.setString(10, evaluatorVersion);
            statement.setTimestamp(11, Timestamp.from(submittedAt));
            return statement;
        }, keys);
        if (keys.getKey() == null) {
            throw new IllegalStateException("Answer attempt was not created");
        }
        return keys.getKey().longValue();
    }

    private long insertEvidence(
            long attemptId,
            long graphVersionId,
            ObjectiveScoringPolicyV1.Evidence evidence,
            Instant createdAt, String evaluatorVersion) {
        KeyHolder keys = new GeneratedKeyHolder();
        jdbcTemplate.update(connection -> {
            PreparedStatement statement = connection.prepareStatement(
                    """
                    INSERT INTO attempt_evidence (
                        attempt_id, graph_version_id, knowledge_node_id, dimension,
                        score, reliability, evaluator_type, evaluator_version, rationale,
                        misconception_codes, created_at)
                    VALUES (?, ?, ?, ?, ?, ?, 'DETERMINISTIC', ?, ?, JSON_ARRAY(), ?)
                    """,
                    Statement.RETURN_GENERATED_KEYS);
            statement.setLong(1, attemptId);
            statement.setLong(2, graphVersionId);
            statement.setLong(3, evidence.knowledgeNodeId());
            statement.setString(4, evidence.dimension().name());
            statement.setBigDecimal(5, evidence.score());
            statement.setBigDecimal(6, evidence.reliability());
            statement.setString(7, evaluatorVersion);
            statement.setString(8, "Objective answer evaluated by versioned deterministic policy.");
            statement.setTimestamp(9, Timestamp.from(createdAt));
            return statement;
        }, keys);
        if (keys.getKey() == null) {
            throw new IllegalStateException("Attempt evidence was not created");
        }
        return keys.getKey().longValue();
    }

    private void insertOutbox(
            SessionRecord session,
            long attemptId,
            long evidenceId,
            ObjectiveScoringPolicyV1.Evidence evidence,
            Instant occurredAt, String attemptKind, int expectedEvidenceCount,
            boolean replanEligible, String evaluatorVersion) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("evidenceId", Long.toString(evidenceId));
        payload.put("attemptId", Long.toString(attemptId));
        payload.put("userId", Long.toString(session.userId()));
        payload.put("goalId", Long.toString(session.goalId()));
        payload.put("graphVersionId", Long.toString(session.graphVersionId()));
        payload.put("knowledgeNodeId", Long.toString(evidence.knowledgeNodeId()));
        payload.put("dimension", evidence.dimension().name());
        payload.put("score", evidence.score());
        payload.put("reliability", evidence.reliability());
        payload.put("evaluatorVersion", evaluatorVersion);
        payload.put("evaluatorType", "DETERMINISTIC");
        payload.put("assessmentPurpose", attemptKind.equals("DIAGNOSTIC") ? "DIAGNOSTIC" : "PRACTICE");
        payload.put("attemptKind",attemptKind);
        payload.put("expectedEvidenceCount",expectedEvidenceCount);
        payload.put("replanEligible",replanEligible);
        payload.put("misconceptionCodes", List.of());
        payload.put("policyVersion", session.policyVersion());
        payload.put("observedAt", occurredAt.toString());
        jdbcTemplate.update(
                """
                INSERT INTO outbox_events (
                    event_key, owner_module, aggregate_type, aggregate_id, event_type,event_version,
                    payload, status, attempt_count, available_at, occurred_at, published_at,
                    created_at, updated_at)
                VALUES (?, 'assessment', 'AttemptEvidence', ?, 'AssessmentEvidenceCreated',?,
                        CAST(? AS JSON), 'PENDING', 0, ?, ?, NULL, ?, ?)
                """,
                "assessment-evidence:" + evidenceId,
                Long.toString(evidenceId),
                attemptKind.equals("TASK_CHECK") ? 2 : 1,
                writeJson(payload),
                Timestamp.from(occurredAt),
                Timestamp.from(occurredAt),
                Timestamp.from(occurredAt),
                Timestamp.from(occurredAt));
    }

    @Override
    public void complete(long sessionId, long expectedVersion, Instant completedAt) {
        int changed = jdbcTemplate.update(
                """
                UPDATE assessment_sessions
                SET status = 'COMPLETED', completed_at = ?, version = version + 1, updated_at = ?
                WHERE id = ? AND status = 'IN_PROGRESS' AND version = ?
                """,
                Timestamp.from(completedAt),
                Timestamp.from(completedAt),
                sessionId,
                expectedVersion);
        if (changed != 1) {
            throw new IllegalStateException("Assessment session changed during completion");
        }
    }

    @Override
    public ResultRecord result(long sessionId) {
        int total = totalQuestions(sessionId);
        int answered = answeredQuestions(sessionId);
        BigDecimal overall = jdbcTemplate.queryForObject(
                "SELECT COALESCE(AVG(raw_score), 0) FROM answer_attempts WHERE session_id = ? AND evaluation_status = 'EVALUATED'",
                BigDecimal.class,
                sessionId);
        List<EvidenceRecord> evidence = jdbcTemplate.query(
                """
                SELECT ae.id, ae.attempt_id, ae.knowledge_node_id, ae.dimension,
                       ae.score, ae.reliability, ae.evaluator_version, ae.created_at
                FROM attempt_evidence ae
                JOIN answer_attempts aa ON aa.id = ae.attempt_id
                WHERE aa.session_id = ?
                ORDER BY ae.created_at, ae.id
                """,
                (resultSet, rowNumber) -> new EvidenceRecord(
                        resultSet.getLong("id"),
                        resultSet.getLong("attempt_id"),
                        resultSet.getLong("knowledge_node_id"),
                        KnowledgeDimension.valueOf(resultSet.getString("dimension")),
                        resultSet.getBigDecimal("score"),
                        resultSet.getBigDecimal("reliability"),
                        resultSet.getString("evaluator_version"),
                        resultSet.getTimestamp("created_at").toInstant()),
                sessionId);
        return new ResultRecord(answered, total, overall == null ? BigDecimal.ZERO : overall, evidence);
    }

    @Override
    public Optional<TaskCheckDefinition> taskCheck(long templateVersionId, long graphVersionId) {
        return jdbcTemplate.query("""
                SELECT d.task_template_version_id,d.graph_version_id,d.question_version_id,d.evaluator_version
                FROM task_check_definitions d
                JOIN question_versions q ON q.id=d.question_version_id
                WHERE d.task_template_version_id=? AND d.graph_version_id=?
                  AND q.scoring_strategy='EXACT'
                  AND q.type IN ('SINGLE_CHOICE','MULTIPLE_CHOICE')
                  AND EXISTS (SELECT 1 FROM question_knowledge m
                              WHERE m.question_version_id=d.question_version_id
                                AND m.graph_version_id=d.graph_version_id
                                AND m.dimension IN ('RECOGNITION','UNDERSTANDING'))
                  AND NOT EXISTS (SELECT 1 FROM question_knowledge m
                                  WHERE m.question_version_id=d.question_version_id
                                    AND (m.graph_version_id<>d.graph_version_id
                                         OR m.dimension NOT IN ('RECOGNITION','UNDERSTANDING')))
                """, (rs, row) -> new TaskCheckDefinition(rs.getLong(1),rs.getLong(2),rs.getString(4),
                        loadQuestion(rs.getLong(3))), templateVersionId, graphVersionId).stream().findFirst();
    }

    @Override
    public Optional<TaskCheckSubmission> taskCheckSubmission(long taskId, long userId) {
        return jdbcTemplate.query("""
                SELECT aa.id,aa.idempotency_key,aa.request_hash,aa.raw_score
                FROM task_check_submissions s
                JOIN answer_attempts aa ON aa.id=s.answer_attempt_id
                WHERE s.learning_task_id=? AND s.user_id=?
                """, (rs,row)->new TaskCheckSubmission(rs.getLong(1),rs.getString(2),
                        rs.getString(3),rs.getBigDecimal(4)),taskId,userId).stream().findFirst();
    }

    @Override
    public List<TaskCheckHistoryQueries.FailureObservation> recentTaskChecks(long userId,long goalId,
                                                                              long graphVersionId,Instant asOf) {
        return jdbcTemplate.query("""
                SELECT s.answer_attempt_id,s.task_template_version_id,aa.raw_score
                FROM task_check_submissions s
                JOIN answer_attempts aa ON aa.id=s.answer_attempt_id
                WHERE s.user_id=? AND s.goal_id=? AND s.graph_version_id=? AND s.submitted_at<=?
                ORDER BY s.submitted_at DESC,s.answer_attempt_id DESC LIMIT 2000
                """,(rs,row)->new TaskCheckHistoryQueries.FailureObservation(rs.getLong(1),
                        rs.getLong(2),rs.getBigDecimal(3)),userId,goalId,graphVersionId,Timestamp.from(asOf));
    }

    @Override
    public TaskCheckSubmission saveTaskCheck(long taskId,long userId,long goalId,
                                             TaskCheckDefinition definition,String idempotencyKey,
                                             String requestHash,List<String> selectedOptionIds,
                                             int timeSpentSeconds,ObjectiveScoringPolicyV1.Evaluation evaluation,
                                             Instant now) {
        long sessionId = insertGenerated("""
                INSERT INTO assessment_sessions(user_id,goal_id,purpose,status,graph_version_id,
                    assessment_policy_version,started_at,expires_at,completed_at,version,created_at,updated_at)
                VALUES(?,?,'PRACTICE','COMPLETED',?,?,?, ?,?,0,?,?)
                """,userId,goalId,definition.graphVersionId(),definition.evaluatorVersion(),
                Timestamp.from(now),Timestamp.from(now.plusSeconds(7*86400L)),Timestamp.from(now),
                Timestamp.from(now),Timestamp.from(now));
        long sessionQuestionId=insertGenerated("""
                INSERT INTO assessment_session_questions(session_id,question_version_id,position,created_at)
                VALUES(?,?,1,?)
                """,sessionId,definition.question().versionId(),Timestamp.from(now));
        SessionRecord session=findOwnedSession(sessionId,userId).orElseThrow();
        SessionQuestionRecord sessionQuestion=new SessionQuestionRecord(sessionQuestionId,sessionId,1,definition.question());
        long attemptId=insertAttempt(session,sessionQuestion,userId,idempotencyKey,requestHash,
                selectedOptionIds,null,timeSpentSeconds,evaluation.score(),now,
                definition.evaluatorVersion());
        for(ObjectiveScoringPolicyV1.Evidence evidence:evaluation.evidence()){
            long evidenceId=insertEvidence(attemptId,definition.graphVersionId(),evidence,now,
                    definition.evaluatorVersion());
            insertOutbox(session,attemptId,evidenceId,evidence,now,"TASK_CHECK",evaluation.evidence().size(),
                    true,definition.evaluatorVersion());
        }
        jdbcTemplate.update("""
                INSERT INTO task_check_submissions(learning_task_id,user_id,goal_id,graph_version_id,
                    assessment_session_id,answer_attempt_id,task_template_version_id,submitted_at)
                VALUES(?,?,?,?,?,?,?,?)
                """,taskId,userId,goalId,definition.graphVersionId(),sessionId,attemptId,
                definition.templateVersionId(),Timestamp.from(now));
        return new TaskCheckSubmission(attemptId,idempotencyKey,requestHash,evaluation.score());
    }

    private long insertGenerated(String sql,Object... values){
        KeyHolder keys=new GeneratedKeyHolder();
        jdbcTemplate.update(connection->{
            PreparedStatement statement=connection.prepareStatement(sql,Statement.RETURN_GENERATED_KEYS);
            for(int i=0;i<values.length;i++)statement.setObject(i+1,values[i]);
            return statement;
        },keys);
        if(keys.getKey()==null)throw new IllegalStateException("Assessment row was not created");
        return keys.getKey().longValue();
    }

    private ObjectiveQuestion loadQuestion(long versionId) {
        QuestionRow row = jdbcTemplate.queryForObject(
                """
                SELECT id, type, prompt, difficulty, estimated_seconds, options, answer_key
                FROM question_versions WHERE id = ?
                """,
                (resultSet, rowNumber) -> new QuestionRow(
                        resultSet.getLong("id"),
                        resultSet.getString("type"),
                        resultSet.getString("prompt"),
                        resultSet.getInt("difficulty"),
                        resultSet.getInt("estimated_seconds"),
                        resultSet.getString("options"),
                        resultSet.getString("answer_key")),
                versionId);
        if (row == null) {
            throw new IllegalStateException("Question version was not found");
        }
        List<QuestionOption> options = parseOptions(row.options());
        Set<String> correct = parseCorrectOptions(row.answerKey());
        List<QuestionMapping> mappings = jdbcTemplate.query(
                """
                SELECT knowledge_node_id, dimension, weight, max_evidence_strength
                FROM question_knowledge
                WHERE question_version_id = ?
                ORDER BY knowledge_node_id, dimension
                """,
                (resultSet, rowNumber) -> new QuestionMapping(
                        resultSet.getLong("knowledge_node_id"),
                        KnowledgeDimension.valueOf(resultSet.getString("dimension")),
                        resultSet.getBigDecimal("weight"),
                        resultSet.getBigDecimal("max_evidence_strength")),
                versionId);
        return new ObjectiveQuestion(
                row.id(),
                QuestionType.valueOf(row.type()),
                row.prompt(),
                row.difficulty(),
                row.estimatedSeconds(),
                options,
                correct,
                mappings);
    }

    private List<QuestionOption> parseOptions(String json) {
        try {
            List<QuestionOption> options = new ArrayList<>();
            for (JsonNode node : objectMapper.readTree(json)) {
                options.add(new QuestionOption(node.path("id").asText(), node.path("label").asText()));
            }
            return options;
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("Stored question options are invalid", exception);
        }
    }

    private Set<String> parseCorrectOptions(String json) {
        try {
            JsonNode array = objectMapper.readTree(json).path("correctOptionIds");
            if (!array.isArray()) {
                throw new IllegalStateException("Stored answer key is invalid");
            }
            List<String> ids = new ArrayList<>();
            array.forEach(node -> ids.add(node.asText()));
            return Set.copyOf(ids);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("Stored answer key is invalid", exception);
        }
    }

    private String writeJson(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("Assessment JSON could not be serialized", exception);
        }
    }

    private record QuestionRow(
            long id,
            String type,
            String prompt,
            int difficulty,
            int estimatedSeconds,
            String options,
            String answerKey) {}
}
