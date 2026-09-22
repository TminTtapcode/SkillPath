package com.skillpath.goal.infrastructure.persistence;

import com.skillpath.goal.application.IdempotencyStore;
import java.sql.Timestamp;
import java.time.Instant;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

@Component
class JdbcIdempotencyStore implements IdempotencyStore {

    private final JdbcTemplate jdbcTemplate;

    JdbcIdempotencyStore(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    public Claim claim(
            long userId,
            String operation,
            String key,
            String requestHash,
            Instant now,
            Instant expiresAt) {
        int inserted = insert(userId, operation, key, requestHash, now, expiresAt);
        Record record = findForUpdate(userId, operation, key);

        if (inserted == 1) {
            return new Claim(record.id(), Claim.State.NEW, null);
        }
        if (!record.expiresAt().isAfter(now)) {
            jdbcTemplate.update("DELETE FROM idempotency_records WHERE id = ?", record.id());
            int replacement = insert(userId, operation, key, requestHash, now, expiresAt);
            if (replacement != 1) {
                throw new IllegalStateException("Could not replace expired idempotency record");
            }
            Record replacementRecord = findForUpdate(userId, operation, key);
            return new Claim(replacementRecord.id(), Claim.State.NEW, null);
        }
        if (!record.requestHash().equals(requestHash)) {
            return new Claim(record.id(), Claim.State.HASH_MISMATCH, null);
        }
        if (record.resourceId() != null && "COMPLETED".equals(record.status())) {
            return new Claim(record.id(), Claim.State.REPLAY, record.resourceId());
        }
        return new Claim(record.id(), Claim.State.IN_PROGRESS, null);
    }

    private int insert(
            long userId,
            String operation,
            String key,
            String requestHash,
            Instant now,
            Instant expiresAt) {
        return jdbcTemplate.update(
                """
                INSERT IGNORE INTO idempotency_records
                    (user_id, operation_name, idempotency_key, request_hash, outcome_status,
                     resource_id, expires_at, created_at, updated_at)
                VALUES (?, ?, ?, ?, 'IN_PROGRESS', NULL, ?, ?, ?)
                """,
                userId,
                operation,
                key,
                requestHash,
                Timestamp.from(expiresAt),
                Timestamp.from(now),
                Timestamp.from(now));
    }

    private Record findForUpdate(long userId, String operation, String key) {
        return jdbcTemplate.queryForObject(
                """
                SELECT id, request_hash, outcome_status, resource_id, expires_at
                FROM idempotency_records
                WHERE user_id = ? AND operation_name = ? AND idempotency_key = ?
                FOR UPDATE
                """,
                (resultSet, rowNumber) -> new Record(
                        resultSet.getLong("id"),
                        resultSet.getString("request_hash"),
                        resultSet.getString("outcome_status"),
                        resultSet.getObject("resource_id", Long.class),
                        resultSet.getTimestamp("expires_at").toInstant()),
                userId,
                operation,
                key);
    }

    @Override
    public void complete(long recordId, long resourceId, Instant now) {
        jdbcTemplate.update(
                """
                UPDATE idempotency_records
                SET outcome_status = 'COMPLETED', resource_id = ?, updated_at = ?
                WHERE id = ? AND outcome_status = 'IN_PROGRESS'
                """,
                resourceId,
                Timestamp.from(now),
                recordId);
    }

    private record Record(
            long id, String requestHash, String status, Long resourceId, Instant expiresAt) {}
}
