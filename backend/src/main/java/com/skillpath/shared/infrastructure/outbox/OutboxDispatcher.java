package com.skillpath.shared.infrastructure.outbox;

import com.skillpath.shared.application.OutboxHandler;
import com.skillpath.shared.application.OutboxHandler.OutboxEvent;
import java.sql.Timestamp;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionTemplate;

@Component
public class OutboxDispatcher {
    private final JdbcTemplate jdbc;
    private final TransactionTemplate transactions;
    private final List<OutboxHandler> handlers;
    private final Clock clock;
    private final String worker = UUID.randomUUID().toString();
    private final boolean enabled;

    public OutboxDispatcher(JdbcTemplate jdbc, TransactionTemplate transactions, List<OutboxHandler> handlers,
            Clock clock, @Value("${skillpath.outbox.enabled:true}") boolean enabled) {
        this.jdbc = jdbc;
        this.transactions = transactions;
        this.handlers = handlers;
        this.clock = clock;
        this.enabled = enabled;
    }

    @Scheduled(fixedDelayString = "${skillpath.outbox.poll-ms:1000}")
    public void dispatch() {
        if (!enabled) return;
        for (int i = 0; i < 50; i++) {
            OutboxEvent event = transactions.execute(status -> claim());
            if (event == null) return;
            try {
                transactions.executeWithoutResult(status -> {
                    OutboxHandler handler = handlers.stream()
                            .filter(candidate -> candidate.supports(event.ownerModule(), event.eventType(), event.eventVersion()))
                            .findFirst().orElseThrow(() -> new IllegalStateException("OUTBOX_HANDLER_NOT_FOUND"));
                    handler.handle(event);
                    Instant now = clock.instant();
                    jdbc.update("UPDATE outbox_events SET status='PUBLISHED', published_at=?, locked_at=NULL, locked_until=NULL, locked_by=NULL, last_error_code=NULL, updated_at=? WHERE id=? AND status='PROCESSING' AND locked_by=?",
                            Timestamp.from(now), Timestamp.from(now), event.id(), worker);
                });
            } catch (RuntimeException exception) {
                transactions.executeWithoutResult(status -> fail(event.id(), exception));
            }
        }
    }

    private OutboxEvent claim() {
        Instant now = clock.instant();
        List<OutboxEvent> rows = jdbc.query("""
                SELECT id,event_key,owner_module,event_type,event_version,aggregate_id,payload,occurred_at
                FROM outbox_events
                WHERE ((status='PENDING' AND available_at<=?) OR (status='PROCESSING' AND locked_until<?))
                  AND attempt_count < 10
                ORDER BY occurred_at,id LIMIT 1 FOR UPDATE
                """, (rs, n) -> new OutboxEvent(rs.getLong("id"), rs.getString("event_key"),
                rs.getString("owner_module"), rs.getString("event_type"), rs.getInt("event_version"),
                rs.getString("aggregate_id"), rs.getString("payload"), rs.getTimestamp("occurred_at").toInstant()),
                Timestamp.from(now), Timestamp.from(now));
        if (rows.isEmpty()) return null;
        OutboxEvent event = rows.getFirst();
        jdbc.update("UPDATE outbox_events SET status='PROCESSING', attempt_count=attempt_count+1, locked_at=?, locked_until=?, locked_by=?, updated_at=? WHERE id=?",
                Timestamp.from(now), Timestamp.from(now.plusSeconds(30)), worker, Timestamp.from(now), event.id());
        return event;
    }

    private void fail(long id, RuntimeException exception) {
        Instant now = clock.instant();
        Integer attempts = jdbc.queryForObject("SELECT attempt_count FROM outbox_events WHERE id=?", Integer.class, id);
        int count = attempts == null ? 10 : attempts;
        long delay = Math.min(300, 1L << Math.min(8, Math.max(0, count - 1)));
        String code = exception.getMessage() == null ? exception.getClass().getSimpleName() : exception.getMessage();
        if (code.length() > 120) code = code.substring(0, 120);
        jdbc.update("UPDATE outbox_events SET status=?, available_at=?, locked_at=NULL, locked_until=NULL, locked_by=NULL, last_error_code=?, updated_at=? WHERE id=?",
                count >= 10 ? "FAILED" : "PENDING", Timestamp.from(now.plus(Duration.ofSeconds(delay))), code, Timestamp.from(now), id);
    }
}
