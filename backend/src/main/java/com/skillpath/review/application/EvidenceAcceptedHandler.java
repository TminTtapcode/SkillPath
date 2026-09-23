package com.skillpath.review.application;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.skillpath.review.domain.ReviewIntervalPolicyV1;
import com.skillpath.shared.application.OutboxHandler;
import java.math.BigDecimal;
import java.sql.Timestamp;
import java.time.Clock;
import java.time.Instant;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

/** Review owns the durable barrier between accepted evidence and planner input. */
@Component
public class EvidenceAcceptedHandler implements OutboxHandler {
    private static final Set<String> KINDS = Set.of("DIAGNOSTIC", "TASK_CHECK");
    private static final Set<String> STATUSES = Set.of("UNKNOWN", "LEARNING", "PROVISIONAL", "MASTERED", "REVIEW_DUE");
    private static final Set<String> DIMENSIONS = Set.of("RECOGNITION", "UNDERSTANDING");
    private final JdbcTemplate jdbc;
    private final ObjectMapper mapper;
    private final Clock clock;
    private final ReviewIntervalPolicyV1 policy = new ReviewIntervalPolicyV1();

    public EvidenceAcceptedHandler(JdbcTemplate jdbc, ObjectMapper mapper, Clock clock) {
        this.jdbc = jdbc;
        this.mapper = mapper;
        this.clock = clock;
    }

    @Override
    public boolean supports(String owner, String type, int version) {
        return owner.equals("progress") && type.equals("EvidenceAccepted") && version == 1;
    }

    @Override
    public void handle(OutboxEvent event) {
        try {
            JsonNode payload = mapper.readTree(event.payload());
            String kind = payload.path("attemptKind").asText();
            if (!KINDS.contains(kind) || !payload.path("replanEligible").isBoolean())
                throw new IllegalArgumentException("Invalid accepted attempt");
            long attempt = id(payload, "attemptId"), user = id(payload, "userId");
            long goal = id(payload, "goalId"), graph = id(payload, "graphVersionId");
            JsonNode evidence = payload.path("evidence");
            int expected = payload.path("expectedEvidenceCount").asInt(0);
            if (!evidence.isArray() || expected < 1 || expected > 20 || evidence.size() != expected)
                throw new IllegalArgumentException("Incomplete accepted attempt");
            Instant now = clock.instant();
            var prior = jdbc.query("SELECT source_event_id,user_id,graph_version_id FROM review_processed_attempts WHERE attempt_kind=? AND attempt_id=?",
                    (rs,n) -> new ProcessedAttempt(rs.getLong(1), rs.getLong(2), rs.getLong(3)), kind, attempt);
            if (!prior.isEmpty()) {
                ProcessedAttempt original = prior.getFirst();
                if (original.sourceEventId() != event.id() || original.userId() != user
                        || original.graphVersionId() != graph)
                    throw new IllegalArgumentException("Attempt event identity changed");
                return;
            }
            jdbc.update("INSERT INTO review_processed_attempts(attempt_kind,attempt_id,source_event_id,user_id,graph_version_id,processed_at) VALUES(?,?,?,?,?,?)",
                    kind, attempt, event.id(), user, graph, Timestamp.from(now));
            Map<Long, Observation> byNode = new HashMap<>();
            for (JsonNode item : evidence) {
                long node = id(item, "knowledgeNodeId");
                String dimension = item.path("dimension").asText();
                String status = item.path("status").asText();
                BigDecimal score = decimal(item, "score"), reliability = decimal(item, "reliability");
                if (!DIMENSIONS.contains(dimension) || !STATUSES.contains(status)
                        || score.signum() < 0 || score.compareTo(BigDecimal.ONE) > 0
                        || reliability.signum() < 0 || reliability.compareTo(BigDecimal.ONE) > 0)
                    throw new IllegalArgumentException("Invalid accepted evidence");
                byNode.merge(node, new Observation(status, score),
                        (oldValue, newValue) -> oldValue.score().compareTo(newValue.score()) <= 0 ? oldValue : newValue);
            }
            for (var entry : byNode.entrySet().stream().sorted(Map.Entry.comparingByKey()).toList())
                updateSchedule(user, graph, entry.getKey(), entry.getValue(), now);
            if (payload.path("replanEligible").booleanValue()) emitReady(kind, attempt, user, goal, graph, event.id(), now);
        } catch (Exception exception) {
            throw new IllegalStateException("INVALID_EVIDENCE_ACCEPTED_EVENT", exception);
        }
    }

    private void updateSchedule(long user, long graph, long node, Observation observation, Instant now) {
        var rows = jdbc.query("SELECT id,interval_index,status FROM review_schedules WHERE user_id=? AND knowledge_node_id=? FOR UPDATE",
                (rs,n) -> new ScheduleRow(rs.getLong(1), rs.getInt(2), rs.getString(3)), user, node);
        if (rows.isEmpty()) {
            if (!observation.status().equals("MASTERED")) return;
            Instant due = now.plus(policy.firstInterval());
            jdbc.update("INSERT INTO review_schedules(user_id,graph_version_id,knowledge_node_id,policy_version,interval_index,due_at,last_reviewed_at,status,version,created_at,updated_at) VALUES(?,?,? ,?,0,?,?,'SCHEDULED',0,?,?)",
                    user, graph, node, ReviewIntervalPolicyV1.VERSION, Timestamp.from(due), Timestamp.from(now),
                    Timestamp.from(now), Timestamp.from(now));
            return;
        }
        ScheduleRow row = rows.getFirst();
        if (row.status().equals("PAUSED")) return;
        var next = policy.next(row.intervalIndex(), observation.score());
        jdbc.update("UPDATE review_schedules SET interval_index=?,due_at=?,last_reviewed_at=?,status='SCHEDULED',version=version+1,updated_at=? WHERE id=?",
                next.intervalIndex(), Timestamp.from(now.plus(next.interval())), Timestamp.from(now), Timestamp.from(now), row.id());
    }

    private void emitReady(String kind, long attempt, long user, long goal, long graph,
                           long sourceEventId, Instant now) throws Exception {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("attemptKind", kind); payload.put("attemptId", Long.toString(attempt));
        payload.put("userId", Long.toString(user)); payload.put("goalId", Long.toString(goal));
        payload.put("graphVersionId", Long.toString(graph));
        payload.put("sourceEventId", Long.toString(sourceEventId));
        payload.put("reviewedAt", now.toString());
        jdbc.update("INSERT INTO outbox_events(event_key,owner_module,aggregate_type,aggregate_id,event_type,event_version,payload,status,attempt_count,available_at,occurred_at,created_at,updated_at) VALUES(?,'review','ReviewAttempt',?,'PlanningInputsReady',1,CAST(? AS JSON),'PENDING',0,?,?,?,?)",
                "planning-inputs-ready:" + kind + ":" + attempt, Long.toString(attempt), mapper.writeValueAsString(payload),
                Timestamp.from(now), Timestamp.from(now), Timestamp.from(now), Timestamp.from(now));
    }

    private static long id(JsonNode value, String field) {
        long result = Long.parseLong(value.path(field).asText());
        if (result < 1) throw new IllegalArgumentException("Invalid identifier");
        return result;
    }
    private static BigDecimal decimal(JsonNode value, String field) { return new BigDecimal(value.path(field).asText()); }
    private record Observation(String status, BigDecimal score) {}
    private record ScheduleRow(long id, int intervalIndex, String status) {}
    private record ProcessedAttempt(long sourceEventId, long userId, long graphVersionId) {}
}
