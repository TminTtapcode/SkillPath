package com.skillpath;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.skillpath.learning.application.LearningQueries;
import com.skillpath.assessment.application.AssessmentService;
import com.skillpath.planner.application.PlannerService;
import com.skillpath.planner.application.PlannerStore;
import com.skillpath.planner.application.PlannerReplanWorker;
import com.skillpath.planner.application.PlanningInputsReadyHandler;
import com.skillpath.planner.application.AdaptiveReplanService;
import com.skillpath.planner.application.ReplanRequestStore;
import com.skillpath.planner.application.PlannerDayStore;
import com.skillpath.knowledge.application.PlannerKnowledgeQueries;
import com.skillpath.planner.domain.PlannerPolicyV1;
import com.skillpath.goal.application.GoalQueries;
import com.skillpath.shared.application.OutboxHandler.OutboxEvent;
import com.skillpath.progress.application.AssessmentEvidenceHandler;
import com.skillpath.review.application.EvidenceAcceptedHandler;
import com.skillpath.shared.localization.SupportedLocale;
import jakarta.servlet.http.Cookie;
import java.math.BigDecimal;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.time.Clock;
import java.time.ZoneOffset;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.support.TransactionTemplate;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

@SpringBootTest(properties={"debug=false","skillpath.outbox.enabled=false",
        "skillpath.phase7.task-check-enabled=true"})
@AutoConfigureMockMvc
@Testcontainers
class PhaseSevenPartialLearningIT {
    @Container static final MySQLContainer<?> MYSQL=new MySQLContainer<>("mysql:8.4")
            .withDatabaseName("skillpath").withUsername("skillpath").withPassword("integration-only");
    @DynamicPropertySource static void database(DynamicPropertyRegistry registry){
        registry.add("spring.datasource.url",MYSQL::getJdbcUrl);
        registry.add("spring.datasource.username",MYSQL::getUsername);
        registry.add("spring.datasource.password",MYSQL::getPassword);
    }
    @Autowired MockMvc mvc;
    @Autowired JdbcTemplate jdbc;
    @Autowired PlannerService planner;
    @Autowired PlannerStore plannerStore;
    @Autowired LearningQueries learning;
    @Autowired AssessmentService assessment;
    @Autowired AdaptiveReplanService adaptive;
    @Autowired PlanningInputsReadyHandler intake;
    @Autowired GoalQueries goals;
    @Autowired TransactionTemplate transactions;
    @Autowired Clock clock;
    @Autowired ReplanRequestStore requests;
    @Autowired PlannerDayStore dayStore;
    @Autowired PlannerKnowledgeQueries knowledge;
    @Autowired AssessmentEvidenceHandler progressEvents;
    @Autowired EvidenceAcceptedHandler reviewEvents;

