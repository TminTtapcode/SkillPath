package com.skillpath.planner.application;

import com.skillpath.goal.application.GoalQueries;
import com.skillpath.shared.api.ApiException;
import java.time.Clock;
import java.time.Instant;
import java.util.UUID;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Leased delivery of Review-ready and day-override requests. Rollout stays
 * disabled until the full learner-flow gate passes; AdaptiveReplanService is
 * the production executor for immutable revisions.
 */
@Component
@ConditionalOnProperty(name = "skillpath.phase7.replan-worker-enabled", havingValue = "true")
public class PlannerReplanWorker {
    private final ReplanRequestStore store;
    private final ReplanExecution execution;
    private final GoalQueries goals;
    private final TransactionTemplate transactions;
    private final Clock clock;
    private final String workerId = UUID.randomUUID().toString();

    public PlannerReplanWorker(ReplanRequestStore store, ReplanExecution execution,
                               GoalQueries goals, TransactionTemplate transactions, Clock clock) {
        this.store = store;
        this.execution = execution;
        this.goals = goals;
        this.transactions = transactions;
        this.clock = clock;
    }

    @Scheduled(fixedDelayString = "${skillpath.phase7.replan-poll-ms:1000}")
    public void dispatch() {
        for (int i = 0; i < 50 && processOne(); i++) {
            // Bounded batch; the next scheduled poll handles any remainder.
        }
    }

    public boolean processOne() {
        ReplanRequestStore.Request claimed = transactions.execute(status ->
                store.claim(workerId, clock.instant()).orElse(null));
        if (claimed == null) return false;
        try {
            transactions.executeWithoutResult(status -> {
                ReplanRequestStore.Request request = store.locked(claimed.id(), workerId).orElse(null);
                if (request == null) return;
                ReplanExecution.Outcome outcome = executeForOwnedGoal(request);
                store.complete(request.id(), workerId, outcome, clock.instant());
            });
        } catch (RuntimeException exception) {
            transactions.executeWithoutResult(status -> store.fail(claimed.id(), workerId,
                    clock.instant(), safeCode(exception)));
        }
        return true;
    }

    private ReplanExecution.Outcome executeForOwnedGoal(ReplanRequestStore.Request request) {
        try {
            var goal = goals.planningGoalForUser(request.userId(), true);
            if (goal.id() != request.goalId())
                return new ReplanExecution.Outcome(ReplanExecution.ResultCode.STALE_GOAL, null);
        } catch (ApiException exception) {
            if ("ACTIVE_GOAL_NOT_FOUND".equals(exception.code()))
                return new ReplanExecution.Outcome(ReplanExecution.ResultCode.STALE_GOAL, null);
            throw exception;
        }
        return execution.execute(request);
    }

    private static String safeCode(RuntimeException exception) {
        if (exception instanceof ApiException api && api.code().matches("[A-Z0-9_]{1,80}"))
            return api.code();
        return "REPLAN_EXECUTION_FAILED";
    }
}
