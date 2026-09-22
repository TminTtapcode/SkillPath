package com.skillpath;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import jakarta.servlet.http.Cookie;
import java.time.LocalDate;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import com.jayway.jsonpath.JsonPath;
import org.springframework.jdbc.core.JdbcTemplate;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

@SpringBootTest(properties = "debug=false")
@AutoConfigureMockMvc
@Testcontainers
class PhaseOneFlowIT {

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

    @Test
    void registerCreateReplayReadAndLogout() throws Exception {
        String registerBody = """
                {
                  "email": "integration@skillpath.local",
                  "password": "CorrectHorseBattery9",
                  "displayName": "Integration Learner",
                  "timezone": "Asia/Bangkok"
                }
                """;

        MvcResult registration = mockMvc.perform(post("/api/v1/auth/register")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(registerBody))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.email").value("integration@skillpath.local"))
                .andReturn();
        Cookie sessionCookie = registration.getResponse().getCookie("SKILLPATH_SESSION");
        assertThat(sessionCookie).isNotNull();

        mockMvc.perform(get("/api/v1/goal-templates"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].key").value("JAVA_BACKEND_INTERN"));

        String goalBody = """
                {
                  "goalTemplateId": "1",
                  "targetDate": "%s",
                  "timezone": "Asia/Bangkok",
                  "defaultDailyMinutes": 60
                }
                """.formatted(LocalDate.now().plusDays(90));

        MvcResult created = mockMvc.perform(post("/api/v1/goals")
                        .cookie(sessionCookie)
                        .with(csrf())
                        .header("Idempotency-Key", "integration-create-1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(goalBody))
                .andExpect(status().isCreated())
                .andReturn();
        String goalId = JsonPath.read(created.getResponse().getContentAsString(), "$.id");

        mockMvc.perform(post("/api/v1/goals")
                        .cookie(sessionCookie)
                        .with(csrf())
                        .header("Idempotency-Key", "integration-create-1")
                        .contentType(MediaType.APPLICATION_JSON)
                .content(goalBody))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(goalId));

        String differentGoalBody = """
                {
                  "goalTemplateId": "1",
                  "targetDate": "%s",
                  "timezone": "Asia/Bangkok",
                  "defaultDailyMinutes": 90
                }
                """.formatted(LocalDate.now().plusDays(90));

        mockMvc.perform(post("/api/v1/goals")
                        .cookie(sessionCookie)
                        .with(csrf())
                        .header("Idempotency-Key", "integration-create-1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(differentGoalBody))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("IDEMPOTENCY_KEY_REUSED"));

        mockMvc.perform(post("/api/v1/goals")
                        .cookie(sessionCookie)
                        .with(csrf())
                        .header("Idempotency-Key", "integration-create-2")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(differentGoalBody))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("ACTIVE_GOAL_ALREADY_EXISTS"));

        mockMvc.perform(get("/api/v1/goals/active")
                        .cookie(sessionCookie))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(goalId))
                .andExpect(jsonPath("$.status").value("ACTIVE"));

        mockMvc.perform(post("/api/v1/auth/logout")
                        .cookie(sessionCookie)
                        .with(csrf()))
                .andExpect(status().isNoContent());

        mockMvc.perform(get("/api/v1/goals/active")
                        .cookie(sessionCookie))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void rejectsStateChangeWithoutCsrf() throws Exception {
        mockMvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isForbidden());
    }

    @Test
    void rejectsDuplicateRegistrationLocksCredentialsAndRotatesSession() throws Exception {
        String registerBody = """
                {
                  "email": "security@skillpath.local",
                  "password": "CorrectHorseBattery9",
                  "displayName": "Security Learner",
                  "timezone": "Asia/Bangkok"
                }
                """;

        Cookie originalSession = mockMvc.perform(post("/api/v1/auth/register")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(registerBody))
                .andExpect(status().isCreated())
                .andReturn()
                .getResponse()
                .getCookie("SKILLPATH_SESSION");
        assertThat(originalSession).isNotNull();

        mockMvc.perform(post("/api/v1/auth/register")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(registerBody))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("EMAIL_ALREADY_REGISTERED"));

        String validLogin = """
                {"email":"security@skillpath.local","password":"CorrectHorseBattery9"}
                """;
        Cookie rotatedSession = mockMvc.perform(post("/api/v1/auth/login")
                        .cookie(originalSession)
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(validLogin))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getCookie("SKILLPATH_SESSION");
        assertThat(rotatedSession).isNotNull();
        assertThat(rotatedSession.getValue()).isNotEqualTo(originalSession.getValue());

        String invalidLogin = """
                {"email":"security@skillpath.local","password":"WrongHorseBattery99"}
                """;
        for (int attempt = 0; attempt < 5; attempt++) {
            mockMvc.perform(post("/api/v1/auth/login")
                            .with(csrf())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(invalidLogin))
                    .andExpect(status().isUnauthorized())
                    .andExpect(jsonPath("$.code").value("INVALID_CREDENTIALS"));
        }

        mockMvc.perform(post("/api/v1/auth/login")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(validLogin))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("INVALID_CREDENTIALS"));

        Integer sessionTimeout = jdbcTemplate.queryForObject(
                "SELECT MAX(MAX_INACTIVE_INTERVAL) FROM SPRING_SESSION", Integer.class);
        assertThat(sessionTimeout).isEqualTo(8 * 60 * 60);
    }

