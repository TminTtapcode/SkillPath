package com.skillpath;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import jakarta.servlet.http.Cookie;
import com.skillpath.knowledge.application.KnowledgeGraphService;
import com.skillpath.shared.api.ApiException;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;
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
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class PhaseTwoFlowIT {

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
    KnowledgeGraphService knowledgeGraphService;

    @Test
    @Order(1)
    void seededPublishedGraphIsPublicBoundedAndStable() throws Exception {
        mockMvc.perform(get("/api/v1/goal-templates/1/graph").param("limit", "5"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.graphVersionId").value("1"))
                .andExpect(jsonPath("$.curriculumKey").value("JAVA_BACKEND"))
                .andExpect(jsonPath("$.nodes.length()").value(5))
                .andExpect(jsonPath("$.truncated").value(true))
                .andExpect(jsonPath("$.nextCursor").isNotEmpty());

        mockMvc.perform(get("/api/v1/knowledge/nodes/1012"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.slug").value("spring-boot"));

        mockMvc.perform(get("/api/v1/knowledge/nodes/1012/prerequisites").param("transitive", "true"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.slug == 'rest-api-design')]").exists());

        mockMvc.perform(get("/api/v1/goal-templates/1/graph").param("cursor", "not-a-versioned-cursor"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_GRAPH_CURSOR"));

        Integer nodes = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM knowledge_nodes WHERE graph_version_id = 1", Integer.class);
        Integer relations = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM knowledge_relations WHERE graph_version_id = 1", Integer.class);
        assertThat(nodes).isEqualTo(17);
        assertThat(relations).isEqualTo(23);
    }

    @Test
    @Order(2)
    void learnerCannotPublishButCuratorCanReplayPublishedVersion() throws Exception {
        mockMvc.perform(post("/api/v1/admin/knowledge/versions/1/publish").with(csrf()))
                .andExpect(status().isUnauthorized());

        Cookie learner = register("phase2-learner@skillpath.local");
        mockMvc.perform(post("/api/v1/admin/knowledge/versions/1/publish").cookie(learner).with(csrf()))
                .andExpect(status().isForbidden());

        Cookie initial = register("phase2-curator@skillpath.local");
        assertThat(initial).isNotNull();
        Long curatorId = jdbcTemplate.queryForObject(
                "SELECT user_id FROM user_credentials WHERE normalized_email = ?",
                Long.class,
                "phase2-curator@skillpath.local");
        jdbcTemplate.update("INSERT INTO user_roles (user_id, role_name) VALUES (?, 'CURATOR')", curatorId);
        Cookie curator = login("phase2-curator@skillpath.local");

        mockMvc.perform(post("/api/v1/admin/knowledge/versions/1/publish")
                        .cookie(curator)
                        .with(csrf())
                        .header("X-Correlation-Id", "phase2-replay"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("PUBLISHED"))
                .andExpect(jsonPath("$.replayed").value(true));
    }

    @Test
    @Order(3)
    void validatesPublishesAndAuditsSuccessor() throws Exception {
        long curatorId = createCurator("phase2-lifecycle@skillpath.local");
        cloneDraft(2, 10000, "1.1.0");

        Cookie curator = login("phase2-lifecycle@skillpath.local");
        mockMvc.perform(post("/api/v1/admin/knowledge/versions/2/validate").cookie(curator))
                .andExpect(status().isForbidden());

        KnowledgeGraphService.ValidationView validation =
                knowledgeGraphService.validate(2, curatorId, "phase2-validate");
        assertThat(validation.valid()).isTrue();
        assertThat(validation.status()).isEqualTo("VALIDATED");

        KnowledgeGraphService.PublicationView publication =
                knowledgeGraphService.publish(2, curatorId, "phase2-publish");
        assertThat(publication.status()).isEqualTo("PUBLISHED");
        assertThat(publication.replayed()).isFalse();
        assertThat(jdbcTemplate.queryForObject("SELECT status FROM knowledge_graph_versions WHERE id = 1", String.class))
                .isEqualTo("RETIRED");
        assertThat(jdbcTemplate.queryForObject("SELECT status FROM knowledge_graph_versions WHERE id = 2", String.class))
                .isEqualTo("PUBLISHED");
        Integer events = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM knowledge_version_events WHERE graph_version_id = 2 AND event_type IN ('VALIDATED','PUBLISHED')",
                Integer.class);
        assertThat(events).isEqualTo(2);
    }

    @Test
    @Order(4)
    void concurrentPublicationsHaveOneWinnerAndOneConflict() throws Exception {
        long curatorId = createCurator("phase2-concurrency@skillpath.local");
        cloneDraft(3, 20000, "1.2.0-a");
        cloneDraft(4, 30000, "1.2.0-b");
        knowledgeGraphService.validate(3, curatorId, "validate-3");
        knowledgeGraphService.validate(4, curatorId, "validate-4");

        CountDownLatch start = new CountDownLatch(1);
        ExecutorService executor = Executors.newFixedThreadPool(2);
        try {
            List<Future<String>> attempts = List.of(
                    executor.submit(() -> publishOutcome(3, curatorId, start)),
                    executor.submit(() -> publishOutcome(4, curatorId, start)));
            start.countDown();
            assertThat(List.of(
                            attempts.get(0).get(15, TimeUnit.SECONDS),
                            attempts.get(1).get(15, TimeUnit.SECONDS)))
                    .containsExactlyInAnyOrder("PUBLISHED", "GRAPH_PUBLICATION_CONFLICT");
        } finally {
            executor.shutdownNow();
        }
        Integer published = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM knowledge_graph_versions WHERE curriculum_key = 'JAVA_BACKEND' AND status = 'PUBLISHED'",
                Integer.class);
        Integer validated = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM knowledge_graph_versions WHERE id IN (3,4) AND status = 'VALIDATED'",
                Integer.class);
        assertThat(published).isEqualTo(1);
        assertThat(validated).isEqualTo(1);
    }

    private String publishOutcome(long versionId, long curatorId, CountDownLatch start) throws Exception {
        start.await(10, TimeUnit.SECONDS);
        try {
            return knowledgeGraphService.publish(versionId, curatorId, "concurrent-" + versionId).status();
        } catch (ApiException exception) {
            return exception.code();
        }
    }

    private long createCurator(String email) throws Exception {
        register(email);
        Long userId = jdbcTemplate.queryForObject(
                "SELECT user_id FROM user_credentials WHERE normalized_email = ?", Long.class, email);
        jdbcTemplate.update("INSERT INTO user_roles (user_id, role_name) VALUES (?, 'CURATOR')", userId);
        return userId;
    }

    private void cloneDraft(long versionId, long offset, String label) {
        jdbcTemplate.update(
                "INSERT INTO knowledge_graph_versions (id, curriculum_key, version_label, status, version, created_at, updated_at) VALUES (?, 'JAVA_BACKEND', ?, 'DRAFT', 0, UTC_TIMESTAMP(6), UTC_TIMESTAMP(6))",
                versionId,
                label);
        jdbcTemplate.update("""
                INSERT INTO knowledge_nodes (
                    id, graph_version_id, slug, name, description, category, difficulty,
                    estimated_minutes, status, metadata, created_at, updated_at)
                SELECT id + ?, ?, slug, name, description, category, difficulty,
                    estimated_minutes, status, metadata, UTC_TIMESTAMP(6), UTC_TIMESTAMP(6)
                FROM knowledge_nodes WHERE graph_version_id = 1
                """, offset, versionId);
        jdbcTemplate.update("""
                INSERT INTO knowledge_relations (
                    id, graph_version_id, source_node_id, target_node_id, relation_type,
                    strength, status, rationale, created_at, updated_at)
                SELECT id + ?, ?, source_node_id + ?, target_node_id + ?, relation_type,
                    strength, status, rationale, UTC_TIMESTAMP(6), UTC_TIMESTAMP(6)
                FROM knowledge_relations WHERE graph_version_id = 1
                """, offset, versionId, offset, offset);
        jdbcTemplate.update("""
                INSERT INTO goal_knowledge (
                    graph_version_id, goal_template_id, knowledge_node_id, relevance_weight,
                    required_mastery, is_terminal, created_at, updated_at)
                SELECT ?, goal_template_id, knowledge_node_id + ?, relevance_weight,
                    required_mastery, is_terminal, UTC_TIMESTAMP(6), UTC_TIMESTAMP(6)
                FROM goal_knowledge WHERE graph_version_id = 1
                """, versionId, offset);
    }

    private Cookie register(String email) throws Exception {
        String body = """
                {"email":"%s","password":"CorrectHorseBattery9","displayName":"Phase Two","timezone":"Asia/Bangkok"}
                """.formatted(email);
        return mockMvc.perform(post("/api/v1/auth/register")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isCreated())
                .andReturn()
                .getResponse()
                .getCookie("SKILLPATH_SESSION");
    }

    private Cookie login(String email) throws Exception {
        String body = """
                {"email":"%s","password":"CorrectHorseBattery9"}
                """.formatted(email);
        return mockMvc.perform(post("/api/v1/auth/login")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getCookie("SKILLPATH_SESSION");
    }
}
