package com.skillpath;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.jayway.jsonpath.JsonPath;
import com.skillpath.assessment.application.AssessmentService;
import com.skillpath.progress.application.AssessmentEvidenceHandler;
import com.skillpath.shared.application.OutboxHandler.OutboxEvent;
import jakarta.servlet.http.Cookie;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.http.HttpHeaders;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

@SpringBootTest(properties = {"debug=false", "skillpath.outbox.enabled=false"})
@AutoConfigureMockMvc
@Testcontainers
class PhaseThreeFlowIT {

    @Container
    static final MySQLContainer<?> MYSQL = new MySQLContainer<>("mysql:8.4")
            .withDatabaseName("skillpath")
            .withUsername("skillpath")
            .withPassword("integration-only");

    @DynamicPropertySource
    static void databaseProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", MYSQL::getJdbcUrl);
        registry.add("spring.datasource.username", MYSQL::getUsername);
        registry.add("spring.datasource.password", MYSQL::getPassword);
    }

    @Autowired
    MockMvc mockMvc;

    @Autowired
    JdbcTemplate jdbcTemplate;

    @Autowired
    ObjectMapper objectMapper;

    @Autowired
    AssessmentService assessmentService;

    @Autowired
    AssessmentEvidenceHandler assessmentEvidenceHandler;

    @Test
    void diagnosticCreatesEvidenceExactlyOnceAndConcealsAnswersAndOwnership() throws Exception {
        mockMvc.perform(post("/api/v1/assessments/diagnostic").with(csrf()))
                .andExpect(status().isUnauthorized());

        Cookie learner = register("phase3-flow@skillpath.local");
        createGoal(learner, "phase3-goal-flow");

        mockMvc.perform(post("/api/v1/assessments/diagnostic").cookie(learner))
                .andExpect(status().isForbidden());

        MvcResult started = mockMvc.perform(post("/api/v1/assessments/diagnostic")
                        .cookie(learner)
                        .with(csrf()))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("IN_PROGRESS"))
                .andExpect(jsonPath("$.totalQuestions").value(8))
                .andExpect(jsonPath("$.created").value(true))
                .andReturn();
        String sessionId = JsonPath.read(started.getResponse().getContentAsString(), "$.id");

        mockMvc.perform(post("/api/v1/assessments/diagnostic").cookie(learner).with(csrf()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(sessionId))
                .andExpect(jsonPath("$.resumed").value(true));

        MvcResult firstQuestion = mockMvc.perform(get(
                                "/api/v1/assessments/{sessionId}/next-question", sessionId)
                        .cookie(learner))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.position").value(1))
                .andExpect(jsonPath("$.answerKey").doesNotExist())
                .andExpect(jsonPath("$.correctOptionIds").doesNotExist())
                .andReturn();
        String firstBody = firstQuestion.getResponse().getContentAsString();
        assertThat(firstBody).doesNotContain("correctOptionIds", "answer_key", "answerKey");

        mockMvc.perform(get("/api/v1/assessments/{sessionId}/result", sessionId).cookie(learner))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("ASSESSMENT_NOT_COMPLETED"));

        Long secondQuestionId = jdbcTemplate.queryForObject(
                "SELECT id FROM assessment_session_questions WHERE session_id = ? AND position = 2",
                Long.class,
                Long.parseLong(sessionId));
        mockMvc.perform(post("/api/v1/assessments/{sessionId}/attempts", sessionId)
                        .cookie(learner)
                        .with(csrf())
                        .header("Idempotency-Key", "phase3-out-of-order")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(answerBody(secondQuestionId.toString(), List.of("not-evaluated"), 10)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("QUESTION_OUT_OF_ORDER"));

        QuestionInput first = questionInput(firstBody);
        String firstAnswer = answerBody(first.sessionQuestionId(), first.correctOptionIds(), 30);
        mockMvc.perform(post("/api/v1/assessments/{sessionId}/attempts", sessionId)
                        .cookie(learner)
                        .with(csrf())
                        .header("Idempotency-Key", "phase3-attempt-1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(firstAnswer))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.replayed").value(false));

        OutboxEvent evidenceEvent = jdbcTemplate.queryForObject(
                "SELECT id,event_key,owner_module,event_type,event_version,aggregate_id,payload,occurred_at FROM outbox_events WHERE event_type='AssessmentEvidenceCreated' ORDER BY id DESC LIMIT 1",
                (resultSet, rowNumber) -> new OutboxEvent(
                        resultSet.getLong("id"), resultSet.getString("event_key"),
                        resultSet.getString("owner_module"), resultSet.getString("event_type"),
                        resultSet.getInt("event_version"), resultSet.getString("aggregate_id"),
                        resultSet.getString("payload"), resultSet.getTimestamp("occurred_at").toInstant()));
        assessmentEvidenceHandler.handle(evidenceEvent);
        assessmentEvidenceHandler.handle(evidenceEvent);
        assertThat(jdbcTemplate.queryForObject(
                        "SELECT COUNT(*) FROM knowledge_evidence WHERE source_event_id=?", Integer.class, evidenceEvent.id()))
                .isEqualTo(1);
        assertThat(jdbcTemplate.queryForObject("SELECT COUNT(*) FROM user_knowledge", Integer.class)).isEqualTo(1);
        mockMvc.perform(get("/api/v1/knowledge/me").cookie(learner).header(HttpHeaders.ACCEPT_LANGUAGE, "vi-VN"))
                .andExpect(status().isOk())
                .andExpect(header().string(HttpHeaders.CONTENT_LANGUAGE, "vi-VN"))
                .andExpect(jsonPath("$.items[0].evidenceCount").value(1))
                .andExpect(jsonPath("$.items[0].policyVersion").value("knowledge-state-v1"));

        mockMvc.perform(post("/api/v1/assessments/{sessionId}/attempts", sessionId)
                        .cookie(learner)
                        .with(csrf())
                        .header("Idempotency-Key", "phase3-attempt-1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(firstAnswer))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.replayed").value(true));

        mockMvc.perform(post("/api/v1/assessments/{sessionId}/attempts", sessionId)
                        .cookie(learner)
                        .with(csrf())
                        .header("Idempotency-Key", "phase3-attempt-1-different-key")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(firstAnswer))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("QUESTION_ALREADY_ANSWERED"));

        mockMvc.perform(post("/api/v1/assessments/{sessionId}/attempts", sessionId)
                        .cookie(learner)
                        .with(csrf())
                        .header("Idempotency-Key", "phase3-attempt-1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(answerBody(first.sessionQuestionId(), first.correctOptionIds(), 31)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("IDEMPOTENCY_KEY_REUSED"));

        for (int position = 2; position <= 8; position++) {
            MvcResult questionResult = mockMvc.perform(get(
                                    "/api/v1/assessments/{sessionId}/next-question", sessionId)
                            .cookie(learner))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.position").value(position))
                    .andReturn();
            QuestionInput question = questionInput(questionResult.getResponse().getContentAsString());
            mockMvc.perform(post("/api/v1/assessments/{sessionId}/attempts", sessionId)
                            .cookie(learner)
                            .with(csrf())
                            .header("Idempotency-Key", "phase3-attempt-" + position)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(answerBody(question.sessionQuestionId(), question.correctOptionIds(), 30)))
                    .andExpect(status().isCreated())
                    .andExpect(jsonPath("$.sessionStatus")
                            .value(position == 8 ? "COMPLETED" : "IN_PROGRESS"));
        }

        mockMvc.perform(get("/api/v1/assessments/{sessionId}/next-question", sessionId).cookie(learner))
                .andExpect(status().isNoContent());
        mockMvc.perform(get("/api/v1/assessments/{sessionId}/result", sessionId).cookie(learner))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.answeredQuestions").value(8))
                .andExpect(jsonPath("$.evidence.length()").value(8))
                .andExpect(jsonPath("$.interpretation").value(org.hamcrest.Matchers.containsString("not authoritative mastery")))
                .andExpect(jsonPath("$.mastery").doesNotExist())
                .andExpect(jsonPath("$.recommendation").doesNotExist());

        assertThat(count("answer_attempts", sessionId)).isEqualTo(8);
        assertThat(countEvidence(sessionId)).isEqualTo(8);
        assertThat(countOutbox(sessionId)).isEqualTo(8);
        List<String> payloads = jdbcTemplate.queryForList(
                "SELECT CAST(payload AS CHAR) FROM outbox_events WHERE owner_module = 'assessment'",
                String.class);
        assertThat(payloads).allSatisfy(payload -> assertThat(payload)
                .doesNotContain("selectedOptionIds", "answerKey", "answer_key"));

        Cookie otherLearner = register("phase3-other@skillpath.local");
        mockMvc.perform(get("/api/v1/assessments/{sessionId}/result", sessionId).cookie(otherLearner))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("ASSESSMENT_SESSION_NOT_FOUND"));
    }

    @Test
    void expiresSessionAndRequiresActiveGoal() throws Exception {
        Cookie noGoal = register("phase3-no-goal@skillpath.local");
        mockMvc.perform(post("/api/v1/assessments/diagnostic").cookie(noGoal).with(csrf()))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("ACTIVE_GOAL_NOT_FOUND"));

        Cookie learner = register("phase3-expiry@skillpath.local");
        createGoal(learner, "phase3-goal-expiry");
        MvcResult started = mockMvc.perform(post("/api/v1/assessments/diagnostic")
                        .cookie(learner)
                        .with(csrf()))
                .andExpect(status().isCreated())
                .andReturn();
        String sessionId = JsonPath.read(started.getResponse().getContentAsString(), "$.id");
        jdbcTemplate.update(
                """
                UPDATE assessment_sessions
                SET started_at = DATE_SUB(UTC_TIMESTAMP(6), INTERVAL 8 DAY),
                    expires_at = DATE_SUB(UTC_TIMESTAMP(6), INTERVAL 1 SECOND)
                WHERE id = ?
                """,
                Long.parseLong(sessionId));

        mockMvc.perform(get("/api/v1/assessments/{sessionId}/next-question", sessionId).cookie(learner))
                .andExpect(status().isGone())
                .andExpect(jsonPath("$.code").value("ASSESSMENT_SESSION_EXPIRED"));
        assertThat(jdbcTemplate.queryForObject(
                        "SELECT status FROM assessment_sessions WHERE id = ?",
                        String.class,
                        Long.parseLong(sessionId)))
                .isEqualTo("EXPIRED");
    }

    @Test
    void concurrentSameKeySubmissionHasOneAttemptAndOneReplay() throws Exception {
        Cookie learner = register("phase3-concurrent@skillpath.local");
        createGoal(learner, "phase3-goal-concurrent");
        Long userId = jdbcTemplate.queryForObject(
                "SELECT user_id FROM user_credentials WHERE normalized_email = ?",
                Long.class,
                "phase3-concurrent@skillpath.local");
        AssessmentService.SessionView session = assessmentService.startDiagnostic(userId);
        AssessmentService.QuestionView question = assessmentService.nextQuestion(
                userId, Long.parseLong(session.id()));
        List<String> correct = correctOptions(Long.parseLong(question.questionVersionId()));
        AssessmentService.SubmitAnswer answer = new AssessmentService.SubmitAnswer(
                Long.parseLong(question.sessionQuestionId()), correct, new BigDecimal("0.7000"), 25);

        CountDownLatch start = new CountDownLatch(1);
        ExecutorService executor = Executors.newFixedThreadPool(2);
        try {
            List<Future<Boolean>> results = List.of(
                    executor.submit(() -> submitAfter(start, userId, session.id(), answer)),
                    executor.submit(() -> submitAfter(start, userId, session.id(), answer)));
            start.countDown();
            assertThat(List.of(
                            results.get(0).get(15, TimeUnit.SECONDS),
                            results.get(1).get(15, TimeUnit.SECONDS)))
                    .containsExactlyInAnyOrder(false, true);
        } finally {
            executor.shutdownNow();
        }
        assertThat(count("answer_attempts", session.id())).isEqualTo(1);
        assertThat(countEvidence(session.id())).isEqualTo(1);
        assertThat(countOutbox(session.id())).isEqualTo(1);
    }

    @Test
    void concurrentDiagnosticStartsConvergeOnOnePinnedSession() throws Exception {
        Cookie learner = register("phase3-concurrent-start@skillpath.local");
        createGoal(learner, "phase3-goal-concurrent-start");
        Long userId = jdbcTemplate.queryForObject(
                "SELECT user_id FROM user_credentials WHERE normalized_email = ?",
                Long.class,
                "phase3-concurrent-start@skillpath.local");

        CountDownLatch start = new CountDownLatch(1);
        ExecutorService executor = Executors.newFixedThreadPool(2);
        try {
            List<Future<AssessmentService.SessionView>> results = List.of(
                    executor.submit(() -> startAfter(start, userId)),
                    executor.submit(() -> startAfter(start, userId)));
            start.countDown();
            AssessmentService.SessionView first = results.get(0).get(15, TimeUnit.SECONDS);
            AssessmentService.SessionView second = results.get(1).get(15, TimeUnit.SECONDS);
            assertThat(first.id()).isEqualTo(second.id());
            assertThat(List.of(first.created(), second.created())).containsExactlyInAnyOrder(true, false);
        } finally {
            executor.shutdownNow();
        }

        assertThat(jdbcTemplate.queryForObject(
                        "SELECT COUNT(*) FROM assessment_sessions WHERE user_id = ? AND purpose = 'DIAGNOSTIC'",
                        Integer.class,
                        userId))
                .isEqualTo(1);
        assertThat(jdbcTemplate.queryForObject(
                        """
                        SELECT COUNT(*) FROM assessment_session_questions sq
                        JOIN assessment_sessions s ON s.id = sq.session_id
                        WHERE s.user_id = ?
                        """,
                        Integer.class,
                        userId))
                .isEqualTo(8);
    }

    @Test
    void localizesLearnerContentWithoutChangingQuestionOrOptionIdentity() throws Exception {
        mockMvc.perform(get("/api/v1/goal-templates")
                        .header(HttpHeaders.ACCEPT_LANGUAGE, "vi-VN"))
                .andExpect(status().isOk())
                .andExpect(header().string(HttpHeaders.CONTENT_LANGUAGE, "vi-VN"))
                .andExpect(jsonPath("$[0].displayName").value("Thực tập sinh Java Backend"));

        mockMvc.perform(get("/api/v1/knowledge/nodes/1001")
                        .header(HttpHeaders.ACCEPT_LANGUAGE, "vi-VN"))
                .andExpect(status().isOk())
                .andExpect(header().string(HttpHeaders.CONTENT_LANGUAGE, "vi-VN"))
                .andExpect(jsonPath("$.name").value("Nền tảng lập trình"));

        Cookie learner = register("phase3-locale@skillpath.local");
        createGoal(learner, "phase3-goal-locale");
        MvcResult started = mockMvc.perform(post("/api/v1/assessments/diagnostic")
                        .cookie(learner)
                        .with(csrf()))
                .andExpect(status().isCreated())
                .andReturn();
        String sessionId = JsonPath.read(started.getResponse().getContentAsString(), "$.id");

        MvcResult english = mockMvc.perform(get(
                                "/api/v1/assessments/{sessionId}/next-question", sessionId)
                        .cookie(learner)
                        .header(HttpHeaders.ACCEPT_LANGUAGE, "en"))
                .andExpect(status().isOk())
                .andExpect(header().string(HttpHeaders.CONTENT_LANGUAGE, "en"))
                .andReturn();
        MvcResult vietnamese = mockMvc.perform(get(
                                "/api/v1/assessments/{sessionId}/next-question", sessionId)
                        .cookie(learner)
                        .header(HttpHeaders.ACCEPT_LANGUAGE, "vi-VN"))
                .andExpect(status().isOk())
                .andExpect(header().string(HttpHeaders.CONTENT_LANGUAGE, "vi-VN"))
                .andReturn();
        MvcResult fallback = mockMvc.perform(get(
                                "/api/v1/assessments/{sessionId}/next-question", sessionId)
                        .cookie(learner)
                        .header(HttpHeaders.ACCEPT_LANGUAGE, "fr-FR"))
                .andExpect(status().isOk())
                .andExpect(header().string(HttpHeaders.CONTENT_LANGUAGE, "en"))
                .andReturn();

        JsonNode englishBody = objectMapper.readTree(english.getResponse().getContentAsString());
        JsonNode vietnameseBody = objectMapper.readTree(vietnamese.getResponse().getContentAsString());
        JsonNode fallbackBody = objectMapper.readTree(fallback.getResponse().getContentAsString());
        assertThat(vietnameseBody.path("sessionQuestionId"))
                .isEqualTo(englishBody.path("sessionQuestionId"));
        assertThat(vietnameseBody.path("questionVersionId"))
                .isEqualTo(englishBody.path("questionVersionId"));
        assertThat(vietnameseBody.path("prompt").asText())
                .isNotEqualTo(englishBody.path("prompt").asText());
        assertThat(optionIds(vietnameseBody)).containsExactlyElementsOf(optionIds(englishBody));
        assertThat(fallbackBody.path("prompt")).isEqualTo(englishBody.path("prompt"));
    }

    private AssessmentService.SessionView startAfter(CountDownLatch start, long userId)
            throws Exception {
        start.await(10, TimeUnit.SECONDS);
        return assessmentService.startDiagnostic(userId);
    }

    private boolean submitAfter(
            CountDownLatch start,
            long userId,
            String sessionId,
            AssessmentService.SubmitAnswer answer) throws Exception {
        start.await(10, TimeUnit.SECONDS);
        return assessmentService
                .submit(userId, Long.parseLong(sessionId), "phase3-concurrent-key", answer)
                .replayed();
    }

    private Cookie register(String email) throws Exception {
        String body = """
                {"email":"%s","password":"CorrectHorseBattery9","displayName":"Phase Three","timezone":"Asia/Bangkok"}
                """.formatted(email);
        Cookie cookie = mockMvc.perform(post("/api/v1/auth/register")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isCreated())
                .andReturn()
                .getResponse()
                .getCookie("SKILLPATH_SESSION");
        assertThat(cookie).isNotNull();
        return cookie;
    }

    private void createGoal(Cookie cookie, String key) throws Exception {
        String body = """
                {"goalTemplateId":"1","targetDate":"%s","timezone":"Asia/Bangkok","defaultDailyMinutes":60}
                """.formatted(LocalDate.now().plusDays(90));
        mockMvc.perform(post("/api/v1/goals")
                        .cookie(cookie)
                        .with(csrf())
                        .header("Idempotency-Key", key)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isCreated());
    }

    private QuestionInput questionInput(String responseBody) throws Exception {
        JsonNode response = objectMapper.readTree(responseBody);
        long versionId = Long.parseLong(response.path("questionVersionId").asText());
        return new QuestionInput(
                response.path("sessionQuestionId").asText(), correctOptions(versionId));
    }

    private List<String> correctOptions(long versionId) throws Exception {
        String answerKey = jdbcTemplate.queryForObject(
                "SELECT CAST(answer_key AS CHAR) FROM question_versions WHERE id = ?",
                String.class,
                versionId);
        JsonNode values = objectMapper.readTree(answerKey).path("correctOptionIds");
        List<String> result = new ArrayList<>();
        values.forEach(node -> result.add(node.asText()));
        return result;
    }

    private List<String> optionIds(JsonNode question) {
        List<String> result = new ArrayList<>();
        question.path("options").forEach(option -> result.add(option.path("id").asText()));
        return result;
    }

    private String answerBody(String sessionQuestionId, List<String> selected, int seconds)
            throws Exception {
        return objectMapper.writeValueAsString(java.util.Map.of(
                "sessionQuestionId", sessionQuestionId,
                "selectedOptionIds", selected,
                "selfConfidence", 0.7,
                "timeSpentSeconds", seconds));
    }

    private int count(String table, String sessionId) {
        if (!"answer_attempts".equals(table)) {
            throw new IllegalArgumentException("Unexpected table");
        }
        Integer count = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM answer_attempts WHERE session_id = ?",
                Integer.class,
                Long.parseLong(sessionId));
        return count == null ? 0 : count;
    }

    private int countEvidence(String sessionId) {
        Integer count = jdbcTemplate.queryForObject(
                """
                SELECT COUNT(*) FROM attempt_evidence ae
                JOIN answer_attempts aa ON aa.id = ae.attempt_id
                WHERE aa.session_id = ?
                """,
                Integer.class,
                Long.parseLong(sessionId));
        return count == null ? 0 : count;
    }

    private int countOutbox(String sessionId) {
        Integer count = jdbcTemplate.queryForObject(
                """
                SELECT COUNT(*) FROM outbox_events oe
                JOIN attempt_evidence ae ON oe.aggregate_id = CAST(ae.id AS CHAR)
                JOIN answer_attempts aa ON aa.id = ae.attempt_id
                WHERE aa.session_id = ? AND oe.event_type = 'AssessmentEvidenceCreated'
                """,
                Integer.class,
                Long.parseLong(sessionId));
        return count == null ? 0 : count;
    }

    private record QuestionInput(String sessionQuestionId, List<String> correctOptionIds) {}
}
