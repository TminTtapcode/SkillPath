package com.skillpath.planner.application;

import com.skillpath.planner.domain.PlannerPolicyV1;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

public interface PlannerStore {
    Optional<PlanRow> current(long userId,long goalId,LocalDate day);
    Optional<PlanRow> plan(long userId,long planId);
    Optional<Receipt> receipt(long userId,String key);
    long snapshot(long userId,long goalId,long graphVersionId,Instant asOf,
                  String progressDigest,String reviewDigest,String inputHash,String inputPayload,
                  int candidateCount,int limitedCount);
    long decision(long snapshotId,PlannerPolicyV1.Choice choice,List<PlannerPolicyV1.Choice> alternatives,Instant now);
    void candidates(long snapshotId,List<PlannerPolicyV1.Choice> topCandidates,
                    java.util.Map<Long,List<Long>> blockedBy);
    long createPlan(long userId,long goalId,LocalDate day,String timezone,int budget,int revision,
                    Long supersedes,long snapshotId,Long sessionId,String outcome,Instant now);
    void addItem(long planId,int position,long decisionId,long taskId,int minutes);
    void supersede(long planId);
    void addReceipt(long userId,String key,String command,String hash,long planId,Instant now);
    List<ItemRow> items(long planId);

    record PlanRow(long id,long userId,long goalId,LocalDate day,String timezone,int budget,int revision,
                   Long sessionId,String status,String outcome,long graphVersionId,Instant projectionAsOf,
                   String progressDigest,String reviewDigest,String inputPayload) {}
    record ItemRow(int position,long decisionId,long taskId,long nodeId,long templateVersionId,
                   int minutes,java.math.BigDecimal score,String reasons) {}
    record Receipt(String command,String hash,long planId) {}
}
