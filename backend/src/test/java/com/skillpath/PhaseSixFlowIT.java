package com.skillpath;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.skillpath.planner.application.PlannerService;
import com.skillpath.shared.localization.SupportedLocale;
import jakarta.servlet.http.Cookie;
import java.time.LocalDate;
import java.util.List;
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

@SpringBootTest(properties={"debug=false","skillpath.outbox.enabled=false"})
@AutoConfigureMockMvc
@Testcontainers
class PhaseSixFlowIT {
    @Container static final MySQLContainer<?> MYSQL=new MySQLContainer<>("mysql:8.4")
            .withDatabaseName("skillpath").withUsername("skillpath").withPassword("integration-only");
    @DynamicPropertySource static void database(DynamicPropertyRegistry registry){
        registry.add("spring.datasource.url",MYSQL::getJdbcUrl);
        registry.add("spring.datasource.username",MYSQL::getUsername);
        registry.add("spring.datasource.password",MYSQL::getPassword);
    }
    @Autowired MockMvc mvc;
    @Autowired ObjectMapper json;
    @Autowired JdbcTemplate jdbc;
    @Autowired PlannerService planner;

    @Test void generatesReplaysAndSupersedesOnlyUnstartedPlan()throws Exception{
        mvc.perform(get("/api/v1/learning/today")).andExpect(status().isUnauthorized());
        Cookie learner=register("phase6-flow@skillpath.local");
        createGoal(learner,"phase6-goal");
        int priorPlans=jdbc.queryForObject("SELECT COUNT(*) FROM daily_plans",Integer.class);
        int priorSnapshots=jdbc.queryForObject("SELECT COUNT(*) FROM planning_snapshots",Integer.class);
        mvc.perform(get("/api/v1/learning/today").cookie(learner))
                .andExpect(status().isOk()).andExpect(jsonPath("$.outcome").value("NOT_GENERATED"));
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM daily_plans",Integer.class))
                .isEqualTo(priorPlans);
        mvc.perform(post("/api/v1/learning/today/generate").cookie(learner)
                .header("Idempotency-Key","p6-first"))
                .andExpect(status().isForbidden());
        JsonNode first=json.readTree(mvc.perform(post("/api/v1/learning/today/generate")
                .cookie(learner).with(csrf()).header("Idempotency-Key","p6-first"))
                .andExpect(status().isCreated()).andExpect(jsonPath("$.outcome").value("PLANNED"))
                .andReturn().getResponse().getContentAsString());
        assertThat(first.path("items").size()).isBetween(1,3);
        int total=0;
        for(JsonNode item:first.path("items"))total+=item.path("minutes").asInt();
        assertThat(total).isLessThanOrEqualTo(60);
        String firstPlan=first.path("planId").asText();
        String firstPayload=jdbc.queryForObject("SELECT s.input_payload FROM planning_snapshots s "
                +"JOIN daily_plans p ON p.snapshot_id=s.id WHERE p.id=?",String.class,
                Long.parseLong(firstPlan));
        mvc.perform(post("/api/v1/learning/today/generate").cookie(learner)
                .with(csrf()).header("Idempotency-Key","p6-first"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.planId").value(firstPlan));
        mvc.perform(post("/api/v1/learning/today/revise").cookie(learner)
                .with(csrf()).header("Idempotency-Key","p6-first"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("IDEMPOTENCY_KEY_REUSED"));
        mvc.perform(get("/api/v1/roadmap").cookie(learner).header("Accept-Language","vi-VN"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.nodes.length()").value(17))
                .andExpect(jsonPath("$.planId").value(firstPlan))
                .andExpect(jsonPath("$.projectionAsOf")
                        .value(first.path("projectionAsOf").asText()))
                .andExpect(jsonPath("$.stale").value(false));
        JsonNode revised=json.readTree(mvc.perform(post("/api/v1/learning/today/revise")
                .cookie(learner).with(csrf()).header("Idempotency-Key","p6-revise"))
                .andExpect(status().isCreated()).andExpect(jsonPath("$.revision").value(2))
                .andReturn().getResponse().getContentAsString());
        assertThat(revised.path("planId").asText()).isNotEqualTo(firstPlan);
        mvc.perform(post("/api/v1/learning/today/revise").cookie(learner)
                .with(csrf()).header("Idempotency-Key","p6-revise"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.planId")
                        .value(revised.path("planId").asText()));
        assertThat(jdbc.queryForObject("SELECT status FROM daily_plans WHERE id=?",String.class,
                Long.parseLong(firstPlan))).isEqualTo("SUPERSEDED");
        assertThat(jdbc.queryForObject("SELECT s.input_payload FROM planning_snapshots s "
                +"JOIN daily_plans p ON p.snapshot_id=s.id WHERE p.id=?",String.class,
                Long.parseLong(firstPlan))).isEqualTo(firstPayload);
        assertThat(revised.path("projectionAsOf").asText()).isNotEqualTo("");
        mvc.perform(get("/api/v1/learning/today/plans/{id}",firstPlan).cookie(learner))
                .andExpect(status().isOk()).andExpect(jsonPath("$.revision").value(1));
        Cookie stranger=register("phase6-stranger@skillpath.local");
        mvc.perform(get("/api/v1/learning/today/plans/{id}",firstPlan).cookie(stranger))
                .andExpect(status().isNotFound());
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM planning_snapshots",Integer.class))
                .isEqualTo(priorSnapshots+2);
        String taskId=revised.path("items").get(0).path("taskId").asText();
        mvc.perform(post("/api/v1/learning/tasks/{id}/start",taskId).cookie(learner)
                .with(csrf()).header("Idempotency-Key","p6-task-start"))
                .andExpect(status().isOk());
        mvc.perform(post("/api/v1/learning/today/revise").cookie(learner)
                .with(csrf()).header("Idempotency-Key","p6-revise-blocked"))
                .andExpect(status().isConflict());
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM knowledge_evidence",Integer.class)).isZero();
    }

    @Test void manualSessionBlocksPlannerAndStoredBudgetIsHard()throws Exception{
        Cookie manual=register("phase6-manual@skillpath.local");
        createGoal(manual,"phase6-manual-goal",30);
        mvc.perform(post("/api/v1/learning/sequences/programming-flow-foundations/sessions")
                .cookie(manual).with(csrf()).header("Idempotency-Key","manual-start"))
                .andExpect(status().isCreated());
        mvc.perform(get("/api/v1/learning/today").cookie(manual))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.activeManualSessionId").isNotEmpty());
        mvc.perform(post("/api/v1/learning/today/generate").cookie(manual)
                .with(csrf()).header("Idempotency-Key","manual-conflict"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("ACTIVE_LEARNING_SESSION"));

        Cookie shortBudget=register("phase6-short@skillpath.local");
        createGoal(shortBudget,"phase6-short-goal",20);
        JsonNode plan=json.readTree(mvc.perform(post("/api/v1/learning/today/generate")
                .cookie(shortBudget).with(csrf()).header("Idempotency-Key","short-plan"))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString());
        assertThat(plan.path("items").size()).isBetween(1,3);
        int total=0;
        for(JsonNode item:plan.path("items"))total+=item.path("minutes").asInt();
        assertThat(total).isLessThanOrEqualTo(20);
    }

    @Test void concurrentGenerationConvergesOnOneCurrentPlan()throws Exception{
        Cookie learner=register("phase6-concurrent@skillpath.local");
        createGoal(learner,"phase6-concurrent-goal");
        long userId=jdbc.queryForObject("SELECT user_id FROM user_credentials "
                +"WHERE normalized_email=?",Long.class,"phase6-concurrent@skillpath.local");
        CountDownLatch start=new CountDownLatch(1);
        try(var executor=Executors.newFixedThreadPool(2)){
            var first=executor.submit(()->{
                start.await(10,TimeUnit.SECONDS);
                return planner.generate(userId,"p6-concurrent-a",SupportedLocale.ENGLISH);
            });
            var second=executor.submit(()->{
                start.await(10,TimeUnit.SECONDS);
                return planner.generate(userId,"p6-concurrent-b",SupportedLocale.ENGLISH);
            });
            start.countDown();
            var outcomes=List.of(first.get(20,TimeUnit.SECONDS),second.get(20,TimeUnit.SECONDS));
            assertThat(outcomes.get(0).plan().planId()).isEqualTo(outcomes.get(1).plan().planId());
            assertThat(outcomes.stream().filter(PlannerService.CommandOutcome::created).count())
                    .isEqualTo(1);
        }
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM daily_plans WHERE user_id=?",
                Integer.class,userId)).isEqualTo(1);
    }

    @Test void goalBudgetBelowTwentyIsRejectedAtApiBoundary()throws Exception{
        Cookie learner=register("phase6-too-short@skillpath.local");
        String body="""
                {"goalTemplateId":"1","targetDate":"%s","timezone":"Asia/Ho_Chi_Minh","defaultDailyMinutes":19}
                """.formatted(LocalDate.now().plusDays(90));
        mvc.perform(post("/api/v1/goals").cookie(learner).with(csrf())
                .header("Idempotency-Key","too-short-goal")
                .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isBadRequest());
    }

    @Test void concurrentIdenticalRevisionsCreateOneNewImmutableRevision()throws Exception{
        Cookie learner=register("phase6-concurrent-revise@skillpath.local");
        createGoal(learner,"phase6-concurrent-revise-goal");
        long userId=jdbc.queryForObject("SELECT user_id FROM user_credentials "
                +"WHERE normalized_email=?",Long.class,"phase6-concurrent-revise@skillpath.local");
        var original=planner.generate(userId,"phase6-before-revise",SupportedLocale.ENGLISH).plan();
        String payload=jdbc.queryForObject("SELECT input_payload FROM planning_snapshots WHERE "
                +"id=(SELECT snapshot_id FROM daily_plans WHERE id=?)",String.class,
                Long.parseLong(original.planId()));
        CountDownLatch start=new CountDownLatch(1);
        try(var executor=Executors.newFixedThreadPool(2)){
            var first=executor.submit(()->{
                start.await(10,TimeUnit.SECONDS);
                return planner.revise(userId,"phase6-same-revision",SupportedLocale.ENGLISH);
            });
            var second=executor.submit(()->{
                start.await(10,TimeUnit.SECONDS);
                return planner.revise(userId,"phase6-same-revision",SupportedLocale.ENGLISH);
            });
            start.countDown();
            var outcomes=List.of(first.get(20,TimeUnit.SECONDS),second.get(20,TimeUnit.SECONDS));
            assertThat(outcomes.get(0).plan().planId()).isEqualTo(outcomes.get(1).plan().planId());
            assertThat(outcomes.stream().filter(PlannerService.CommandOutcome::created).count())
                    .isEqualTo(1);
            assertThat(outcomes.get(0).plan().revision()).isEqualTo(2);
        }
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM daily_plans WHERE user_id=?",
                Integer.class,userId)).isEqualTo(2);
        assertThat(jdbc.queryForObject("SELECT input_payload FROM planning_snapshots WHERE "
                +"id=(SELECT snapshot_id FROM daily_plans WHERE id=?)",String.class,
                Long.parseLong(original.planId()))).isEqualTo(payload);
    }

    @Test void plannerItemFailureRollsBackSnapshotSessionPlanAndReceipt()throws Exception{
        Cookie learner=register("phase6-rollback@skillpath.local");
        createGoal(learner,"phase6-rollback-goal");
        long userId=jdbc.queryForObject("SELECT user_id FROM user_credentials "
                +"WHERE normalized_email=?",Long.class,"phase6-rollback@skillpath.local");
        int highestExistingTask=jdbc.queryForObject("SELECT COALESCE(MAX(learning_task_id),0) "
                +"FROM daily_plan_items",Integer.class);
        jdbc.execute("ALTER TABLE daily_plan_items ADD CONSTRAINT ck_phase6_rollback_fixture "
                +"CHECK (learning_task_id <= "+highestExistingTask+")");
        try{
            assertThatThrownBy(()->planner.generate(userId,"phase6-failed-command",
                    SupportedLocale.ENGLISH)).isInstanceOf(RuntimeException.class);
        }finally{
            jdbc.execute("ALTER TABLE daily_plan_items DROP CHECK ck_phase6_rollback_fixture");
        }
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM planning_snapshots WHERE user_id=?",
                Integer.class,userId)).isZero();
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM daily_plans WHERE user_id=?",
                Integer.class,userId)).isZero();
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM learning_sessions WHERE user_id=?",
                Integer.class,userId)).isZero();
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM planner_command_receipts WHERE user_id=?",
                Integer.class,userId)).isZero();
    }

    @Test void roadmapPagesStayOnOneSnapshotAndRejectCrossOwnerOrChangedCursor()throws Exception{
        Cookie learner=register("phase6-map-pages@skillpath.local");
        createGoal(learner,"phase6-map-goal");
        JsonNode first=json.readTree(mvc.perform(get("/api/v1/roadmap").cookie(learner)
                .param("limit","5")).andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString());
        assertThat(first.path("nodes").size()).isEqualTo(5);
        assertThat(first.path("hasMore").asBoolean()).isTrue();
        java.util.Set<String> nodeIds=new java.util.HashSet<>();
        JsonNode page=first;
        while(true){
            for(JsonNode node:page.path("nodes"))assertThat(nodeIds.add(node.path("id").asText())).isTrue();
            for(JsonNode edge:page.path("edges"))
                assertThat(page.path("nodes").findValuesAsText("id"))
                        .contains(edge.path("targetId").asText());
            if(!page.path("hasMore").asBoolean())break;
            page=json.readTree(mvc.perform(get("/api/v1/roadmap").cookie(learner)
                    .param("limit","5").param("cursor",page.path("nextCursor").asText()))
                    .andExpect(status().isOk()).andReturn().getResponse().getContentAsString());
            assertThat(page.path("graphVersionId").asText()).isEqualTo(first.path("graphVersionId").asText());
            assertThat(page.path("progressDigest").asText()).isEqualTo(first.path("progressDigest").asText());
            assertThat(page.path("reviewDigest").asText()).isEqualTo(first.path("reviewDigest").asText());
            assertThat(page.path("projectionAsOf").asText()).isEqualTo(first.path("projectionAsOf").asText());
        }
        assertThat(nodeIds).hasSize(17);
        mvc.perform(get("/api/v1/roadmap").cookie(learner).param("limit","0"))
                .andExpect(status().isBadRequest());
        mvc.perform(get("/api/v1/roadmap").cookie(learner).param("cursor","bad!"))
                .andExpect(status().isBadRequest());
        Cookie stranger=register("phase6-map-other@skillpath.local");
        createGoal(stranger,"phase6-map-other-goal");
        String cursor=first.path("nextCursor").asText();
        mvc.perform(get("/api/v1/roadmap").cookie(stranger).param("cursor",cursor))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("ROADMAP_CURSOR_STALE"));
        mvc.perform(post("/api/v1/learning/today/generate").cookie(learner)
                .with(csrf()).header("Idempotency-Key","phase6-map-now-planned"))
                .andExpect(status().isCreated());
        mvc.perform(get("/api/v1/roadmap").cookie(learner).param("cursor",cursor))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("ROADMAP_CURSOR_STALE"));
    }

    private Cookie register(String email)throws Exception{
        String body="""
                {"email":"%s","password":"CorrectHorseBattery9","displayName":"Phase Six","timezone":"Asia/Ho_Chi_Minh"}
                """.formatted(email);
        Cookie cookie=mvc.perform(post("/api/v1/auth/register").with(csrf())
                .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isCreated()).andReturn().getResponse().getCookie("SKILLPATH_SESSION");
        assertThat(cookie).isNotNull();return cookie;
    }
    private void createGoal(Cookie cookie,String key)throws Exception{
        createGoal(cookie,key,60);
    }
    private void createGoal(Cookie cookie,String key,int minutes)throws Exception{
        String body="""
                {"goalTemplateId":"1","targetDate":"%s","timezone":"Asia/Ho_Chi_Minh","defaultDailyMinutes":%d}
                """.formatted(LocalDate.now().plusDays(90),minutes);
        mvc.perform(post("/api/v1/goals").cookie(cookie).with(csrf())
                .header("Idempotency-Key",key).contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isCreated());
    }
}
