package com.skillpath.progress.application;

import com.skillpath.progress.domain.KnowledgeStatePolicyV1;
import com.skillpath.progress.domain.KnowledgeStatePolicyV1.Evidence;
import com.skillpath.progress.domain.KnowledgeStatePolicyV1.Projection;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

public interface ProgressStore {
    void lockProjection(long userId, long graphVersionId, long nodeId, Instant now);
    boolean append(SourceEvidence evidence, Instant ingestedAt);
    List<Evidence> evidence(long userId, long nodeId);
    List<Evidence> evidenceForGraph(long userId, long graphVersionId, long nodeId, Instant asOf);
    boolean hasEvidenceOutsideGraph(long userId, long graphVersionId, long nodeId);
    StateSnapshot saveProjection(long userId, long graphVersionId, long nodeId, Projection projection, Instant projectedAt);
    List<StateRow> states(long userId, int limit, long afterId);
    StateRow state(long userId, long nodeId);
    List<EvidenceRow> evidencePage(long userId, long nodeId, int limit, long afterId);
    int rebuildAll(Instant asOf, KnowledgeStatePolicyV1 policy);

    record SourceEvidence(long sourceEventId, String sourceEventKey, String sourceType, String sourceId,
            long userId, long goalId, long graphVersionId, long nodeId,
            KnowledgeStatePolicyV1.Dimension dimension, BigDecimal score, BigDecimal reliability,
            String sourcePolicyVersion, Instant observedAt) {}
    record StateSnapshot(String priorAcquisitionStatus, String acquisitionStatus, BigDecimal priorMastery, BigDecimal mastery) {}
    record StateRow(long id, long graphVersionId, long nodeId, BigDecimal recognition, BigDecimal understanding,
            BigDecimal recall, BigDecimal application, BigDecimal mastery, BigDecimal confidence,
            int evidenceCount, Instant lastEvidenceAt, Instant nextReviewAt, String status, String policyVersion) {}
    record EvidenceRow(long id, String sourceType, String sourceId, String dimension, BigDecimal score,
            BigDecimal reliability, String policyVersion, Instant observedAt) {}
}
