package com.skillpath.planner.application;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.skillpath.shared.application.OutboxHandler;
import java.sql.Timestamp;
import java.time.Clock;
import java.time.Instant;
import java.util.Set;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

/** Durable Planner-owned intake; execution occurs separately after Review commits. */
@Component
public class PlanningInputsReadyHandler implements OutboxHandler {
    private final JdbcTemplate jdbc;
    private final ObjectMapper mapper;
    private final Clock clock;

    public PlanningInputsReadyHandler(JdbcTemplate jdbc, ObjectMapper mapper, Clock clock) {
        this.jdbc = jdbc;
        this.mapper = mapper;
        this.clock = clock;
    }

    @Override
    public boolean supports(String owner, String type, int version) {
        return owner.equals("review") && type.equals("PlanningInputsReady") && version == 1;
    }

    @Override
    public void handle(OutboxEvent event) {
        try {
            JsonNode payload = mapper.readTree(event.payload());
            String kind = payload.path("attemptKind").asText();
            if (!Set.of("DIAGNOSTIC", "TASK_CHECK").contains(kind))
                throw new IllegalArgumentException("Invalid planning trigger kind");
            long attempt = id(payload, "attemptId"), user = id(payload, "userId");
            long goal = id(payload, "goalId"), graph = id(payload, "graphVersionId");
            id(payload, "sourceEventId");
            Instant.parse(payload.path("reviewedAt").asText());
            Instant now = clock.instant();
            jdbc.update("INSERT INTO planner_replan_requests(attempt_kind,attempt_id,source_event_id,user_id,goal_id,graph_version_id,status,attempt_count,available_at,created_at,updated_at) VALUES(?,?,?,?,?,?,'PENDING',0,?,?,?) ON DUPLICATE KEY UPDATE id=id",
                    kind, attempt, event.id(), user, goal, graph, Timestamp.from(now), Timestamp.from(now), Timestamp.from(now));
            Long source = jdbc.queryForObject("SELECT source_event_id FROM planner_replan_requests WHERE attempt_kind=? AND attempt_id=?",
                    Long.class, kind, attempt);
            if (source == null || source != event.id()) throw new IllegalArgumentException("Planning trigger identity changed");
        } catch (Exception exception) {
            throw new IllegalStateException("INVALID_PLANNING_INPUTS_READY_EVENT", exception);
        }
    }

    private static long id(JsonNode value, String field) {
        long result = Long.parseLong(value.path(field).asText());
        if (result < 1) throw new IllegalArgumentException("Invalid identifier");
        return result;
    }
}
