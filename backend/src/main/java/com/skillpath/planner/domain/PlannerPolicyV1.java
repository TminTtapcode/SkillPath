package com.skillpath.planner.domain;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** Pure, deterministic policy. All inputs are pinned by the application snapshot. */
public final class PlannerPolicyV1 {
    public static final String VERSION = "planner-v1";
    private static final BigDecimal ZERO = BigDecimal.ZERO.setScale(4);
    private static final BigDecimal ONE = BigDecimal.ONE.setScale(4);
    private static final BigDecimal THRESHOLD = new BigDecimal("0.7500");
    private static final BigDecimal HARD = new BigDecimal("0.8000");
    private static final Map<String, BigDecimal> PRIORS = Map.of(
            "LEARN", new BigDecimal("0.30"), "PRACTICE", new BigDecimal("0.35"),
            "RECALL", new BigDecimal("0.20"));

    public Result plan(Input input) {
        if (input.availableMinutes() < 1 || input.availableMinutes() > 360
                || input.nodes().size() > 200 || input.projectionAsOf() == null)
            throw new IllegalArgumentException("Invalid planner input bounds");
        Map<Long, Node> nodes = new HashMap<>();
        for (Node node : input.nodes()) nodes.put(node.id(), node);
        Map<Long, State> states = new HashMap<>();
        for (State state : input.states()) states.put(state.nodeId(), state);
        Map<Long, Due> due = new HashMap<>();
        for (Due review : input.due()) due.put(review.nodeId(), review);
        Map<Long, List<Variant>> variants = new HashMap<>();
        for (Variant variant : input.variants()) {
            if (PRIORS.containsKey(variant.activityType()) && variant.minutes() > 0)
                variants.computeIfAbsent(variant.nodeId(), ignored -> new ArrayList<>()).add(variant);
        }
        Map<Long, List<Long>> blockers = new HashMap<>();
        Map<Long, BigDecimal> unlock = new HashMap<>();
        for (Edge edge : input.edges()) {
            if (!edge.type().equals("PREREQUISITE") || edge.strength().compareTo(HARD) < 0
                    || !nodes.containsKey(edge.sourceId()) || !nodes.containsKey(edge.targetId())) continue;
            Node target = nodes.get(edge.targetId());
            boolean sourceBlocks=mastery(states.get(edge.sourceId())).compareTo(THRESHOLD) < 0;
            if (sourceBlocks)
                blockers.computeIfAbsent(edge.targetId(), ignored -> new ArrayList<>()).add(edge.sourceId());
            if (sourceBlocks
                    && mastery(states.get(edge.targetId())).compareTo(target.requiredMastery()) < 0)
                unlock.merge(edge.sourceId(), target.relevance(), BigDecimal::add);
        }
        BigDecimal maxUnlock = unlock.values().stream().max(BigDecimal::compareTo).orElse(ZERO);
        List<Node> candidates = input.nodes().stream()
                .filter(node -> node.status().equals("ACTIVE"))
                .filter(node -> variants.containsKey(node.id()))
                .filter(node -> mastery(states.get(node.id())).compareTo(node.requiredMastery()) < 0
                        || due.containsKey(node.id()) || unlock.containsKey(node.id()))
                .sorted(Comparator.<Node>comparingInt(node -> due.containsKey(node.id()) ? 0 : 1)
                        .thenComparing(Node::relevance, Comparator.reverseOrder())
                        .thenComparing(node -> unlock.getOrDefault(node.id(), ZERO), Comparator.reverseOrder())
                        .thenComparingInt(Node::topologicalOrder).thenComparingLong(Node::id)).toList();
        List<Node> limited = candidates.subList(0, Math.min(100, candidates.size()));
        Map<Long, List<Long>> immutableBlockers = new HashMap<>();
        blockers.forEach((key,value) -> immutableBlockers.put(key,value.stream().sorted().toList()));
        List<Choice> choices = new ArrayList<>();
        boolean hadOversizedVariant = false;
        for (Node node : limited) {
            if (!blockers.getOrDefault(node.id(),List.of()).isEmpty()) continue;
            BigDecimal gap = node.requiredMastery().signum() == 0 ? ZERO : clamp(
                    node.requiredMastery().subtract(mastery(states.get(node.id())))
                            .divide(node.requiredMastery(),4,RoundingMode.HALF_UP));
            BigDecimal prereq = maxUnlock.signum() == 0 ? ZERO : clamp(
                    unlock.getOrDefault(node.id(),ZERO).divide(maxUnlock,4,RoundingMode.HALF_UP));
            long daysOverdue = due.containsKey(node.id()) ? Math.max(0,
                    Duration.between(due.get(node.id()).dueAt(),input.projectionAsOf()).toDays()) : 0;
            BigDecimal urgency = clamp(BigDecimal.valueOf(daysOverdue)
                    .divide(new BigDecimal("30"),4,RoundingMode.HALF_UP));
            for (Variant variant : variants.get(node.id())) {
                if (variant.minutes() > input.availableMinutes()) {
                    hadOversizedVariant = true;
                    continue;
                }
                if (due.containsKey(node.id()) && !variant.activityType().equals("RECALL")) continue;
                if (states.getOrDefault(node.id(),unknown(node.id())).evidenceCount() == 0
                        && !variant.activityType().equals("LEARN")) continue;
                BigDecimal fit = variant.minutes() <= input.availableMinutes() ? ONE :
                        BigDecimal.valueOf(input.availableMinutes()).divide(BigDecimal.valueOf(variant.minutes()),4,RoundingMode.HALF_UP);
                BigDecimal gain = gap.multiply(PRIORS.get(variant.activityType()))
                        .multiply(BigDecimal.valueOf(6L-variant.difficulty()))
                        .divide(new BigDecimal("5"),4,RoundingMode.HALF_UP);
                BigDecimal unlockValue = new BigDecimal("0.5").add(prereq.multiply(new BigDecimal("0.5")));
                BigDecimal effort = BigDecimal.valueOf(variant.minutes())
                        .divide(BigDecimal.valueOf(input.availableMinutes()),4,RoundingMode.HALF_UP)
                        .max(new BigDecimal("0.1"));
                BigDecimal roi = clamp(gain.multiply(unlockValue).divide(effort,4,RoundingMode.HALF_UP));
                BigDecimal score = priority(gap,node.relevance(),prereq,roi,fit,urgency,
                        daysOverdue > 14);
                List<String> reasons = new ArrayList<>();
                if (gap.signum() > 0) reasons.add("GOAL_RELEVANT_GAP");
                if (prereq.signum() > 0) reasons.add("UNLOCKS_DEPENDENCIES");
                if (due.containsKey(node.id())) reasons.add("REVIEW_DUE");
                reasons.add("TIME_FIT");
                choices.add(new Choice(node.id(),variant,score,gap,node.relevance(),prereq,roi,fit,
                        urgency,List.copyOf(reasons)));
            }
        }
        choices.sort(PlannerPolicyV1::compareChoices);
        List<Choice> selected = new ArrayList<>();
        Set<Long> used = new HashSet<>();
        int remaining = input.availableMinutes();
        int reviewBudget = Math.min(input.availableMinutes(), 180);
        int reviewConsumed = 0;
        while(selected.size()<3){
            int available=remaining;
            boolean reviewPriority = reviewConsumed < reviewBudget && choices.stream()
                    .anyMatch(c -> c.reasons().contains("REVIEW_DUE") 
                            && c.variant().minutes() <= available 
                            && !used.contains(c.variant().templateVersionId()));
            
            Choice choice=choices.stream()
                    .filter(candidate->candidate.variant().minutes()<=available)
                    .filter(candidate->!used.contains(candidate.variant().templateVersionId()))
                    .filter(candidate-> !reviewPriority || candidate.reasons().contains("REVIEW_DUE"))
                    .map(candidate->rescore(candidate,available,due,input.projectionAsOf()))
                    .sorted(PlannerPolicyV1::compareChoices).findFirst().orElse(null);
            if(choice==null)break;
            selected.add(choice);
            used.add(choice.variant().templateVersionId());
            remaining-=choice.variant().minutes();
            if(choice.reasons().contains("REVIEW_DUE")) {
                reviewConsumed += choice.variant().minutes();
            }
        }
        boolean allMastered = input.nodes().stream().filter(node -> node.status().equals("ACTIVE"))
                .allMatch(node -> mastery(states.get(node.id())).compareTo(node.requiredMastery()) >= 0);
        String outcome = !selected.isEmpty() ? "PLANNED" : allMastered ?
                "GOAL_COMPLETION_CANDIDATE" : !choices.isEmpty() ?
                "NO_TIME_FIT_VARIANT" : hadOversizedVariant ?
                "NO_TIME_FIT_VARIANT" : "NO_SAFE_RECOMMENDATION";
        String reasonCode="NO_SAFE_RECOMMENDATION".equals(outcome)
                ? candidates.isEmpty()?"NO_CONTENT":"NO_ELIGIBLE_VARIANT"
                : "NO_TIME_FIT_VARIANT".equals(outcome)?"NO_TIME_FIT_VARIANT":null;
        List<Choice> alternatives=choices.stream()
                .filter(choice->selected.stream().noneMatch(picked->picked.variant().templateVersionId()
                        ==choice.variant().templateVersionId()))
                .limit(3).toList();
        return new Result(List.copyOf(selected),alternatives,choices.stream().limit(3).toList(),
                Map.copyOf(immutableBlockers),candidates.size(),limited.size(),remaining,outcome,reasonCode);
    }

