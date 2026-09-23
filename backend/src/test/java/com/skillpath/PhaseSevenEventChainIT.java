package com.skillpath;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.skillpath.planner.application.PlanningInputsReadyHandler;
import com.skillpath.progress.application.AssessmentEvidenceHandler;
import com.skillpath.review.application.EvidenceAcceptedHandler;
import com.skillpath.shared.application.OutboxHandler.OutboxEvent;
import jakarta.servlet.http.Cookie;
import java.math.BigDecimal;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.LocalDate;
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
class PhaseSevenEventChainIT {
    @Container static final MySQLContainer<?> MYSQL = new MySQLContainer<>("mysql:8.4")
            .withDatabaseName("skillpath").withUsername("skillpath").withPassword("integration-only");

    @DynamicPropertySource static void database(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", MYSQL::getJdbcUrl);
        registry.add("spring.datasource.username", MYSQL::getUsername);
        registry.add("spring.datasource.password", MYSQL::getPassword);
    }

    @Autowired MockMvc mvc;
    @Autowired JdbcTemplate jdbc;
    @Autowired AssessmentEvidenceHandler progress;
    @Autowired EvidenceAcceptedHandler review;
    @Autowired PlanningInputsReadyHandler planner;

    @Test void outOfOrderEvidenceAndUnchangedMasteryReachReviewBeforeOnePlannerRequest() throws Exception {
        Cookie learner = mvc.perform(post("/api/v1/auth/register").with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"email\":\"phase7-events@skillpath.local\",\"password\":\"CorrectHorseBattery9\",\"displayName\":\"P7\",\"timezone\":\"Asia/Ho_Chi_Minh\"}"))
                .andExpect(status().isCreated()).andReturn().getResponse().getCookie("SKILLPATH_SESSION");
        assertThat(learner).isNotNull();
        mvc.perform(post("/api/v1/goals").cookie(learner).with(csrf())
                .header("Idempotency-Key", "phase7-event-goal")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"goalTemplateId\":\"1\",\"targetDate\":\"%s\",\"timezone\":\"Asia/Ho_Chi_Minh\",\"defaultDailyMinutes\":60}"
                        .formatted(LocalDate.now().plusDays(90))))
                .andExpect(status().isCreated());
        Long user = jdbc.queryForObject("SELECT user_id FROM user_credentials WHERE normalized_email=?",
                Long.class, "phase7-events@skillpath.local");
        Long goal = jdbc.queryForObject("SELECT id FROM user_goals WHERE user_id=?", Long.class, user);
        assertThat(user).isNotNull();assertThat(goal).isNotNull();
        OutboxEvent second = evidenceEvent("p7-evidence-second", 900002, "UNDERSTANDING", user, goal);
        OutboxEvent first = evidenceEvent("p7-evidence-first", 900001, "RECOGNITION", user, goal);
        progress.handle(second);
        assertThat(count("SELECT COUNT(*) FROM outbox_events WHERE event_type='EvidenceAccepted'")).isZero();
        progress.handle(first);
        progress.handle(first);
        assertThat(count("SELECT COUNT(*) FROM knowledge_evidence WHERE user_id=?", user)).isEqualTo(2);
        assertThat(count("SELECT COUNT(*) FROM progress_attempt_evidence_receipts")).isEqualTo(2);
        assertThat(count("SELECT COUNT(*) FROM outbox_events WHERE event_type='EvidenceAccepted'")).isEqualTo(1);

        OutboxEvent accepted = event("EvidenceAccepted");
        review.handle(accepted);
        review.handle(accepted);
        assertThat(count("SELECT COUNT(*) FROM review_processed_attempts")).isEqualTo(1);
        assertThat(count("SELECT COUNT(*) FROM outbox_events WHERE event_type='PlanningInputsReady'")).isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT mastery_score FROM user_knowledge WHERE user_id=? AND knowledge_node_id=1001",
                BigDecimal.class, user)).isEqualByComparingTo("0.0000");

        OutboxEvent ready = event("PlanningInputsReady");
        planner.handle(ready);
        planner.handle(ready);
        assertThat(count("SELECT COUNT(*) FROM planner_replan_requests WHERE user_id=?", user)).isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT status FROM planner_replan_requests WHERE user_id=?",
                String.class, user)).isEqualTo("PENDING");
    }

    private OutboxEvent evidenceEvent(String key, long evidenceId, String dimension, long user, long goal) {
        Instant now = Instant.now().truncatedTo(java.time.temporal.ChronoUnit.MICROS);
        String payload = "{\"evidenceId\":\"%d\",\"attemptKind\":\"TASK_CHECK\",\"attemptId\":\"900000\",\"expectedEvidenceCount\":2,\"replanEligible\":true,\"userId\":\"%d\",\"goalId\":\"%d\",\"graphVersionId\":\"1\",\"knowledgeNodeId\":\"1001\",\"dimension\":\"%s\",\"score\":0,\"reliability\":0.45,\"policyVersion\":\"task-check-objective-v1\",\"observedAt\":\"%s\"}"
                .formatted(evidenceId, user, goal, dimension, now);
        jdbc.update("INSERT INTO outbox_events(event_key,owner_module,aggregate_type,aggregate_id,event_type,event_version,payload,status,attempt_count,available_at,occurred_at,created_at,updated_at) VALUES(?,'assessment','AttemptEvidence',?,'AssessmentEvidenceCreated',2,CAST(? AS JSON),'PENDING',0,?,?,?,?)",
                key, Long.toString(evidenceId), payload, Timestamp.from(now), Timestamp.from(now),
                Timestamp.from(now), Timestamp.from(now));
        return jdbc.queryForObject("SELECT id,event_key,owner_module,event_type,event_version,aggregate_id,CAST(payload AS CHAR),occurred_at FROM outbox_events WHERE event_key=?",
                (rs,n)->new OutboxEvent(rs.getLong(1),rs.getString(2),rs.getString(3),rs.getString(4),
                        rs.getInt(5),rs.getString(6),rs.getString(7),rs.getTimestamp(8).toInstant()),key);
    }

    private OutboxEvent event(String type) {
        return jdbc.queryForObject("SELECT id,event_key,owner_module,event_type,event_version,aggregate_id,CAST(payload AS CHAR),occurred_at FROM outbox_events WHERE event_type=? ORDER BY id DESC LIMIT 1",
                (rs,n)->new OutboxEvent(rs.getLong(1),rs.getString(2),rs.getString(3),rs.getString(4),
                        rs.getInt(5),rs.getString(6),rs.getString(7),rs.getTimestamp(8).toInstant()),type);
    }

    private int count(String sql, Object... args) {return jdbc.queryForObject(sql, Integer.class, args);}
}
