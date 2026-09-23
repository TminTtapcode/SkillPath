package com.skillpath;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.skillpath.learning.application.LearningStore;
import com.skillpath.learning.application.LearningStore.CatalogTask;
import com.skillpath.learning.application.LearningStore.LocalizedText;
import com.skillpath.learning.application.LearningStore.SequenceDefinition;
import com.skillpath.learning.application.LearningStore.Step;
import com.skillpath.planner.application.PlanningInputsReadyHandler;
import com.skillpath.progress.application.AssessmentEvidenceHandler;
import com.skillpath.review.application.EvidenceAcceptedHandler;
import com.skillpath.shared.application.OutboxHandler.OutboxEvent;
import jakarta.servlet.http.Cookie;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
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

@SpringBootTest(properties={"debug=false","skillpath.outbox.enabled=false",
        "skillpath.phase7.task-check-enabled=true"})
@AutoConfigureMockMvc
@Testcontainers
class PhaseSevenTaskCheckIT {
    @Container static final MySQLContainer<?> MYSQL=new MySQLContainer<>("mysql:8.4")
            .withDatabaseName("skillpath").withUsername("skillpath").withPassword("integration-only");
    @DynamicPropertySource static void database(DynamicPropertyRegistry registry){
        registry.add("spring.datasource.url",MYSQL::getJdbcUrl);
        registry.add("spring.datasource.username",MYSQL::getUsername);
        registry.add("spring.datasource.password",MYSQL::getPassword);
    }
    @Autowired MockMvc mvc;
    @Autowired JdbcTemplate jdbc;
    @Autowired LearningStore learning;
    @Autowired AssessmentEvidenceHandler progress;
    @Autowired EvidenceAcceptedHandler review;
    @Autowired PlanningInputsReadyHandler planner;

