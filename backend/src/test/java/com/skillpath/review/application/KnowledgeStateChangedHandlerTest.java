package com.skillpath.review.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockingDetails;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.skillpath.shared.application.OutboxHandler.OutboxEvent;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;

class KnowledgeStateChangedHandlerTest {
    @Test
    void legacyEventChangesOnlyReviewOwnedSchedule() {
        JdbcTemplate jdbc = mock(JdbcTemplate.class);
        var handler = new KnowledgeStateChangedHandler(jdbc, new ObjectMapper(),
                Clock.fixed(Instant.parse("2026-09-23T10:00:00Z"), ZoneOffset.UTC));
        handler.handle(new OutboxEvent(1L, "state-1", "progress", "KnowledgeStateChanged", 1,
                "1001", "{\"userId\":\"1\",\"graphVersionId\":\"1\",\"knowledgeNodeId\":\"1001\",\"status\":\"MASTERED\"}",
                Instant.parse("2026-09-23T10:00:00Z")));

        var writes = mockingDetails(jdbc).getInvocations().stream()
                .filter(invocation -> invocation.getMethod().getName().equals("update"))
                .map(invocation -> invocation.getArgument(0).toString())
                .toList();
        assertThat(writes).hasSize(1);
        assertThat(writes.getFirst()).contains("INSERT INTO review_schedules")
                .doesNotContain("user_knowledge");
    }
}
