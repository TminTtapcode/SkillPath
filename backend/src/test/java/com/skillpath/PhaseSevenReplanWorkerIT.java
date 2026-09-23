package com.skillpath;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.skillpath.goal.application.GoalQueries;
import com.skillpath.planner.application.PlannerReplanWorker;
import com.skillpath.planner.application.ReplanExecution;
import com.skillpath.planner.application.ReplanRequestStore;
import com.skillpath.planner.application.PlanningInputsReadyHandler;
import com.skillpath.shared.application.OutboxHandler.OutboxEvent;
import jakarta.servlet.http.Cookie;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationContext;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.support.TransactionTemplate;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

@SpringBootTest(properties={"debug=false","skillpath.outbox.enabled=false","skillpath.phase7.replan-worker-enabled=false"})
@AutoConfigureMockMvc
@Testcontainers
class PhaseSevenReplanWorkerIT {
    @Container static final MySQLContainer<?> MYSQL=new MySQLContainer<>("mysql:8.4")
            .withDatabaseName("skillpath").withUsername("skillpath").withPassword("integration-only");
    @DynamicPropertySource static void database(DynamicPropertyRegistry registry){
        registry.add("spring.datasource.url",MYSQL::getJdbcUrl);
        registry.add("spring.datasource.username",MYSQL::getUsername);
        registry.add("spring.datasource.password",MYSQL::getPassword);
    }
    @Autowired MockMvc mvc;
    @Autowired JdbcTemplate jdbc;
    @Autowired ReplanRequestStore requests;
    @Autowired PlanningInputsReadyHandler intake;
    @Autowired GoalQueries goals;
    @Autowired TransactionTemplate transactions;
    @Autowired Clock clock;
    @Autowired ApplicationContext context;

