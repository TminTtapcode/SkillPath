package com.skillpath.planner.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;
import org.junit.jupiter.api.Test;

class AdaptiveBudgetPolicyV1Test {
    private final AdaptiveBudgetPolicyV1 policy=new AdaptiveBudgetPolicyV1();

    @Test void overridePreservesCompletedAndInProgressAndExpiresOnlyUnstarted() {
        var tasks=List.of(new AdaptiveBudgetPolicyV1.Task(1,"COMPLETED",45,25),
                new AdaptiveBudgetPolicyV1.Task(2,"IN_PROGRESS",20,null),
                new AdaptiveBudgetPolicyV1.Task(3,"ASSIGNED",30,null));
        var before=policy.evaluate(90,tasks);
        assertThat(before.remainingMinutes()).isEqualTo(45);
        var after=policy.evaluate(30,tasks);
        assertThat(after.completedActualMinutes()).isEqualTo(25);
        assertThat(after.inProgressReservedMinutes()).isEqualTo(20);
        assertThat(after.remainingMinutes()).isZero();
        assertThat(after.overBudgetMinutes()).isEqualTo(15);
        assertThat(after.preservedTaskIds()).containsExactly(1L,2L);
        assertThat(after.expirableTaskIds()).containsExactly(3L);
    }

    @Test void blockedWorkIsReservedAndZeroActualIsRecordedNotMastery() {
        var result=policy.evaluate(20,List.of(new AdaptiveBudgetPolicyV1.Task(1,"COMPLETED",10,0),
                new AdaptiveBudgetPolicyV1.Task(2,"BLOCKED",15,null)));
        assertThat(result.remainingMinutes()).isEqualTo(5);
        assertThat(result.preservedTaskIds()).containsExactly(1L,2L);
    }

    @Test void rejectsDuplicateOrUnknownTasks() {
        assertThatThrownBy(()->policy.evaluate(30,List.of(
                new AdaptiveBudgetPolicyV1.Task(1,"ASSIGNED",10,null),
                new AdaptiveBudgetPolicyV1.Task(1,"ASSIGNED",10,null))))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(()->policy.evaluate(30,List.of(
                new AdaptiveBudgetPolicyV1.Task(1,"MYSTERY",10,null))))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
