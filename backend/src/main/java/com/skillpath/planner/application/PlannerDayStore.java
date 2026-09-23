package com.skillpath.planner.application;

import java.time.Instant;
import java.time.LocalDate;
import java.util.Optional;

/** Planner-owned local-day override audit and durable command intake. */
public interface PlannerDayStore {
    Optional<OverrideRow> latestOverride(long userId,long goalId,LocalDate day);
    Optional<OverrideRow> overrideByKey(long userId,String key);
    long addOverride(long userId,long goalId,LocalDate day,int revision,int minutes,
                     Long supersedesId,String key,String hash,Instant now);
    void enqueueOverride(long overrideId,long userId,long goalId,long graphVersionId,Instant now);
    Optional<RequestState> latestRequest(long userId,long goalId,Instant from,Instant until);

    record OverrideRow(long id,long userId,long goalId,LocalDate day,int revision,
                       int minutes,String key,String hash) {}
    record RequestState(long id,String triggerKind,String status,int attempts,String resultCode,
                        String errorCode,Instant createdAt) {}
}
