package com.skillpath.learning.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

class LearningTransitionPolicyTest {
    @Test
    void followsDeclaredLifecycleAndKeepsTerminalTasksImmutable() {
        assertThat(LearningTransitionPolicy.next("ASSIGNED", "start")).isEqualTo("IN_PROGRESS");
        assertThat(LearningTransitionPolicy.next("IN_PROGRESS", "blocked")).isEqualTo("BLOCKED");
        assertThat(LearningTransitionPolicy.next("BLOCKED", "resume")).isEqualTo("IN_PROGRESS");
        assertThat(LearningTransitionPolicy.next("IN_PROGRESS", "complete")).isEqualTo("COMPLETED");
        assertThat(LearningTransitionPolicy.next("ASSIGNED", "skip")).isEqualTo("SKIPPED");
        assertThat(LearningTransitionPolicy.next("IN_PROGRESS", "abandon")).isEqualTo("ABANDONED");
        assertThat(LearningTransitionPolicy.stopsSession("SKIPPED")).isTrue();
        assertThat(LearningTransitionPolicy.stopsSession("ABANDONED")).isTrue();
        assertThat(LearningTransitionPolicy.stopsSession("COMPLETED")).isFalse();
        assertThatThrownBy(() -> LearningTransitionPolicy.next("COMPLETED", "start"))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> LearningTransitionPolicy.next("ASSIGNED", "complete"))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
