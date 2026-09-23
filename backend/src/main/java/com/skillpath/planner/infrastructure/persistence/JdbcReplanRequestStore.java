package com.skillpath.planner.infrastructure.persistence;

import com.skillpath.planner.application.ReplanExecution;
import com.skillpath.planner.application.ReplanRequestStore;
import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class JdbcReplanRequestStore implements ReplanRequestStore {
    private static final int MAX_ATTEMPTS = 10;
    private static final Duration LEASE = Duration.ofMinutes(2);
    private final JdbcTemplate jdbc;

    public JdbcReplanRequestStore(JdbcTemplate jdbc) { this.jdbc = jdbc; }

    @Override
    public Optional<Request> claim(String workerId, Instant now) {
        List<Request> rows = jdbc.query("""
                SELECT id,attempt_kind,attempt_id,source_event_id,user_id,goal_id,
                       graph_version_id,attempt_count
                FROM planner_replan_requests
                WHERE ((status='PENDING' AND available_at<=?)
                    OR (status='PROCESSING' AND locked_until<?))
                  AND attempt_count<?
                ORDER BY available_at,id LIMIT 1 FOR UPDATE SKIP LOCKED
                """, this::row, Timestamp.from(now), Timestamp.from(now), MAX_ATTEMPTS);
        if (rows.isEmpty()) return Optional.empty();
        Request request = rows.getFirst();
        if (jdbc.update("""
                UPDATE planner_replan_requests
                SET status='PROCESSING',attempt_count=attempt_count+1,
                    locked_at=?,locked_until=?,locked_by=?,updated_at=?
                WHERE id=? AND attempt_count=? AND status IN ('PENDING','PROCESSING')
                """, Timestamp.from(now), Timestamp.from(now.plus(LEASE)), workerId,
                Timestamp.from(now), request.id(), request.attemptCount()) != 1)
            throw new IllegalStateException("REPLAN_CLAIM_CHANGED");
        return Optional.of(new Request(request.id(), request.attemptKind(), request.attemptId(),
                request.sourceEventId(), request.userId(), request.goalId(),
                request.graphVersionId(), request.attemptCount() + 1));
    }

    @Override
    public Optional<Request> locked(long requestId, String workerId) {
        return jdbc.query("""
                SELECT id,attempt_kind,attempt_id,source_event_id,user_id,goal_id,
                       graph_version_id,attempt_count
                FROM planner_replan_requests
                WHERE id=? AND status='PROCESSING' AND locked_by=? FOR UPDATE
                """, this::row, requestId, workerId).stream().findFirst();
    }

    @Override
    public void complete(long requestId, String workerId, ReplanExecution.Outcome outcome, Instant now) {
        if (jdbc.update("""
                UPDATE planner_replan_requests
                SET status='COMPLETED',result_code=?,result_plan_id=?,completed_at=?,
                    locked_at=NULL,locked_until=NULL,locked_by=NULL,last_error_code=NULL,updated_at=?
                WHERE id=? AND status='PROCESSING' AND locked_by=?
                """, outcome.code().name(), outcome.planId(), Timestamp.from(now),
                Timestamp.from(now), requestId, workerId) != 1)
            throw new IllegalStateException("REPLAN_COMPLETION_CHANGED");
    }

    @Override
    public void fail(long requestId, String workerId, Instant now, String safeErrorCode) {
        List<Integer> counts = jdbc.query("""
                SELECT attempt_count FROM planner_replan_requests
                WHERE id=? AND status='PROCESSING' AND locked_by=? FOR UPDATE
                """, (rs, n) -> rs.getInt(1), requestId, workerId);
        if (counts.isEmpty()) return;
        int attempts = counts.getFirst();
        long delay = Math.min(300, 1L << Math.min(8, Math.max(0, attempts - 1)));
        String code = safeErrorCode == null || !safeErrorCode.matches("[A-Z0-9_]{1,80}")
                ? "REPLAN_EXECUTION_FAILED" : safeErrorCode;
        jdbc.update("""
                UPDATE planner_replan_requests
                SET status=?,available_at=?,last_error_code=?,
                    locked_at=NULL,locked_until=NULL,locked_by=NULL,updated_at=?
                WHERE id=? AND status='PROCESSING' AND locked_by=?
                """, attempts >= MAX_ATTEMPTS ? "FAILED" : "PENDING",
                Timestamp.from(now.plusSeconds(delay)), code, Timestamp.from(now),
                requestId, workerId);
    }

    private Request row(java.sql.ResultSet rs, int index) throws java.sql.SQLException {
        long sourceEventId = rs.getLong("source_event_id");
        Long source = rs.wasNull() ? null : sourceEventId;
        return new Request(rs.getLong("id"), rs.getString("attempt_kind"), rs.getLong("attempt_id"),
                source, rs.getLong("user_id"), rs.getLong("goal_id"),
                rs.getLong("graph_version_id"), rs.getInt("attempt_count"));
    }
}