    @Test void leaseRetryRecoveryDeadLetterGoalSerializationAndGate()throws Exception{
        assertThat(context.getBeansOfType(PlannerReplanWorker.class)).isEmpty();
        long user=createLearner("p7-worker@skillpath.local");
        long goal=jdbc.queryForObject("SELECT id FROM user_goals WHERE user_id=?",Long.class,user);
        long attempt=710001L;
        OutboxEvent ready=ready(810001L,attempt,user,goal);
        transactions.executeWithoutResult(status->{intake.handle(ready);status.setRollbackOnly();});
        assertThat(count(attempt)).isZero();
        intake.handle(ready);
        intake.handle(ready);
        assertThat(count(attempt)).isEqualTo(1);

        AtomicInteger calls=new AtomicInteger();
        ReplanExecution transientExecution=request->{
            if(calls.incrementAndGet()==1){
                jdbc.update("UPDATE planner_replan_requests SET result_code='UNCOMMITTED' WHERE id=?",request.id());
                throw new IllegalStateException("private failure detail must not persist");
            }
            return new ReplanExecution.Outcome(ReplanExecution.ResultCode.NO_CURRENT_PLAN,null);
        };
        var worker=new PlannerReplanWorker(requests,transientExecution,goals,transactions,clock);
        assertThat(worker.processOne()).isTrue();
        assertThat(requestStatus(attempt)).isEqualTo("PENDING");
        assertThat(jdbc.queryForObject("SELECT result_code FROM planner_replan_requests WHERE attempt_id=?",
                String.class,attempt)).isNull();
        assertThat(jdbc.queryForObject("SELECT last_error_code FROM planner_replan_requests WHERE attempt_id=?",
                String.class,attempt)).isEqualTo("REPLAN_EXECUTION_FAILED");
        assertThat(jdbc.queryForObject("SELECT attempt_count FROM planner_replan_requests WHERE attempt_id=?",
                Integer.class,attempt)).isEqualTo(1);
        assertThat(worker.processOne()).isFalse();
        due(attempt);
        assertThat(worker.processOne()).isTrue();
        assertThat(requestStatus(attempt)).isEqualTo("COMPLETED");
        assertThat(jdbc.queryForObject("SELECT result_code FROM planner_replan_requests WHERE attempt_id=?",
                String.class,attempt)).isEqualTo("NO_CURRENT_PLAN");
        assertThat(calls).hasValue(2);
        assertThat(worker.processOne()).isFalse();
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM daily_plans WHERE user_id=?",
                Integer.class,user)).isZero();

        long abandoned=710002L;
        intake.handle(ready(810002L,abandoned,user,goal));
        jdbc.update("UPDATE planner_replan_requests SET status='PROCESSING',attempt_count=1,"
                +"locked_at=DATE_SUB(UTC_TIMESTAMP(6),INTERVAL 5 MINUTE),"
                +"locked_until=DATE_SUB(UTC_TIMESTAMP(6),INTERVAL 3 MINUTE),locked_by='crashed'"
                +" WHERE attempt_id=?",abandoned);
        assertThat(worker.processOne()).isTrue();
        assertThat(requestStatus(abandoned)).isEqualTo("COMPLETED");
        assertThat(jdbc.queryForObject("SELECT attempt_count FROM planner_replan_requests WHERE attempt_id=?",
                Integer.class,abandoned)).isEqualTo(2);

        long poisoned=710003L;
        intake.handle(ready(810003L,poisoned,user,goal));
        jdbc.update("UPDATE planner_replan_requests SET attempt_count=9 WHERE attempt_id=?",poisoned);
        var failing=new PlannerReplanWorker(requests,request->{throw new IllegalStateException("secret");},
                goals,transactions,clock);
        assertThat(failing.processOne()).isTrue();
        assertThat(requestStatus(poisoned)).isEqualTo("FAILED");
        assertThat(jdbc.queryForObject("SELECT attempt_count FROM planner_replan_requests WHERE attempt_id=?",
                Integer.class,poisoned)).isEqualTo(10);
        assertThat(failing.processOne()).isFalse();

        AtomicInteger inFlight=new AtomicInteger(),maximum=new AtomicInteger(),completed=new AtomicInteger();
        ReplanExecution serialized=request->{
            int active=inFlight.incrementAndGet();
            maximum.accumulateAndGet(active,Math::max);
            try{Thread.sleep(150);}catch(InterruptedException exception){
                Thread.currentThread().interrupt();throw new IllegalStateException(exception);
            }finally{inFlight.decrementAndGet();}
            completed.incrementAndGet();
            return new ReplanExecution.Outcome(ReplanExecution.ResultCode.NO_CURRENT_PLAN,null);
        };
        intake.handle(ready(810004L,710004L,user,goal));
        intake.handle(ready(810005L,710005L,user,goal));
        assertThat(requestStatus(710004L)).isEqualTo("PENDING");
        assertThat(requestStatus(710005L)).isEqualTo("PENDING");
        var first=new PlannerReplanWorker(requests,serialized,goals,transactions,clock);
        var second=new PlannerReplanWorker(requests,serialized,goals,transactions,clock);
        CountDownLatch start=new CountDownLatch(1);
        try(var pool=Executors.newFixedThreadPool(2)){
            var one=pool.submit(()->{start.await();return processEventually(first);});
            var two=pool.submit(()->{start.await();return processEventually(second);});
            start.countDown();
            assertThat(one.get(15,TimeUnit.SECONDS)).isTrue();
            assertThat(two.get(15,TimeUnit.SECONDS)).isTrue();
        }
        assertThat(completed).hasValue(2);
        assertThat(maximum).hasValue(1);
        assertThat(requestStatus(710004L)).isEqualTo("COMPLETED");
        assertThat(requestStatus(710005L)).isEqualTo("COMPLETED");

        long other=createLearner("p7-worker-other@skillpath.local");
        long otherGoal=jdbc.queryForObject("SELECT id FROM user_goals WHERE user_id=?",Long.class,other);
        intake.handle(ready(810006L,710006L,user,otherGoal));
        assertThat(first.processOne()).isTrue();
        assertThat(requestStatus(710006L)).isEqualTo("COMPLETED");
        assertThat(jdbc.queryForObject("SELECT result_code FROM planner_replan_requests WHERE attempt_id=?",
                String.class,710006L)).isEqualTo("STALE_GOAL");
        assertThat(completed).hasValue(2);
    }

    private long createLearner(String email)throws Exception{
        Cookie cookie=mvc.perform(post("/api/v1/auth/register").with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"email\":\"%s\",\"password\":\"CorrectHorseBattery9\",\"displayName\":\"P7\",\"timezone\":\"Asia/Ho_Chi_Minh\"}"
                        .formatted(email))).andExpect(status().isCreated())
                .andReturn().getResponse().getCookie("SKILLPATH_SESSION");
        String goal="{\"goalTemplateId\":\"1\",\"targetDate\":\"%s\",\"timezone\":\"Asia/Ho_Chi_Minh\",\"defaultDailyMinutes\":60}"
                .formatted(LocalDate.now().plusDays(90));
        mvc.perform(post("/api/v1/goals").cookie(cookie).with(csrf())
                .header("Idempotency-Key","goal-"+email.replace("@skillpath.local",""))
                .contentType(MediaType.APPLICATION_JSON)
                .content(goal)).andExpect(status().isCreated());
        return jdbc.queryForObject("SELECT user_id FROM user_credentials WHERE normalized_email=?",
                Long.class,email);
    }

    private OutboxEvent ready(long eventId,long attempt,long user,long goal){
        Instant now=clock.instant();
        String payload="{\"attemptKind\":\"TASK_CHECK\",\"attemptId\":\"%d\",\"userId\":\"%d\",\"goalId\":\"%d\",\"graphVersionId\":\"1\",\"sourceEventId\":\"%d\",\"reviewedAt\":\"%s\"}"
                .formatted(attempt,user,goal,eventId-1,now);
        return new OutboxEvent(eventId,"ready-"+attempt,"review","PlanningInputsReady",1,
                Long.toString(attempt),payload,now);
    }
    private int count(long attempt){return jdbc.queryForObject("SELECT COUNT(*) FROM planner_replan_requests WHERE attempt_id=?",
            Integer.class,attempt);}
    private String requestStatus(long attempt){return jdbc.queryForObject("SELECT status FROM planner_replan_requests WHERE attempt_id=?",
            String.class,attempt);}
    private void due(long attempt){jdbc.update("UPDATE planner_replan_requests SET available_at=DATE_SUB(UTC_TIMESTAMP(6),INTERVAL 1 SECOND) WHERE attempt_id=?",attempt);}
    private static boolean processEventually(PlannerReplanWorker worker)throws InterruptedException{
        for(int retry=0;retry<100;retry++){
            if(worker.processOne())return true;
            Thread.sleep(10);
        }
        return false;
    }
}
