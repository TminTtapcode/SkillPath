package com.skillpath.progress.domain;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Duration;
import java.time.Instant;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

public final class KnowledgeStatePolicyV1 {
    public static final String VERSION = "knowledge-state-v1";
    private static final BigDecimal ZERO = scaled(BigDecimal.ZERO);
    private static final Map<Dimension, BigDecimal> WEIGHTS = Map.of(
            Dimension.RECOGNITION, new BigDecimal("0.15"),
            Dimension.UNDERSTANDING, new BigDecimal("0.25"),
            Dimension.RECALL, new BigDecimal("0.25"),
            Dimension.APPLICATION, new BigDecimal("0.35"));
    private static final Map<Dimension, Long> HALF_LIFE_DAYS = Map.of(
            Dimension.RECOGNITION, 120L,
            Dimension.UNDERSTANDING, 150L,
            Dimension.RECALL, 45L,
            Dimension.APPLICATION, 180L);

    public Projection project(List<Evidence> orderedEvidence, Instant asOf) {
        EnumMap<Dimension, BigDecimal> scores = new EnumMap<>(Dimension.class);
        EnumMap<Dimension, Integer> counts = new EnumMap<>(Dimension.class);
        for (Dimension dimension : Dimension.values()) {
            scores.put(dimension, ZERO);
            counts.put(dimension, 0);
        }
        Instant first = null;
        Instant last = null;
        for (Evidence evidence : orderedEvidence) {
            BigDecimal alpha = clamp(new BigDecimal("0.10")
                    .add(evidence.reliability().multiply(new BigDecimal("0.35"))),
                    new BigDecimal("0.10"), new BigDecimal("0.45"));
            BigDecimal current = scores.get(evidence.dimension());
            BigDecimal updated = current.add(alpha.multiply(evidence.score().subtract(current)));
            scores.put(evidence.dimension(), clamp01(updated));
            counts.compute(evidence.dimension(), (key, value) -> value + 1);
            if (first == null || evidence.observedAt().isBefore(first)) first = evidence.observedAt();
            if (last == null || evidence.observedAt().isAfter(last)) last = evidence.observedAt();
        }
        BigDecimal storedMastery = mastery(scores);
        BigDecimal confidence = confidence(orderedEvidence, counts, asOf);
        KnowledgeStatus acquisition = status(storedMastery, confidence);
        EnumMap<Dimension, BigDecimal> effective = new EnumMap<>(Dimension.class);
        for (Dimension dimension : Dimension.values()) {
            long ageDays = last == null ? 0 : Math.max(0, Duration.between(last, asOf).toDays());
            double factor = StrictMath.exp(-StrictMath.log(2.0) * ageDays / HALF_LIFE_DAYS.get(dimension));
            effective.put(dimension, scaled(scores.get(dimension).multiply(BigDecimal.valueOf(factor))));
        }
        BigDecimal effectiveMastery = mastery(effective);
        KnowledgeStatus effectiveStatus = acquisition == KnowledgeStatus.MASTERED
                        && effectiveMastery.compareTo(new BigDecimal("0.80")) < 0
                ? KnowledgeStatus.REVIEW_DUE
                : status(effectiveMastery, confidence);
        return new Projection(Map.copyOf(scores), storedMastery, effectiveMastery, confidence,
                orderedEvidence.size(), first, last, acquisition, effectiveStatus);
    }

    private BigDecimal confidence(List<Evidence> evidence, Map<Dimension, Integer> counts, Instant asOf) {
        if (evidence.isEmpty()) return ZERO;
        BigDecimal volume = BigDecimal.valueOf(Math.min(1.0, evidence.size() / 6.0));
        long covered = counts.values().stream().filter(count -> count > 0).count();
        BigDecimal coverage = BigDecimal.valueOf(covered / 4.0);
        Instant latest = evidence.stream().map(Evidence::observedAt).max(Instant::compareTo).orElse(asOf);
        double recency = Math.max(0.0, 1.0 - Math.max(0, Duration.between(latest, asOf).toDays()) / 365.0);
        return clamp01(volume.multiply(new BigDecimal("0.50"))
                .add(coverage.multiply(new BigDecimal("0.30")))
                .add(BigDecimal.valueOf(recency).multiply(new BigDecimal("0.20"))));
    }

    private BigDecimal mastery(Map<Dimension, BigDecimal> scores) {
        BigDecimal total = BigDecimal.ZERO;
        for (Dimension dimension : Dimension.values()) total = total.add(scores.get(dimension).multiply(WEIGHTS.get(dimension)));
        return clamp01(total);
    }

    private KnowledgeStatus status(BigDecimal mastery, BigDecimal confidence) {
        if (confidence.compareTo(new BigDecimal("0.15")) < 0) return KnowledgeStatus.UNKNOWN;
        if (mastery.compareTo(new BigDecimal("0.80")) >= 0 && confidence.compareTo(new BigDecimal("0.60")) >= 0) return KnowledgeStatus.MASTERED;
        if (mastery.compareTo(new BigDecimal("0.55")) >= 0) return KnowledgeStatus.PROVISIONAL;
        return KnowledgeStatus.LEARNING;
    }

    private static BigDecimal clamp01(BigDecimal value) { return scaled(clamp(value, BigDecimal.ZERO, BigDecimal.ONE)); }
    private static BigDecimal clamp(BigDecimal value, BigDecimal min, BigDecimal max) { return value.max(min).min(max); }
    private static BigDecimal scaled(BigDecimal value) { return value.setScale(4, RoundingMode.HALF_UP); }

    public enum Dimension { RECOGNITION, UNDERSTANDING, RECALL, APPLICATION }
    public record Evidence(Dimension dimension, BigDecimal score, BigDecimal reliability, Instant observedAt, long sourceEventId) {}
    public record Projection(Map<Dimension, BigDecimal> dimensions, BigDecimal mastery, BigDecimal effectiveMastery,
            BigDecimal confidence, int evidenceCount, Instant firstEvidenceAt, Instant lastEvidenceAt,
            KnowledgeStatus acquisitionStatus, KnowledgeStatus effectiveStatus) {}
}
