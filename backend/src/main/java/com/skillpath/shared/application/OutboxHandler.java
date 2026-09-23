package com.skillpath.shared.application;

import java.time.Instant;

public interface OutboxHandler {
    boolean supports(String ownerModule, String eventType, int eventVersion);
    void handle(OutboxEvent event);

    record OutboxEvent(long id, String eventKey, String ownerModule, String eventType,
            int eventVersion, String aggregateId, String payload, Instant occurredAt) {}
}
