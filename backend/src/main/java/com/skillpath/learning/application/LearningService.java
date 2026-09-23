package com.skillpath.learning.application;

import com.skillpath.goal.application.GoalQueries;
import com.skillpath.knowledge.application.LearningKnowledgeQueries;
import com.skillpath.learning.application.LearningStore.CatalogTask;
import com.skillpath.learning.application.LearningStore.Receipt;
import com.skillpath.learning.application.LearningStore.SequenceDefinition;
import com.skillpath.learning.application.LearningStore.SessionRow;
import com.skillpath.learning.application.LearningStore.Step;
import com.skillpath.learning.application.LearningStore.TaskRow;
import com.skillpath.learning.domain.LearningTransitionPolicy;
import com.skillpath.shared.api.ApiException;
import com.skillpath.shared.localization.SupportedLocale;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.HexFormat;
import java.util.List;
import java.util.Set;
import java.util.regex.Pattern;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

@Service
public class LearningService implements LearningQueries {
    private static final Pattern KEY = Pattern.compile("[A-Za-z0-9._:-]{1,128}");
    private static final Pattern SEQUENCE_KEY = Pattern.compile("[a-z0-9-]{1,120}");
    private static final Set<String> REASONS = Set.of("TIME", "DIFFICULT", "OTHER");
    private final LearningStore store;
    private final GoalQueries goals;
    private final LearningKnowledgeQueries knowledge;
    private final Clock clock;

    public LearningService(LearningStore store, GoalQueries goals, LearningKnowledgeQueries knowledge, Clock clock) {
        this.store = store;
        this.goals = goals;
        this.knowledge = knowledge;
        this.clock = clock;
    }

    @Override
    @Transactional(readOnly = true)
    public List<SequenceDefinition> activeCatalog(long graphVersionId) {
        return store.activeSequences(graphVersionId);
    }

    @Override
    @Transactional(readOnly = true)
    public List<TaskRow> assignedTasks(long userId, long goalId) {
        return store.activeSession(userId, goalId).map(s -> store.tasks(userId, s.id())).orElse(List.of());
    }

    @Transactional(readOnly = true)
    public List<SequenceView> sequences(long userId, SupportedLocale locale) {
        var goal = goals.activeGoalForUser(userId);
        var graph = knowledge.publishedLearningGraph(goal.goalTemplateId());
        return store.activeSequences(graph.graphVersionId()).stream()
                .filter(sequence -> valid(sequence, graph.rootNodeIds()))
                .map(sequence -> sequenceView(sequence, locale))
                .toList();
    }

    @Transactional(readOnly = true)
    public SequenceView sequence(long userId, String key, SupportedLocale locale) {
        var goal = goals.activeGoalForUser(userId);
        var graph = knowledge.publishedLearningGraph(goal.goalTemplateId());
        return sequenceView(compatible(graph.graphVersionId(), graph.rootNodeIds(), key), locale);
    }

    @Transactional(isolation = Isolation.READ_COMMITTED)
    public StartOutcome start(long userId, String key, String idempotencyKey) {
        validateKey(idempotencyKey);
        validateSequenceKey(key);
        String hash = hash("sequence|" + key);
        Receipt replay = replay(userId, idempotencyKey, "start", hash);
        if (replay != null) return new StartOutcome(replay.resourceId(), false, true);

        var goal = goals.lockActiveGoalForUser(userId);
        // The goal lock serializes starts for this learner before the database unique active-goal key.
        replay = replay(userId, idempotencyKey, "start", hash);
        if (replay != null) return new StartOutcome(replay.resourceId(), false, true);
        var graph = knowledge.publishedLearningGraph(goal.goalTemplateId());
        SequenceDefinition sequence = compatible(graph.graphVersionId(), graph.rootNodeIds(), key);
        SessionRow active = store.activeSession(userId, goal.id()).orElse(null);
        long id;
        boolean created;
        if (active != null) {
            id = active.id();
            created = false;
        } else {
            id = store.createSession(userId, goal.id(), sequence, clock.instant());
            created = true;
        }
        store.addReceipt(userId, idempotencyKey, "start", hash, id, "ACTIVE", clock.instant());
        return new StartOutcome(id, created, false);
    }

    @Transactional(readOnly = true)
    public SessionView activeSession(long userId, SupportedLocale locale) {
        var goal = goals.activeGoalForUser(userId);
        return store.activeSession(userId, goal.id())
                .map(session -> view(userId, session, locale))
                .orElse(null);
    }

    @Transactional(readOnly = true)
    public SessionView session(long userId, long sessionId, SupportedLocale locale) {
        SessionRow session = store.session(userId, sessionId, false)
                .orElseThrow(() -> notFound("LEARNING_SESSION_NOT_FOUND"));
        return view(userId, session, locale);
    }