    @Test
    void expiresOldestSessionWhenConcurrentSessionLimitIsExceeded() throws Exception {
        String registerBody = """
                {
                  "email": "sessions@skillpath.local",
                  "password": "CorrectHorseBattery9",
                  "displayName": "Session Learner",
                  "timezone": "Asia/Bangkok"
                }
                """;
        Cookie oldestSession = mockMvc.perform(post("/api/v1/auth/register")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(registerBody))
                .andExpect(status().isCreated())
                .andReturn()
                .getResponse()
                .getCookie("SKILLPATH_SESSION");
        assertThat(oldestSession).isNotNull();

        String loginBody = """
                {"email":"sessions@skillpath.local","password":"CorrectHorseBattery9"}
                """;
        for (int login = 0; login < 5; login++) {
            mockMvc.perform(post("/api/v1/auth/login")
                            .with(csrf())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(loginBody))
                    .andExpect(status().isOk());
        }

        mockMvc.perform(get("/api/v1/me").cookie(oldestSession))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void concurrentIdempotentRequestsCreateExactlyOneGoal() throws Exception {
        String registerBody = """
                {
                  "email": "concurrency@skillpath.local",
                  "password": "CorrectHorseBattery9",
                  "displayName": "Concurrency Learner",
                  "timezone": "Asia/Bangkok"
                }
                """;
        Cookie sessionCookie = mockMvc.perform(post("/api/v1/auth/register")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(registerBody))
                .andExpect(status().isCreated())
                .andReturn()
                .getResponse()
                .getCookie("SKILLPATH_SESSION");
        assertThat(sessionCookie).isNotNull();

        String loginBody = """
                {"email":"concurrency@skillpath.local","password":"CorrectHorseBattery9"}
                """;
        Cookie secondSessionCookie = mockMvc.perform(post("/api/v1/auth/login")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(loginBody))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getCookie("SKILLPATH_SESSION");
        assertThat(secondSessionCookie).isNotNull();

        String goalBody = """
                {
                  "goalTemplateId": "1",
                  "targetDate": "%s",
                  "timezone": "Asia/Bangkok",
                  "defaultDailyMinutes": 60
                }
                """.formatted(LocalDate.now().plusDays(120));
        CountDownLatch start = new CountDownLatch(1);
        ExecutorService executor = Executors.newFixedThreadPool(2);
        try {
            List<Future<Integer>> requests = List.of(
                    executor.submit(() -> createGoalConcurrently(sessionCookie, goalBody, start)),
                    executor.submit(() -> createGoalConcurrently(secondSessionCookie, goalBody, start)));
            start.countDown();
            List<Integer> statuses = List.of(
                    requests.get(0).get(10, TimeUnit.SECONDS),
                    requests.get(1).get(10, TimeUnit.SECONDS));
            assertThat(statuses).containsExactlyInAnyOrder(201, 200);
        } finally {
            executor.shutdownNow();
        }

        Integer goalCount = jdbcTemplate.queryForObject(
                """
                SELECT COUNT(*) FROM user_goals
                WHERE user_id = (SELECT id FROM users WHERE display_name = 'Concurrency Learner')
                """,
                Integer.class);
        assertThat(goalCount).isEqualTo(1);
    }

    private int createGoalConcurrently(Cookie sessionCookie, String body, CountDownLatch start)
            throws Exception {
        start.await(10, TimeUnit.SECONDS);
        return mockMvc.perform(post("/api/v1/goals")
                        .cookie(sessionCookie)
                        .with(csrf())
                        .header("Idempotency-Key", "concurrent-create-1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andReturn()
                .getResponse()
                .getStatus();
    }
}
