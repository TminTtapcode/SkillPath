package com.skillpath;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.skillpath.learning.application.LearningService;
import jakarta.servlet.http.Cookie;
import java.time.LocalDate;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

@SpringBootTest(properties = {"debug=false", "skillpath.outbox.enabled=false"})
@AutoConfigureMockMvc
@Testcontainers
class PhaseFiveFlowIT {
    @Container static final MySQLContainer<?> MYSQL = new MySQLContainer<>("mysql:8.4")
            .withDatabaseName("skillpath").withUsername("skillpath").withPassword("integration-only");
    @DynamicPropertySource static void database(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", MYSQL::getJdbcUrl);
        registry.add("spring.datasource.username", MYSQL::getUsername);
        registry.add("spring.datasource.password", MYSQL::getPassword);
    }
    @Autowired MockMvc mvc;
    @Autowired JdbcTemplate jdbc;
    @Autowired ObjectMapper json;
    @Autowired LearningService learningService;

    @Test
    void learnerExecutesPinnedBilingualSequenceWithoutCreatingKnowledgeEvidence() throws Exception {
        mvc.perform(get("/api/v1/learning/sequences")).andExpect(status().isUnauthorized());
        Cookie learner = register("phase5-flow@skillpath.local");
        mvc.perform(get("/api/v1/learning/sequences").cookie(learner))
                .andExpect(status().isNotFound());
        createGoal(learner, "phase5-goal");
        mvc.perform(get("/api/v1/learning/sequences").cookie(learner).header("Accept-Language", "vi-VN"))
                .andExpect(status().isOk()).andExpect(header().string("Content-Language", "vi-VN"))
                .andExpect(jsonPath("$[0].totalMinutes").value(30))
                .andExpect(jsonPath("$[0].title").value("Luồng chương trình: học, thực hành, nhớ lại"));
        mvc.perform(post("/api/v1/learning/sequences/programming-flow-foundations/sessions")
                .cookie(learner).header("Idempotency-Key", "p5-start"))
                .andExpect(status().isForbidden());
        String sessionId = json.readTree(mvc.perform(post("/api/v1/learning/sequences/programming-flow-foundations/sessions")
                .cookie(learner).with(csrf()).header("Idempotency-Key", "p5-start"))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString()).path("sessionId").asText();
        mvc.perform(post("/api/v1/learning/sequences/programming-flow-foundations/sessions")
                .cookie(learner).with(csrf()).header("Idempotency-Key", "p5-start"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.sessionId").value(sessionId))
                .andExpect(jsonPath("$.replayed").value(true));
        Cookie stranger = register("phase5-stranger@skillpath.local");
        mvc.perform(get("/api/v1/learning/sessions/{id}", sessionId).cookie(stranger))
                .andExpect(status().isNotFound());
        JsonNode session = json.readTree(mvc.perform(get("/api/v1/learning/sessions/{id}", sessionId)
                .cookie(learner).header("Accept-Language", "en"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.tasks.length()").value(3))
                .andReturn().getResponse().getContentAsString());
        String first = session.path("tasks").get(0).path("id").asText();
        String second = session.path("tasks").get(1).path("id").asText();
        String third = session.path("tasks").get(2).path("id").asText();
        mvc.perform(get("/api/v1/learning/sessions/{id}", sessionId)
                .cookie(learner).header("Accept-Language", "vi-VN"))
                .andExpect(status().isOk()).andExpect(header().string("Content-Language", "vi-VN"))
                .andExpect(jsonPath("$.tasks[0].id").value(first))
                .andExpect(jsonPath("$.tasks[0].title").value("Học: lần theo luồng chương trình"));
        mvc.perform(post("/api/v1/learning/tasks/{id}/start", first).cookie(stranger).with(csrf())
                .header("Idempotency-Key", "stranger-task"))
                .andExpect(status().isNotFound());
        mvc.perform(post("/api/v1/learning/tasks/{id}/start", first).cookie(learner)
                .header("Idempotency-Key", "missing-csrf"))
                .andExpect(status().isForbidden());
        mvc.perform(post("/api/v1/learning/tasks/{id}/start", second).cookie(learner).with(csrf())
                .header("Idempotency-Key", "p5-order"))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("LEARNING_TASK_OUT_OF_ORDER"));
        command(learner, first, "start", "p5-first-start", null).andExpect(status().isOk());
        command(learner, first, "complete", "p5-incomplete-checklist",
                "{\"actualMinutes\":10,\"completedStepIds\":[\"read\"]}")
                .andExpect(status().isUnprocessableEntity());
        command(learner, first, "complete", "p5-excess-minutes",
                "{\"actualMinutes\":361,\"completedStepIds\":[\"read\",\"trace\"]}")
                .andExpect(status().isBadRequest());
        String firstCompletion = "{\"actualMinutes\":10,\"completedStepIds\":[\"read\",\"trace\"]}";
        command(learner, first, "complete", "p5-first-complete", firstCompletion)
                .andExpect(status().isOk()).andExpect(jsonPath("$.status").value("COMPLETED"));
        command(learner, first, "complete", "p5-first-complete", firstCompletion)
                .andExpect(status().isOk()).andExpect(jsonPath("$.replayed").value(true));
        command(learner, first, "complete", "p5-different-key", firstCompletion)
                .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("LEARNING_TASK_STATE_CONFLICT"));
        command(learner, first, "complete", "p5-first-complete", "{\"actualMinutes\":11,\"completedStepIds\":[\"read\",\"trace\"]}")
                .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("IDEMPOTENCY_KEY_REUSED"));
        command(learner, second, "start", "p5-second-start", null).andExpect(status().isOk());
        command(learner, second, "blocked", "p5-blocked", "{\"reasonCode\":\"DIFFICULT\"}")
                .andExpect(status().isOk()).andExpect(jsonPath("$.status").value("BLOCKED"));
        command(learner, second, "resume", "p5-resume", null).andExpect(status().isOk());
        command(learner, second, "complete", "p5-second-complete", "{\"actualMinutes\":15,\"completedStepIds\":[\"attempt\",\"check\"]}")
                .andExpect(status().isOk());
        command(learner, third, "start", "p5-third-start", null).andExpect(status().isOk());
        command(learner, third, "complete", "p5-third-complete", "{\"actualMinutes\":5,\"completedStepIds\":[\"recall\",\"compare\"]}")
                .andExpect(status().isOk());
        mvc.perform(get("/api/v1/learning/sessions/{id}", sessionId).cookie(learner))
                .andExpect(status().isOk()).andExpect(jsonPath("$.status").value("COMPLETED"));
        assertThat(count("learning_tasks")).isEqualTo(3);
        assertThat(count("learning_task_events")).isEqualTo(8);
        assertThat(count("knowledge_evidence")).isZero();
        assertThat(count("user_knowledge")).isZero();
        assertThat(count("review_schedules")).isZero();

        long retiredVersion = jdbc.queryForObject("SELECT task_template_version_id FROM learning_sequence_items WHERE position=3",
                Long.class);
        jdbc.update("UPDATE task_template_versions SET status='RETIRED' WHERE id=?", retiredVersion);
        try {
            mvc.perform(get("/api/v1/learning/sequences").cookie(learner))
                    .andExpect(status().isOk()).andExpect(jsonPath("$.length()").value(0));
            mvc.perform(post("/api/v1/learning/sequences/programming-flow-foundations/sessions")
                    .cookie(learner).with(csrf()).header("Idempotency-Key", "p5-retired-start"))
                    .andExpect(status().isUnprocessableEntity());
            mvc.perform(get("/api/v1/learning/sessions/{id}", sessionId).cookie(learner))
                    .andExpect(status().isOk()).andExpect(jsonPath("$.tasks.length()").value(3));
        } finally {
            jdbc.update("UPDATE task_template_versions SET status='ACTIVE' WHERE id=?", retiredVersion);
        }
    }

    @Test
    void concurrentStartsConvergeAndSkippedSequenceStops() throws Exception {
        Cookie learner = register("phase5-concurrent@skillpath.local");
        createGoal(learner, "phase5-concurrent-goal");
        long userId = jdbc.queryForObject("SELECT user_id FROM user_credentials WHERE normalized_email=?",
                Long.class, "phase5-concurrent@skillpath.local");
        var ready = new CountDownLatch(2);
        var go = new CountDownLatch(1);
        try (var pool = Executors.newFixedThreadPool(2)) {
            var first = pool.submit(() -> { ready.countDown(); go.await(10, TimeUnit.SECONDS);
                return learningService.start(userId, "programming-flow-foundations", "p5-concurrent-a"); });
            var second = pool.submit(() -> { ready.countDown(); go.await(10, TimeUnit.SECONDS);
                return learningService.start(userId, "programming-flow-foundations", "p5-concurrent-b"); });
            assertThat(ready.await(10, TimeUnit.SECONDS)).isTrue();
            go.countDown();
            var a = first.get(20, TimeUnit.SECONDS);
            var b = second.get(20, TimeUnit.SECONDS);
            assertThat(a.sessionId()).isEqualTo(b.sessionId());
            assertThat(a.created() ^ b.created()).isTrue();
            JsonNode session = json.readTree(mvc.perform(get("/api/v1/learning/sessions/{id}", a.sessionId())
                    .cookie(learner)).andExpect(status().isOk()).andReturn().getResponse().getContentAsString());
            String taskId = session.path("tasks").get(0).path("id").asText();
            command(learner, taskId, "skip", "p5-skip", "{\"reasonCode\":\"TIME\"}")
                    .andExpect(status().isOk()).andExpect(jsonPath("$.status").value("SKIPPED"));
            mvc.perform(get("/api/v1/learning/sessions/{id}", a.sessionId()).cookie(learner))
                    .andExpect(status().isOk()).andExpect(jsonPath("$.status").value("STOPPED"));
        }
    }

    private org.springframework.test.web.servlet.ResultActions command(Cookie cookie, String id, String operation,
            String key, String body) throws Exception {
        var request = post("/api/v1/learning/tasks/{id}/{command}", id, operation)
                .cookie(cookie).with(csrf()).header("Idempotency-Key", key);
        if (body != null) request.contentType(MediaType.APPLICATION_JSON).content(body);
        return mvc.perform(request);
    }
    private int count(String table) {
        return jdbc.queryForObject("SELECT COUNT(*) FROM " + table, Integer.class);
    }
    private Cookie register(String email) throws Exception {
        String body = """
                {"email":"%s","password":"CorrectHorseBattery9","displayName":"Phase Five","timezone":"Asia/Ho_Chi_Minh"}
                """.formatted(email);
        Cookie cookie = mvc.perform(post("/api/v1/auth/register").with(csrf())
                .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isCreated()).andReturn().getResponse().getCookie("SKILLPATH_SESSION");
        assertThat(cookie).isNotNull();
        return cookie;
    }
    private void createGoal(Cookie cookie, String key) throws Exception {
        String body = """
                {"goalTemplateId":"1","targetDate":"%s","timezone":"Asia/Ho_Chi_Minh","defaultDailyMinutes":60}
                """.formatted(LocalDate.now().plusDays(90));
        mvc.perform(post("/api/v1/goals").cookie(cookie).with(csrf())
                .header("Idempotency-Key", key).contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isCreated());
    }
}