    @Test void objectiveTaskCannotSelfReportAndSubmissionIsAtomicAndIdempotent() throws Exception {
        assertThat(learning.activeVariants(1).stream().map(v->v.content().templateVersionId()))
                .doesNotContain(13007L);
        assertThat(learning.activeAdaptiveVariants(1).stream().map(v->v.content().templateVersionId()))
                .contains(13007L,13008L);
        Cookie learner=register("p7-check@skillpath.local");
        createGoal(learner,"p7-check-goal");
        long user=jdbc.queryForObject("SELECT user_id FROM user_credentials WHERE normalized_email=?",
                Long.class,"p7-check@skillpath.local");
        long goal=jdbc.queryForObject("SELECT id FROM user_goals WHERE user_id=?",Long.class,user);
        var content=new CatalogTask(13007,1,"PRACTICE","OBJECTIVE",12,1001,
                new LocalizedText("Counter trace","Lần theo biến đếm"),
                new LocalizedText("Trace before check","Lần theo trước khi kiểm tra"),
                new LocalizedText("Resource","Tài liệu"),new LocalizedText("Trace","Lần theo"),
                List.of(new Step("trace","Trace")),List.of(new Step("trace","Lần theo")));
        long sequenceId=jdbc.queryForObject("SELECT id FROM learning_sequences WHERE sequence_key='programming-flow-foundations'",Long.class);
        long session=learning.createSession(user,goal,new SequenceDefinition(sequenceId,"p7-fixture",1,1,
                new LocalizedText("Check","Kiểm tra"),new LocalizedText("",""),List.of(content)),Instant.now());
        long task=jdbc.queryForObject("SELECT id FROM learning_tasks WHERE session_id=?",Long.class,session);
        Cookie stranger=register("p7-check-stranger@skillpath.local");
        mvc.perform(get("/api/v1/learning/tasks/{id}/check",task).cookie(stranger))
                .andExpect(status().isNotFound());
        mvc.perform(get("/api/v1/learning/tasks/{id}/check",task).cookie(learner)
                .header("Accept-Language","vi-VN"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.questionVersionId").value("3109"))
                .andExpect(jsonPath("$.options[0].id").value("count-six"))
                .andExpect(jsonPath("$.answerKey").doesNotExist());
        String answer="{\"selectedOptionIds\":[\"count-four\"],\"timeSpentSeconds\":30,\"actualMinutes\":12}";
        mvc.perform(post("/api/v1/learning/tasks/{id}/check/attempts",task).cookie(learner).with(csrf())
                .header("Idempotency-Key","p7-too-early").contentType(MediaType.APPLICATION_JSON)
                .content(answer)).andExpect(status().isConflict());
        assertThat(count("SELECT COUNT(*) FROM task_check_submissions")).isZero();
        mvc.perform(post("/api/v1/learning/tasks/{id}/start",task).cookie(learner).with(csrf())
                .header("Idempotency-Key","p7-start"))
                .andExpect(status().isOk());
        mvc.perform(post("/api/v1/learning/tasks/{id}/complete",task).cookie(learner).with(csrf())
                .header("Idempotency-Key","p7-self-report").contentType(MediaType.APPLICATION_JSON)
                .content("{\"actualMinutes\":12,\"completedStepIds\":[\"trace\"]}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("OBJECTIVE_CHECK_REQUIRED"));
        mvc.perform(post("/api/v1/learning/tasks/{id}/check/attempts",task).cookie(learner).with(csrf())
                .header("Idempotency-Key","p7-invalid-option").contentType(MediaType.APPLICATION_JSON)
                .content("{\"selectedOptionIds\":[\"forged\"],\"timeSpentSeconds\":30,\"actualMinutes\":12}"))
                .andExpect(status().isUnprocessableEntity());
        mvc.perform(post("/api/v1/learning/tasks/{id}/check/attempts",task).cookie(learner)
                .header("Idempotency-Key","p7-submit").contentType(MediaType.APPLICATION_JSON)
                .content(answer)).andExpect(status().isForbidden());
        mvc.perform(post("/api/v1/learning/tasks/{id}/check/attempts",task).cookie(learner).with(csrf())
                .header("Idempotency-Key","p7-submit").contentType(MediaType.APPLICATION_JSON)
                .content(answer)).andExpect(status().isCreated()).andExpect(jsonPath("$.score").value(0));
        mvc.perform(post("/api/v1/learning/tasks/{id}/check/attempts",task).cookie(learner).with(csrf())
                .header("Idempotency-Key","p7-submit").contentType(MediaType.APPLICATION_JSON)
                .content(answer)).andExpect(status().isOk()).andExpect(jsonPath("$.replayed").value(true));
        mvc.perform(post("/api/v1/learning/tasks/{id}/check/attempts",task).cookie(learner).with(csrf())
                .header("Idempotency-Key","p7-other").contentType(MediaType.APPLICATION_JSON)
                .content(answer)).andExpect(status().isConflict());
        mvc.perform(post("/api/v1/learning/tasks/{id}/check/attempts",task).cookie(learner).with(csrf())
                .header("Idempotency-Key","p7-submit").contentType(MediaType.APPLICATION_JSON)
                .content("{\"selectedOptionIds\":[\"count-six\"],\"timeSpentSeconds\":30,\"actualMinutes\":12}"))
                .andExpect(status().isConflict());
        assertThat(count("SELECT COUNT(*) FROM task_check_submissions WHERE learning_task_id=?",task)).isEqualTo(1);
        assertThat(count("SELECT COUNT(*) FROM attempt_evidence ae JOIN task_check_submissions s ON s.answer_attempt_id=ae.attempt_id WHERE s.learning_task_id=?",task)).isEqualTo(1);
        assertThat(count("SELECT COUNT(*) FROM outbox_events WHERE event_type='AssessmentEvidenceCreated' AND event_version=2")).isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT CAST(payload AS CHAR) FROM outbox_events WHERE event_type='AssessmentEvidenceCreated' AND event_version=2",
                String.class)).doesNotContain("count-four","count-six","correctOptionIds");
        assertThat(jdbc.queryForObject("SELECT status FROM learning_tasks WHERE id=?",String.class,task)).isEqualTo("COMPLETED");
        assertThat(count("SELECT COUNT(*) FROM learning_task_events WHERE task_id=? AND to_status='COMPLETED'",task)).isEqualTo(1);
        assertThat(count("SELECT COUNT(*) FROM knowledge_evidence")).isZero();
        progress.handle(event("AssessmentEvidenceCreated"));
        assertThat(count("SELECT COUNT(*) FROM knowledge_evidence WHERE user_id=?",user)).isEqualTo(1);
        review.handle(event("EvidenceAccepted"));
        planner.handle(event("PlanningInputsReady"));
        assertThat(count("SELECT COUNT(*) FROM review_processed_attempts WHERE user_id=?",user)).isEqualTo(1);
        assertThat(count("SELECT COUNT(*) FROM planner_replan_requests WHERE user_id=?",user)).isEqualTo(1);
    }

    private Cookie register(String email)throws Exception{
        return mvc.perform(post("/api/v1/auth/register").with(csrf()).contentType(MediaType.APPLICATION_JSON)
                .content("{\"email\":\"%s\",\"password\":\"CorrectHorseBattery9\",\"displayName\":\"P7\",\"timezone\":\"Asia/Ho_Chi_Minh\"}".formatted(email)))
                .andExpect(status().isCreated()).andReturn().getResponse().getCookie("SKILLPATH_SESSION");
    }
    private void createGoal(Cookie learner,String key)throws Exception{
        mvc.perform(post("/api/v1/goals").cookie(learner).with(csrf()).header("Idempotency-Key",key)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"goalTemplateId\":\"1\",\"targetDate\":\"%s\",\"timezone\":\"Asia/Ho_Chi_Minh\",\"defaultDailyMinutes\":60}".formatted(LocalDate.now().plusDays(90))))
                .andExpect(status().isCreated());
    }
    private int count(String sql,Object... args){return jdbc.queryForObject(sql,Integer.class,args);}
    private OutboxEvent event(String type){
        return jdbc.queryForObject("SELECT id,event_key,owner_module,event_type,event_version,aggregate_id,CAST(payload AS CHAR),occurred_at FROM outbox_events WHERE event_type=? ORDER BY id DESC LIMIT 1",
                (rs,n)->new OutboxEvent(rs.getLong(1),rs.getString(2),rs.getString(3),rs.getString(4),
                        rs.getInt(5),rs.getString(6),rs.getString(7),rs.getTimestamp(8).toInstant()),type);
    }
}
