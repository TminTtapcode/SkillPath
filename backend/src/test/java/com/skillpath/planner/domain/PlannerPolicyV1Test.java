package com.skillpath.planner.domain;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;

class PlannerPolicyV1Test {
    private final PlannerPolicyV1 policy=new PlannerPolicyV1();
    private static final Instant NOW=Instant.parse("2026-09-23T08:00:00Z");
    private static BigDecimal n(String value){return new BigDecimal(value);}

    @Test void unmetHardPrerequisiteBlocksDependentAndSelectsRoot(){
        var input=new PlannerPolicyV1.Input(NOW,20,
                List.of(new PlannerPolicyV1.Node(1,"ACTIVE",n("0.7"),n("0.8"),0),
                        new PlannerPolicyV1.Node(2,"ACTIVE",n("1"),n("0.8"),1)),
                List.of(new PlannerPolicyV1.Edge(1,2,"PREREQUISITE",n("0.8"))),List.of(),List.of(),
                List.of(new PlannerPolicyV1.Variant(10,1,"LEARN",1,12),
                        new PlannerPolicyV1.Variant(20,2,"LEARN",2,15)));
        var first=policy.plan(input);
        assertThat(first.selected()).extracting(PlannerPolicyV1.Choice::nodeId).containsExactly(1L);
        assertThat(first.blockedBy().get(2L)).containsExactly(1L);
        assertThat(policy.plan(input)).isEqualTo(first);
    }

    @Test void timeBudgetNeverSelectsOversizedVariant(){
        var input=new PlannerPolicyV1.Input(NOW,20,
                List.of(new PlannerPolicyV1.Node(1,"ACTIVE",n("0.8"),n("0.8"),0)),List.of(),
                List.of(),List.of(),List.of(new PlannerPolicyV1.Variant(10,1,"LEARN",1,45)));
        assertThat(policy.plan(input).selected()).isEmpty();
        assertThat(policy.plan(input).outcome()).isEqualTo("NO_TIME_FIT_VARIANT");
    }

    @Test void masteredPrerequisiteDoesNotRemainAnUnlockCandidate(){
        var input=new PlannerPolicyV1.Input(NOW,20,
                List.of(new PlannerPolicyV1.Node(1,"ACTIVE",n("1"),n("0.8"),0),
                        new PlannerPolicyV1.Node(2,"ACTIVE",n("0.7"),n("0.8"),1)),
                List.of(new PlannerPolicyV1.Edge(1,2,"PREREQUISITE",n("1.0"))),
                List.of(new PlannerPolicyV1.State(1,n("0.9"),n("0.8"),4,"MASTERED")),List.of(),
                List.of(new PlannerPolicyV1.Variant(10,1,"LEARN",1,10),
                        new PlannerPolicyV1.Variant(20,2,"LEARN",1,10)));
        var result=policy.plan(input);
        assertThat(result.selected()).extracting(PlannerPolicyV1.Choice::nodeId).containsExactly(2L);
        assertThat(result.blockedBy()).doesNotContainKey(2L);
    }

    @Test void noContentHasAStableNoSafeReason(){
        var input=new PlannerPolicyV1.Input(NOW,20,
                List.of(new PlannerPolicyV1.Node(1,"ACTIVE",n("0.8"),n("0.8"),0)),
                List.of(),List.of(),List.of(),List.of());
        var result=policy.plan(input);
        assertThat(result.outcome()).isEqualTo("NO_SAFE_RECOMMENDATION");
        assertThat(result.reasonCode()).isEqualTo("NO_CONTENT");
    }

    @Test void dueReviewMayWinAndAllMasteredIsOnlyCandidate(){
        var node=new PlannerPolicyV1.Node(1,"ACTIVE",n("0.8"),n("0.8"),0);
        var state=new PlannerPolicyV1.State(1,n("0.9"),n("0.8"),8,"MASTERED");
        var variant=new PlannerPolicyV1.Variant(10,1,"RECALL",1,5);
        var input=new PlannerPolicyV1.Input(NOW,20,List.of(node),List.of(),List.of(state),
                List.of(new PlannerPolicyV1.Due(1,NOW.minusSeconds(20L*86400))),List.of(variant));
        assertThat(policy.plan(input).selected()).hasSize(1);
        assertThat(policy.plan(new PlannerPolicyV1.Input(NOW,20,List.of(node),List.of(),
                List.of(state),List.of(),List.of(variant))).outcome())
                .isEqualTo("GOAL_COMPLETION_CANDIDATE");
    }

    @Test void remainingBudgetRecalculatesTheNextItemWithoutChangingKnowledge(){
        var input=new PlannerPolicyV1.Input(NOW,30,
                List.of(new PlannerPolicyV1.Node(1,"ACTIVE",n("0.9"),n("0.8"),0),
                        new PlannerPolicyV1.Node(2,"ACTIVE",n("0.5"),n("0.8"),1)),
                List.of(),List.of(),List.of(),
                List.of(new PlannerPolicyV1.Variant(10,1,"LEARN",1,15),
                        new PlannerPolicyV1.Variant(20,2,"LEARN",1,15)));
        var result=policy.plan(input);
        assertThat(result.selected()).hasSize(2);
        assertThat(result.selected().get(1).learningRoi())
                .isLessThan(result.topCandidates().stream()
                        .filter(choice->choice.variant().templateVersionId()==20)
                        .findFirst().orElseThrow().learningRoi());
    }
}