    private static BigDecimal mastery(State state) { return state == null ? ZERO : state.effectiveMastery(); }
    private static State unknown(long id) { return new State(id,ZERO,ZERO,0,"UNKNOWN"); }
    private static BigDecimal clamp(BigDecimal value) { return value.max(ZERO).min(ONE).setScale(4,RoundingMode.HALF_UP); }
    private static Choice rescore(Choice choice,int minutes,Map<Long,Due> due,Instant asOf){
        BigDecimal gain=choice.gap().multiply(PRIORS.get(choice.variant().activityType()))
                .multiply(BigDecimal.valueOf(6L-choice.variant().difficulty()))
                .divide(new BigDecimal("5"),4,RoundingMode.HALF_UP);
        BigDecimal unlockValue=new BigDecimal("0.5").add(choice.prerequisiteValue()
                .multiply(new BigDecimal("0.5")));
        BigDecimal effort=BigDecimal.valueOf(choice.variant().minutes())
                .divide(BigDecimal.valueOf(minutes),4,RoundingMode.HALF_UP)
                .max(new BigDecimal("0.1"));
        BigDecimal roi=clamp(gain.multiply(unlockValue).divide(effort,4,RoundingMode.HALF_UP));
        Due review=due.get(choice.nodeId());
        boolean longOverdue=review!=null && Duration.between(review.dueAt(),asOf).toDays()>14;
        BigDecimal score=priority(choice.gap(),choice.relevance(),choice.prerequisiteValue(),
                roi,ONE,choice.reviewUrgency(),longOverdue);
        return new Choice(choice.nodeId(),choice.variant(),score,choice.gap(),choice.relevance(),
                choice.prerequisiteValue(),roi,ONE,choice.reviewUrgency(),choice.reasons());
    }
    private static BigDecimal priority(BigDecimal gap,BigDecimal relevance,BigDecimal prereq,
            BigDecimal roi,BigDecimal fit,BigDecimal urgency,boolean overdue){
        BigDecimal base=gap.multiply(new BigDecimal("0.28"))
                .add(relevance.multiply(new BigDecimal("0.22")))
                .add(prereq.multiply(new BigDecimal("0.18")))
                .add(roi.multiply(new BigDecimal("0.12")))
                .add(fit.multiply(new BigDecimal("0.08")))
                .add(urgency.multiply(new BigDecimal("0.08")));
        return base.multiply(new BigDecimal("100"))
                .add(overdue?new BigDecimal("10"):BigDecimal.ZERO)
                .min(new BigDecimal("100")).setScale(2,RoundingMode.HALF_UP);
    }
    private static int compareChoices(Choice left,Choice right){
        BigDecimal delta=left.score().subtract(right.score()).abs();
        if(delta.compareTo(new BigDecimal("0.01"))>=0)return right.score().compareTo(left.score());
        int comparison=right.reviewUrgency().compareTo(left.reviewUrgency());
        if(comparison!=0)return comparison;
        comparison=right.prerequisiteValue().compareTo(left.prerequisiteValue());
        if(comparison!=0)return comparison;
        comparison=right.timeFit().compareTo(left.timeFit());
        if(comparison!=0)return comparison;
        comparison=Integer.compare(left.variant().difficulty(),right.variant().difficulty());
        return comparison!=0?comparison:Long.compare(left.variant().templateVersionId(),
                right.variant().templateVersionId());
    }

    public record Node(long id,String status,BigDecimal relevance,BigDecimal requiredMastery,int topologicalOrder) {}
    public record Edge(long sourceId,long targetId,String type,BigDecimal strength) {}
    public record State(long nodeId,BigDecimal effectiveMastery,BigDecimal confidence,int evidenceCount,String status) {}
    public record Due(long nodeId,Instant dueAt) {}
    public record Variant(long templateVersionId,long nodeId,String activityType,int difficulty,int minutes) {}
    public record Input(Instant projectionAsOf,int availableMinutes,List<Node> nodes,List<Edge> edges,
                        List<State> states,List<Due> due,List<Variant> variants) {}
    public record Choice(long nodeId,Variant variant,BigDecimal score,BigDecimal gap,BigDecimal relevance,
                         BigDecimal prerequisiteValue,BigDecimal learningRoi,BigDecimal timeFit,
                         BigDecimal reviewUrgency,List<String> reasons) {}
    public record Result(List<Choice> selected,List<Choice> alternatives,List<Choice> topCandidates,
                         Map<Long,List<Long>> blockedBy,
                         int candidateCount,int limitedCount,int remainingMinutes,String outcome,
                         String reasonCode) {}
}