    @Test void checkedPlannerTaskFlowsThroughReviewToAnotherImmutableRevision()throws Exception{
        String email="p7-golden@skillpath.local";
        Cookie learner=register(email);
        createGoal(learner);
        long userId=jdbc.queryForObject("SELECT user_id FROM user_credentials WHERE normalized_email=?",
                Long.class,email);
        long goalId=jdbc.queryForObject("SELECT id FROM user_goals WHERE user_id=?",Long.class,userId);
        planner.generate(userId,"p7-golden-original",SupportedLocale.ENGLISH);
        // Fixture: an already accepted conceptual observation makes practice
        // eligible. Catalog retirement narrows the authored check to one version.
        List<Long> archived=jdbc.query("SELECT id FROM task_template_versions "
                +"WHERE graph_version_id=1 AND status='ACTIVE' AND id<>13007",
                (rs,row)->rs.getLong(1));
        try{
        jdbc.update("UPDATE task_template_versions SET status='RETIRED' "
                +"WHERE graph_version_id=1 AND status='ACTIVE' AND id<>13007");
        Instant now=clock.instant().truncatedTo(java.time.temporal.ChronoUnit.MICROS);
        String seed="{\"evidenceId\":\"970001\",\"attemptKind\":\"TASK_CHECK\","
                +"\"attemptId\":\"970000\",\"expectedEvidenceCount\":1,\"replanEligible\":true,"
                +"\"userId\":\"%d\",\"goalId\":\"%d\",\"graphVersionId\":\"1\","
                +"\"knowledgeNodeId\":\"1001\",\"dimension\":\"RECOGNITION\","
                +"\"score\":0.5,\"reliability\":0.45,\"policyVersion\":\"task-check-objective-v1\","
                +"\"observedAt\":\"%s\"}";
        String payload=seed.formatted(userId,goalId,now);
        jdbc.update("INSERT INTO outbox_events(event_key,owner_module,aggregate_type,aggregate_id,"
                +"event_type,event_version,payload,status,attempt_count,available_at,occurred_at,"
                +"created_at,updated_at) VALUES('p7-golden-seed','assessment','AttemptEvidence',"
                +"'970001','AssessmentEvidenceCreated',2,CAST(? AS JSON),'PENDING',0,?,?,?,?)",
                payload,Timestamp.from(now),Timestamp.from(now),Timestamp.from(now),Timestamp.from(now));
        progressEvents.handle(outbox("AssessmentEvidenceCreated"));
        reviewEvents.handle(outbox("EvidenceAccepted"));
        intake.handle(outbox("PlanningInputsReady"));
        var worker=new PlannerReplanWorker(requests,adaptive,goals,transactions,clock);
        assertThat(worker.processOne()).isTrue();
        var assigned=planner.today(userId,SupportedLocale.ENGLISH);
        assertThat(assigned.revision()).isEqualTo(2);
        long objectiveTask=jdbc.queryForObject("SELECT i.learning_task_id FROM daily_plan_items i "
                +"JOIN planner_decisions d ON d.id=i.decision_id WHERE i.plan_id=? "
                +"AND d.template_version_id=13007",Long.class,Long.parseLong(assigned.planId()));
        mvc.perform(post("/api/v1/learning/tasks/{id}/start",objectiveTask).cookie(learner)
                .with(csrf()).header("Idempotency-Key","p7-golden-start"))
                .andExpect(status().isOk());
        var checked=assessment.submitTaskCheck(userId,objectiveTask,"p7-golden-check",
                new AssessmentService.TaskCheckAnswer(List.of("count-four"),30,12));
        assertThat(checked.score()).isEqualByComparingTo(BigDecimal.ZERO);
        progressEvents.handle(outbox("AssessmentEvidenceCreated"));
        reviewEvents.handle(outbox("EvidenceAccepted"));
        intake.handle(outbox("PlanningInputsReady"));
        assertThat(worker.processOne()).isTrue();
        var revised=planner.today(userId,SupportedLocale.ENGLISH);
        assertThat(revised.revision()).isEqualTo(3);
        assertThat(revised.items()).anyMatch(item->item.taskId().equals(Long.toString(objectiveTask))
                && item.carried() && item.status().equals("COMPLETED"));
        assertThat(planner.roadmap(userId,50,null,SupportedLocale.ENGLISH).planId())
                .isEqualTo(revised.planId());
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM task_check_submissions "
                +"WHERE learning_task_id=?",Integer.class,objectiveTask)).isEqualTo(1);
        }finally{
            for(long versionId:archived)jdbc.update("UPDATE task_template_versions "
                    +"SET status='ACTIVE' WHERE id=?",versionId);
        }
    }

    @Test void missedDayRefreshExpiresOnlyUnstartedAndKeepsInProgress()throws Exception{
        String email="p7-missed-day@skillpath.local";
        Cookie learner=register(email);
        createGoal(learner);
        long userId=jdbc.queryForObject("SELECT user_id FROM user_credentials WHERE normalized_email=?",
                Long.class,email);
        var original=planner.generate(userId,"p7-missed-original",SupportedLocale.ENGLISH).plan();
        long startedId=Long.parseLong(original.items().getFirst().taskId());
        long oldPlanId=Long.parseLong(original.planId());
        mvc.perform(post("/api/v1/learning/tasks/{id}/start",startedId).cookie(learner)
                .with(csrf()).header("Idempotency-Key","p7-missed-start"))
                .andExpect(status().isOk());
        var tomorrow=Clock.fixed(clock.instant().plusSeconds(86400),ZoneOffset.UTC);
        var later=new AdaptiveReplanService(goals,learning,knowledge,assessment,plannerStore,
                dayStore,planner,tomorrow);
        var refreshed=transactions.execute(status->later.refresh(userId,"p7-missed-refresh",
                SupportedLocale.ENGLISH));
        assertThat(refreshed).isNotNull();
        assertThat(refreshed.planId()).isNotEqualTo(original.planId());
        assertThat(refreshed.revision()).isEqualTo(1);
        assertThat(refreshed.items()).anyMatch(item->item.taskId().equals(Long.toString(startedId))
                && item.carried() && item.status().equals("IN_PROGRESS"));
        assertThat(jdbc.queryForObject("SELECT status FROM daily_plans WHERE id=?",String.class,oldPlanId))
                .isEqualTo("CURRENT");
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM learning_tasks WHERE session_id=? "
                +"AND status='EXPIRED'",Integer.class,Long.parseLong(original.sessionId())))
                .isGreaterThan(0);
    }

    @Test void dayOverrideIsAuditedIdempotentAndPreservesActiveWork()throws Exception{
        String email="p7-override@skillpath.local";
        Cookie learner=register(email);
        createGoal(learner);
        long userId=jdbc.queryForObject("SELECT user_id FROM user_credentials WHERE normalized_email=?",
                Long.class,email);
        var original=planner.generate(userId,"p7-override-original",SupportedLocale.ENGLISH).plan();
        long startedId=Long.parseLong(original.items().getFirst().taskId());
        mvc.perform(post("/api/v1/learning/tasks/{id}/start",startedId).cookie(learner)
                .with(csrf()).header("Idempotency-Key","p7-override-start"))
                .andExpect(status().isOk());
        String body="{\"availableMinutes\":30}";
        mvc.perform(put("/api/v1/learning/today/available-minutes").cookie(learner)
                .header("Idempotency-Key","p7-override-no-csrf")
                .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isForbidden());
        mvc.perform(put("/api/v1/learning/today/available-minutes").cookie(learner).with(csrf())
                .header("Idempotency-Key","p7-override-invalid")
                .contentType(MediaType.APPLICATION_JSON).content("{\"availableMinutes\":181}"))
                .andExpect(status().isUnprocessableEntity());
        mvc.perform(put("/api/v1/learning/today/available-minutes").cookie(learner).with(csrf())
                .header("Idempotency-Key","p7-override-key")
                .contentType(MediaType.APPLICATION_JSON).content(body)).andExpect(status().isOk());
        mvc.perform(put("/api/v1/learning/today/available-minutes").cookie(learner).with(csrf())
                .header("Idempotency-Key","p7-override-key")
                .contentType(MediaType.APPLICATION_JSON).content(body)).andExpect(status().isOk());
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM daily_budget_overrides WHERE user_id=?",
                Integer.class,userId)).isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT default_daily_minutes FROM user_goals WHERE user_id=?",
                Integer.class,userId)).isEqualTo(60);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM planner_replan_requests "
                +"WHERE user_id=? AND attempt_kind='TIME_OVERRIDE'",Integer.class,userId)).isEqualTo(1);
        var worker=new PlannerReplanWorker(requests,adaptive,goals,transactions,clock);
        assertThat(worker.processOne()).isTrue();
        var revised=planner.today(userId,SupportedLocale.ENGLISH);
        assertThat(revised.budgetMinutes()).isEqualTo(30);
        assertThat(revised.revision()).isEqualTo(2);
        assertThat(revised.items()).anyMatch(item->item.taskId().equals(Long.toString(startedId))
                && item.carried() && item.status().equals("IN_PROGRESS"));
        assertThat(revised.items().stream().filter(item->item.status().equals("IN_PROGRESS")
                || !item.carried()).mapToInt(PlannerService.ItemView::minutes).sum()).isLessThanOrEqualTo(30);
        assertThat(adaptive.status(userId).status()).isEqualTo("COMPLETED");
    }

    @Test void reviewReadyWorkerCreatesImmutablePartialRevision()throws Exception{
        String email="p7-real-revision@skillpath.local";
        Cookie learner=register(email);
        createGoal(learner);
        long userId=jdbc.queryForObject("SELECT user_id FROM user_credentials WHERE normalized_email=?",
                Long.class,email);
        var original=planner.generate(userId,"p7-revision-original",SupportedLocale.ENGLISH).plan();
        long oldPlanId=Long.parseLong(original.planId());
        long oldSessionId=Long.parseLong(original.sessionId());
        long startedId=Long.parseLong(original.items().getFirst().taskId());
        String oldPayload=jdbc.queryForObject("SELECT s.input_payload FROM daily_plans p "
                +"JOIN planning_snapshots s ON s.id=p.snapshot_id WHERE p.id=?",String.class,oldPlanId);
        mvc.perform(post("/api/v1/learning/tasks/{id}/start",startedId).cookie(learner)
                .with(csrf()).header("Idempotency-Key","p7-revision-start"))
                .andExpect(status().isOk());
        List<Long> originallyUnstarted=learning.plannerTaskStates(userId,oldSessionId).stream()
                .filter(task->"ASSIGNED".equals(task.status()))
                .map(LearningQueries.PlannerTaskState::id).toList();
        long goalId=jdbc.queryForObject("SELECT id FROM user_goals WHERE user_id=?",Long.class,userId);
        Instant now=clock.instant();
        long attempt=991122L;
        String payload=("{\"attemptKind\":\"TASK_CHECK\",\"attemptId\":\"%d\",\"userId\":\"%d\","
                +"\"goalId\":\"%d\",\"graphVersionId\":\"%s\",\"sourceEventId\":\"991121\","
                +"\"reviewedAt\":\"%s\"}").formatted(attempt,userId,goalId,original.graphVersionId(),now);
        intake.handle(new OutboxEvent(991123L,"p7-ready-revision","review","PlanningInputsReady",1,
                Long.toString(attempt),payload,now));
        var worker=new PlannerReplanWorker(requests,adaptive,goals,transactions,clock);
        assertThat(worker.processOne()).isTrue();
        var revised=planner.today(userId,SupportedLocale.ENGLISH);
        assertThat(revised.revision()).isEqualTo(2);
        assertThat(revised.plannerPolicyVersion()).isEqualTo("planner-v2");
        assertThat(revised.items()).anyMatch(item->item.taskId().equals(Long.toString(startedId))
                && item.carried() && item.status().equals("IN_PROGRESS"));
        assertThat(learning.plannerTaskStates(userId,oldSessionId)).anyMatch(
                task->task.id()==startedId && task.status().equals("IN_PROGRESS"));
        assertThat(learning.plannerTaskStates(userId,oldSessionId)).filteredOn(
                task->originallyUnstarted.contains(task.id()))
                .allMatch(task->task.status().equals("EXPIRED"));
        assertThat(jdbc.queryForObject("SELECT s.input_payload FROM daily_plans p "
                +"JOIN planning_snapshots s ON s.id=p.snapshot_id WHERE p.id=?",String.class,oldPlanId))
                .isEqualTo(oldPayload);
        assertThat(jdbc.queryForObject("SELECT status FROM daily_plans WHERE id=?",String.class,oldPlanId))
                .isEqualTo("SUPERSEDED");
    }

    @Test void preservesStartedTaskAndExpiresOnlyUnstartedOnAdaptiveRevision()throws Exception{
        String email="p7-partial@skillpath.local";
        Cookie learner=register(email);
        createGoal(learner);
        long userId=jdbc.queryForObject("SELECT user_id FROM user_credentials WHERE normalized_email=?",
                Long.class,email);
        var plan=planner.generate(userId,"p7-partial-original",SupportedLocale.ENGLISH).plan();
        assertThat(plan.items()).hasSizeGreaterThanOrEqualTo(2);
        long sessionId=Long.parseLong(plan.sessionId());
        long startedId=Long.parseLong(plan.items().get(0).taskId());
        mvc.perform(post("/api/v1/learning/tasks/{id}/start",startedId).cookie(learner)
                .with(csrf()).header("Idempotency-Key","p7-partial-start"))
                .andExpect(status().isOk());
        var before=learning.plannerTaskStates(userId,sessionId);
        List<Long> unstarted=before.stream().filter(task->"ASSIGNED".equals(task.status()))
                .map(LearningQueries.PlannerTaskState::id).toList();
        assertThat(unstarted).isNotEmpty();
        assertThatThrownBy(()->learning.revisePlannerAssignment(userId+1000000L,sessionId,
                Long.parseLong(plan.graphVersionId()),
                unstarted,List.of(),Instant.now())).isInstanceOf(RuntimeException.class);
        assertThatThrownBy(()->learning.revisePlannerAssignment(userId,sessionId,
                Long.parseLong(plan.graphVersionId()),unstarted,
                List.of(new LearningQueries.PlannerSelection(1L,99999999L,1)),Instant.now()))
                .isInstanceOf(RuntimeException.class);
        assertThat(learning.plannerTaskStates(userId,sessionId).stream()
                .filter(task->unstarted.contains(task.id()))
                .allMatch(task->"ASSIGNED".equals(task.status()))).isTrue();
        learning.revisePlannerAssignment(userId,sessionId,Long.parseLong(plan.graphVersionId()),
                unstarted,List.of(),Instant.now());
        var after=learning.plannerTaskStates(userId,sessionId);
        assertThat(after.stream().filter(task->task.id()==startedId).findFirst().orElseThrow().status())
                .isEqualTo("IN_PROGRESS");
        assertThat(after.stream().filter(task->unstarted.contains(task.id()))
                .allMatch(task->"EXPIRED".equals(task.status()))).isTrue();
        assertThat(jdbc.queryForObject("SELECT status FROM learning_sessions WHERE id=?",String.class,sessionId))
                .isEqualTo("ACTIVE");
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM learning_task_events WHERE task_id IN ("
                +unstarted.stream().map(id->"?").collect(java.util.stream.Collectors.joining(","))+ ") AND to_status='EXPIRED'",
                Integer.class,unstarted.toArray())).isEqualTo(unstarted.size());
    }

    @Test void appendedObjectiveTaskCanFollowExpiredPlannerTasks()throws Exception{
        String email="p7-append@skillpath.local";
        Cookie learner=register(email);
        createGoal(learner);
        long userId=jdbc.queryForObject("SELECT user_id FROM user_credentials WHERE normalized_email=?",
                Long.class,email);
        var plan=planner.generate(userId,"p7-append-original",SupportedLocale.ENGLISH).plan();
        long planId=Long.parseLong(plan.planId());
        long sessionId=Long.parseLong(plan.sessionId());
        long graphVersionId=Long.parseLong(plan.graphVersionId());
        long snapshotId=jdbc.queryForObject("SELECT snapshot_id FROM daily_plans WHERE id=?",
                Long.class,planId);
        var variant=new PlannerPolicyV1.Variant(13007L,1001L,"PRACTICE",1,12);
        var choice=new PlannerPolicyV1.Choice(1001L,variant,BigDecimal.ONE,BigDecimal.ONE,
                BigDecimal.ONE,BigDecimal.ZERO,BigDecimal.ONE,BigDecimal.ONE,BigDecimal.ZERO,
                List.of("TASK_CHECK"));
        long decisionId=plannerStore.decision(snapshotId,choice,List.of(),Instant.now());
        var unstarted=learning.plannerTaskStates(userId,sessionId).stream()
                .map(LearningQueries.PlannerTaskState::id).toList();
        var added=learning.revisePlannerAssignment(userId,sessionId,graphVersionId,unstarted,
                List.of(new LearningQueries.PlannerSelection(decisionId,13007L,1)),Instant.now());
        assertThat(added).hasSize(1);
        long taskId=added.getFirst().id();
        assertThat(assessment.taskCheck(userId,taskId,SupportedLocale.VIETNAMESE).options())
                .hasSize(4);
        mvc.perform(post("/api/v1/learning/tasks/{id}/start",taskId).cookie(learner)
                .with(csrf()).header("Idempotency-Key","p7-append-start"))
                .andExpect(status().isOk());
        var attempt=assessment.submitTaskCheck(userId,taskId,"p7-append-check",
                new AssessmentService.TaskCheckAnswer(List.of("count-six","iterations-three"),60,12));
        assertThat(attempt.score()).isEqualByComparingTo(BigDecimal.ONE);
        assertThat(learning.plannerTaskStates(userId,sessionId).stream()
                .filter(task->task.id()==taskId).findFirst().orElseThrow().status())
                .isEqualTo("COMPLETED");
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM learning_task_events WHERE task_id=? AND to_status='COMPLETED'",
                Integer.class,taskId)).isEqualTo(1);
    }

    private Cookie register(String email)throws Exception{
        String body="""
                {"email":"%s","password":"CorrectHorseBattery9","displayName":"Partial Plan","timezone":"Asia/Ho_Chi_Minh"}
                """.formatted(email);
        return mvc.perform(post("/api/v1/auth/register").with(csrf())
                .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isCreated()).andReturn().getResponse().getCookie("SKILLPATH_SESSION");
    }
    private void createGoal(Cookie cookie)throws Exception{
        String body="""
                {"goalTemplateId":"1","targetDate":"%s","timezone":"Asia/Ho_Chi_Minh","defaultDailyMinutes":60}
                """.formatted(LocalDate.now().plusDays(90));
        mvc.perform(post("/api/v1/goals").cookie(cookie).with(csrf())
                .header("Idempotency-Key","p7-partial-goal")
                .contentType(MediaType.APPLICATION_JSON).content(body)).andExpect(status().isCreated());
    }

    private OutboxEvent outbox(String type){
        return jdbc.queryForObject("SELECT id,event_key,owner_module,event_type,event_version,"
                +"aggregate_id,CAST(payload AS CHAR),occurred_at FROM outbox_events "
                +"WHERE event_type=? ORDER BY id DESC LIMIT 1",
                (rs,row)->new OutboxEvent(rs.getLong(1),rs.getString(2),rs.getString(3),
                        rs.getString(4),rs.getInt(5),rs.getString(6),rs.getString(7),
                        rs.getTimestamp(8).toInstant()),type);
    }
}