    @Transactional(isolation = Isolation.READ_COMMITTED)
    public CommandOutcome command(long userId, long taskId, String command, String key,
                                  Completion completion, String reason) {
        validateKey(key);
        if (!Set.of("start", "complete", "skip", "blocked", "resume", "abandon").contains(command)) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "INVALID_LEARNING_COMMAND", "Unknown task command.");
        }
        if (command.equals("complete")) {
            if (completion == null || completion.actualMinutes() < 0 || completion.actualMinutes() > 360
                    || completion.completedStepIds() == null || completion.completedStepIds().size() > 20) {
                throw new ApiException(HttpStatus.BAD_REQUEST, "INVALID_TASK_COMPLETION", "Completion input is invalid.");
            }
        } else if (completion != null) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "INVALID_LEARNING_COMMAND", "Unexpected completion input.");
        }
        if (Set.of("skip", "blocked", "abandon").contains(command)) {
            if (!REASONS.contains(reason)) {
                throw new ApiException(HttpStatus.BAD_REQUEST, "INVALID_TASK_REASON", "Choose a reason code.");
            }
        } else if (reason != null) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "INVALID_TASK_REASON", "Unexpected reason code.");
        }
        List<String> selected = completion == null ? List.of() : completion.completedStepIds();
        if (selected.stream().anyMatch(id -> id == null || !id.matches("[a-z0-9-]{1,40}"))
                || selected.size() != new HashSet<>(selected).size()) {
            throw new ApiException(HttpStatus.UNPROCESSABLE_ENTITY, "INVALID_TASK_CHECKLIST", "Checklist IDs are invalid.");
        }
        List<String> sorted = new ArrayList<>(selected);
        sorted.sort(String::compareTo);
        String hash = hash(command + "|" + taskId + "|" + (completion == null ? "" : completion.actualMinutes())
                + "|" + String.join(",", sorted) + "|" + (reason == null ? "" : reason));
        Receipt prior = replay(userId, key, command, hash);
        if (prior != null) return new CommandOutcome(prior.resourceId(), prior.outcome(), true);

        long sessionId = store.sessionIdForTask(userId, taskId)
                .orElseThrow(() -> notFound("LEARNING_TASK_NOT_FOUND"));
        SessionRow session = store.session(userId, sessionId, true)
                .orElseThrow(() -> notFound("LEARNING_TASK_NOT_FOUND"));
        prior = replay(userId, key, command, hash);
        if (prior != null) return new CommandOutcome(prior.resourceId(), prior.outcome(), true);
        TaskRow task = store.task(userId, taskId, true)
                .orElseThrow(() -> notFound("LEARNING_TASK_NOT_FOUND"));
        if (!session.status().equals("ACTIVE")) {
            throw new ApiException(HttpStatus.CONFLICT, "LEARNING_SESSION_CLOSED", "The study session has ended.");
        }
        List<TaskRow> tasks = store.tasks(userId, sessionId);
        boolean previousComplete = tasks.stream().filter(item -> item.position() < task.position())
                .allMatch(item -> item.status().equals("COMPLETED"));
        if (!previousComplete) {
            throw new ApiException(HttpStatus.CONFLICT, "LEARNING_TASK_OUT_OF_ORDER", "Finish the earlier step first.");
        }
        String next;
        try { next = LearningTransitionPolicy.next(task.status(), command); }
        catch (IllegalArgumentException exception) {
            throw new ApiException(HttpStatus.CONFLICT, "LEARNING_TASK_STATE_CONFLICT", "The task state has changed.");
        }
        if (command.equals("complete")) {
            Set<String> expected = task.checklistEn().stream().map(Step::id).collect(java.util.stream.Collectors.toSet());
            if (!expected.equals(new HashSet<>(selected))) {
                throw new ApiException(HttpStatus.UNPROCESSABLE_ENTITY, "INVALID_TASK_CHECKLIST", "Complete every declared step.");
            }
        }
        Instant now = clock.instant();
        if (!store.transitionTask(task.id(), task.version(), next,
                completion == null ? null : completion.actualMinutes(), now)) {
            throw new ApiException(HttpStatus.CONFLICT, "LEARNING_TASK_STATE_CONFLICT", "The task state has changed.");
        }
        long receiptId = store.addReceipt(userId, key, command, hash, taskId, next, now);
        store.addEvent(taskId, userId, receiptId, task.status(), next, reason, now);
        if (LearningTransitionPolicy.stopsSession(next)) {
            store.closeSession(sessionId, "STOPPED", now);
        } else if (next.equals("COMPLETED") && tasks.stream()
                .allMatch(item -> item.id() == taskId || item.status().equals("COMPLETED"))) {
            store.closeSession(sessionId, "COMPLETED", now);
        }
        return new CommandOutcome(taskId, next, false);
    }

    private SessionView view(long userId, SessionRow session, SupportedLocale locale) {
        // Sequence titles are immutable; task content is served exclusively from the assignment snapshot.
        String title = session.title().forVietnamese(locale.requiresTranslation());
        List<TaskView> tasks = store.tasks(userId, session.id()).stream()
                .map(task -> new TaskView(Long.toString(task.id()), task.position(), task.status(), task.activityType(),
                        task.evaluationMode(), task.plannedMinutes(), task.actualMinutes(),
                        task.title().forVietnamese(locale.requiresTranslation()),
                        task.instructions().forVietnamese(locale.requiresTranslation()),
                        task.resourceTitle().forVietnamese(locale.requiresTranslation()),
                        task.resourceBody().forVietnamese(locale.requiresTranslation()),
                        locale.requiresTranslation() ? task.checklistVi() : task.checklistEn()))
                .toList();
        return new SessionView(Long.toString(session.id()), session.sequenceKey(), title, session.status(),
                Long.toString(session.graphVersionId()), session.assignmentSource(), session.startedAt(),
                session.completedAt(), tasks);
    }

    private SequenceDefinition compatible(long graphId, Set<Long> roots, String key) {
        validateSequenceKey(key);
        SequenceDefinition sequence = store.activeSequence(graphId, key)
                .orElseThrow(() -> new ApiException(HttpStatus.UNPROCESSABLE_ENTITY,
                        "LEARNING_SEQUENCE_UNAVAILABLE", "This sequence is unavailable for the current graph."));
        if (!valid(sequence, roots)) {
            throw new ApiException(HttpStatus.UNPROCESSABLE_ENTITY,
                    "LEARNING_SEQUENCE_UNAVAILABLE", "This sequence is incomplete or incompatible.");
        }
        return sequence;
    }

    private boolean valid(SequenceDefinition sequence, Set<Long> roots) {
        if (sequence.tasks().size() != 3 || sequence.totalMinutes() < 1 || sequence.totalMinutes() > 180
                || sequence.tasks().stream().mapToInt(CatalogTask::minutes).sum() != sequence.totalMinutes()) return false;
        List<String> expectedOrder = List.of("LEARN", "PRACTICE", "RECALL");
        for (int i = 0; i < sequence.tasks().size(); i++) {
            CatalogTask task = sequence.tasks().get(i);
            if (task.position() != i + 1 || !expectedOrder.get(i).equals(task.activityType()) || task.minutes() < 1
                    || !roots.contains(task.primaryNodeId()) || !Set.of("SELF_REPORT", "NONE").contains(task.evaluationMode())
                    || task.checklistEn().isEmpty() || task.checklistEn().size() != task.checklistVi().size()
                    || task.checklistEn().stream().map(Step::id).distinct().count() != task.checklistEn().size()
                    || !task.checklistEn().stream().map(Step::id).toList()
                            .equals(task.checklistVi().stream().map(Step::id).toList())) return false;
        }
        return true;
    }

    private SequenceView sequenceView(SequenceDefinition sequence, SupportedLocale locale) {
        return new SequenceView(sequence.key(), sequence.version(), Long.toString(sequence.graphVersionId()),
                sequence.title().forVietnamese(locale.requiresTranslation()),
                sequence.description().forVietnamese(locale.requiresTranslation()), sequence.totalMinutes(),
                sequence.tasks().stream().map(task -> new SequenceStep(task.position(), task.activityType(),
                        task.minutes(), task.title().forVietnamese(locale.requiresTranslation()))).toList());
    }

    private Receipt replay(long userId, String key, String command, String hash) {
        Receipt receipt = store.receipt(userId, key).orElse(null);
        if (receipt != null && (!receipt.command().equals(command) || !receipt.hash().equals(hash))) {
            throw new ApiException(HttpStatus.CONFLICT, "IDEMPOTENCY_KEY_REUSED", "This key was used for another command.");
        }
        return receipt;
    }

    private void validateKey(String value) {
        if (value == null || !KEY.matcher(value).matches())
            throw new ApiException(HttpStatus.BAD_REQUEST, "INVALID_IDEMPOTENCY_KEY", "Idempotency key is invalid.");
    }
    private void validateSequenceKey(String value) {
        if (value == null || !SEQUENCE_KEY.matcher(value).matches())
            throw new ApiException(HttpStatus.BAD_REQUEST, "INVALID_SEQUENCE_KEY", "Sequence key is invalid.");
    }
    private String hash(String value) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException(exception);
        }
    }
    private ApiException notFound(String code) {
        return new ApiException(HttpStatus.NOT_FOUND, code, "Learning item was not found.");
    }

    public record SequenceStep(int position, String activityType, int minutes, String title) {}
    public record SequenceView(String key, int version, String graphVersionId, String title,
                               String description, int totalMinutes, List<SequenceStep> steps) {}
    public record TaskView(String id, int position, String status, String activityType, String evaluationMode,
                           int plannedMinutes, Integer actualMinutes, String title, String instructions,
                           String resourceTitle, String resourceBody, List<Step> checklist) {}
    public record SessionView(String id, String sequenceKey, String title, String status, String graphVersionId,
                              String assignmentSource, Instant startedAt, Instant completedAt, List<TaskView> tasks) {}
    public record StartOutcome(long sessionId, boolean created, boolean replayed) {}
    public record CommandOutcome(long taskId, String status, boolean replayed) {}
    public record Completion(int actualMinutes, List<String> completedStepIds) {}
}
