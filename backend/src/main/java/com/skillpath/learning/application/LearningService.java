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
public class LearningService implements LearningQueries, EvaluatedTaskCommands {
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

    @Override
    @Transactional(readOnly = true)
    public List<LearningQueries.PlannerVariant> activeVariants(long graphVersionId) {
        return store.activeVariants(graphVersionId).stream().map(variant ->
                new LearningQueries.PlannerVariant(variant.content().templateVersionId(),
                        variant.content().primaryNodeId(), variant.content().activityType(),
                        variant.difficulty(), variant.content().minutes())).toList();
    }

    @Override
    @Transactional(readOnly = true)
    public List<LearningQueries.AdaptiveVariant> activeAdaptiveVariants(long graphVersionId) {
        return store.activeAdaptiveVariants(graphVersionId).stream().map(variant ->
                new LearningQueries.AdaptiveVariant(variant.content().templateVersionId(),
                        variant.content().primaryNodeId(),variant.content().activityType(),
                        variant.difficulty(),variant.content().minutes(),variant.variantGroupKey(),
                        variant.content().evaluationMode())).toList();
    }

    @Override
    @Transactional(readOnly = true)
    public LearningQueries.AssignedSession activeAssignment(long userId, long goalId) {
        return store.activeSession(userId, goalId).map(session ->
                new LearningQueries.AssignedSession(session.id(), session.assignmentSource()))
                .orElse(null);
    }

    @Override
    @Transactional(readOnly = true)
    public List<LearningQueries.AssignedTask> sessionTasks(long userId, long sessionId) {
        if(store.session(userId,sessionId,false).isEmpty()) return List.of();
        return store.tasks(userId,sessionId).stream().map(task -> new LearningQueries.AssignedTask(
                task.id(), task.position(), task.status(), task.plannedMinutes(),
                task.title().en(), task.title().vi())).toList();
    }

    @Override
    @Transactional(readOnly = true)
    public List<LearningQueries.PlannerTaskState> plannerTaskStates(long userId,long sessionId) {
        SessionRow session=store.session(userId,sessionId,false)
                .orElseThrow(()->notFound("LEARNING_SESSION_NOT_FOUND"));
        if(!"PLANNER".equals(session.assignmentSource()))
            throw new ApiException(HttpStatus.CONFLICT,"NOT_PLANNER_SESSION","This is not a planner session.");
        return store.tasks(userId,sessionId).stream().map(task->new LearningQueries.PlannerTaskState(
                task.id(),task.position(),task.templateVersionId(),task.status(),
                task.plannedMinutes(),task.actualMinutes())).toList();
    }

    @Override
    @Transactional
    public List<LearningQueries.AssignedTask> revisePlannerAssignment(long userId,long sessionId,
            long graphVersionId,List<Long> expireTaskIds,List<LearningQueries.PlannerSelection> selections,
            Instant now) {
        SessionRow session=store.session(userId,sessionId,true)
                .orElseThrow(()->notFound("LEARNING_SESSION_NOT_FOUND"));
        if(!"ACTIVE".equals(session.status())||!"PLANNER".equals(session.assignmentSource())
                ||session.graphVersionId()!=graphVersionId)
            throw new ApiException(HttpStatus.CONFLICT,"PLANNER_REVISION_BLOCKED",
                    "The planner session changed or is incompatible.");
        List<TaskRow> prior=store.tasks(userId,sessionId);
        Set<Long> assigned=prior.stream().filter(task->"ASSIGNED".equals(task.status()))
                .map(TaskRow::id).collect(java.util.stream.Collectors.toSet());
        if(expireTaskIds==null||!assigned.equals(new HashSet<>(expireTaskIds))
                ||expireTaskIds.size()!=assigned.size()||selections==null||selections.size()>3)
            throw new ApiException(HttpStatus.CONFLICT,"PLANNER_REVISION_BLOCKED",
                    "Only current unstarted assignments may be replaced.");
        for(TaskRow task:prior)if(assigned.contains(task.id())){
            long receipt=store.addReceipt(userId,"internal-adaptive-expire-"+sessionId+"-"+task.id(),
                    "adaptive-expire",Long.toString(sessionId),task.id(),"EXPIRED",now);
            if(!store.transitionTask(task.id(),task.version(),"EXPIRED",null,now))
                throw new ApiException(HttpStatus.CONFLICT,"PLANNER_REVISION_BLOCKED",
                        "The planner task changed.");
            store.addEvent(task.id(),userId,receipt,"ASSIGNED","EXPIRED","OTHER",now);
        }
        var available=store.activeAdaptiveVariants(graphVersionId).stream()
                .collect(java.util.stream.Collectors.toMap(v->v.content().templateVersionId(),v->v));
        Set<Long> used=prior.stream().map(TaskRow::templateVersionId)
                .collect(java.util.stream.Collectors.toSet());
        List<LearningStore.PlannerAssignedTask> append=new ArrayList<>();
        int position=prior.stream().mapToInt(TaskRow::position).max().orElse(0);
        for(var selected:selections){
            var variant=available.get(selected.templateVersionId());
            if(selected.decisionId()<1||variant==null||!used.add(selected.templateVersionId()))
                throw new ApiException(HttpStatus.UNPROCESSABLE_ENTITY,"INVALID_PLANNER_ASSIGNMENT",
                        "Adaptive task selection is invalid.");
            var content=variant.content();
            append.add(new LearningStore.PlannerAssignedTask(selected.decisionId(),new CatalogTask(
                    content.templateVersionId(),++position,content.activityType(),content.evaluationMode(),
                    content.minutes(),content.primaryNodeId(),content.title(),content.instructions(),
                    content.resourceTitle(),content.resourceBody(),content.checklistEn(),content.checklistVi())));
        }
        List<TaskRow> added=store.appendPlannerTasks(userId,sessionId,append,now);
        if(added.isEmpty() && prior.stream().noneMatch(task->Set.of("IN_PROGRESS","BLOCKED")
                .contains(task.status())))store.closeSession(sessionId,"STOPPED",now);
        return added.stream().map(task->
                new LearningQueries.AssignedTask(task.id(),task.position(),task.status(),
                        task.plannedMinutes(),task.title().en(),task.title().vi())).toList();
    }

    @Override
    @Transactional(readOnly = true)
    public EvaluatedTaskCommands.EvaluatedTask taskForCheck(long userId, long taskId) {
        long sessionId = store.sessionIdForTask(userId, taskId)
                .orElseThrow(() -> notFound("LEARNING_TASK_NOT_FOUND"));
        SessionRow session = store.session(userId, sessionId, false)
                .orElseThrow(() -> notFound("LEARNING_TASK_NOT_FOUND"));
        TaskRow task = store.task(userId, taskId, false)
                .orElseThrow(() -> notFound("LEARNING_TASK_NOT_FOUND"));
        if (!"OBJECTIVE".equals(task.evaluationMode())) {
            throw new ApiException(HttpStatus.CONFLICT, "TASK_CHECK_UNAVAILABLE",
                    "This task has no objective check.");
        }
        return new EvaluatedTaskCommands.EvaluatedTask(task.id(), session.goalId(), session.graphVersionId(),
                task.templateVersionId(), task.status(), session.status());
    }

    @Override
    @Transactional
    public void completeCheckedTask(long userId, long taskId, long attemptId, int actualMinutes, Instant now) {
        if (attemptId <= 0 || actualMinutes < 0 || actualMinutes > 360) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "INVALID_TASK_COMPLETION", "Completion input is invalid.");
        }
        long sessionId = store.sessionIdForTask(userId, taskId)
                .orElseThrow(() -> notFound("LEARNING_TASK_NOT_FOUND"));
        SessionRow session = store.session(userId, sessionId, true)
                .orElseThrow(() -> notFound("LEARNING_TASK_NOT_FOUND"));
        TaskRow task = store.task(userId, taskId, true)
                .orElseThrow(() -> notFound("LEARNING_TASK_NOT_FOUND"));
        if (!"ACTIVE".equals(session.status()) || !"OBJECTIVE".equals(task.evaluationMode())
                || !"IN_PROGRESS".equals(task.status())) {
            throw new ApiException(HttpStatus.CONFLICT, "LEARNING_TASK_STATE_CONFLICT",
                    "This objective task is not ready for completion.");
        }
        List<TaskRow> tasks = store.tasks(userId, sessionId);
        if (tasks.stream().filter(item -> item.position() < task.position())
                .anyMatch(item -> !"COMPLETED".equals(item.status())
                        && !("PLANNER".equals(session.assignmentSource())
                        && "EXPIRED".equals(item.status())))) {
            throw new ApiException(HttpStatus.CONFLICT, "LEARNING_TASK_OUT_OF_ORDER",
                    "Finish the earlier step first.");
        }
        if (!store.transitionTask(taskId, task.version(), "COMPLETED", actualMinutes, now)) {
            throw new ApiException(HttpStatus.CONFLICT, "LEARNING_TASK_STATE_CONFLICT",
                    "The task state has changed.");
        }
        long receipt = store.addReceipt(userId, "internal-objective-attempt-" + attemptId, "objective-check",
                Long.toString(attemptId), taskId, "COMPLETED", now);
        store.addEvent(taskId, userId, receipt, "IN_PROGRESS", "COMPLETED", null, now);
        if (tasks.stream().allMatch(item -> item.id() == taskId || "COMPLETED".equals(item.status())
                || (session.assignmentSource().equals("PLANNER") && "EXPIRED".equals(item.status())))) {
            store.closeSession(sessionId, "COMPLETED", now);
        }
    }

    @Override
    @Transactional
    public long assignPlanner(long userId,long goalId,long graphVersionId,
                              List<LearningQueries.PlannerSelection> selections,Instant now){
        return assignPlannerValidated(userId,goalId,graphVersionId,selections,now,false);
    }

    @Override
    @Transactional
    public long assignAdaptivePlanner(long userId,long goalId,long graphVersionId,
                                      List<LearningQueries.PlannerSelection> selections,Instant now){
        return assignPlannerValidated(userId,goalId,graphVersionId,selections,now,true);
    }

    private long assignPlannerValidated(long userId,long goalId,long graphVersionId,
            List<LearningQueries.PlannerSelection> selections,Instant now,boolean adaptive){
        if(selections.isEmpty() || selections.size()>3 || store.activeSession(userId,goalId).isPresent())
            throw new ApiException(HttpStatus.CONFLICT,"ACTIVE_LEARNING_SESSION",
                    "A learning session is already active or the plan is invalid.");
        var available=(adaptive?store.activeAdaptiveVariants(graphVersionId):store.activeVariants(graphVersionId)).stream()
                .collect(java.util.stream.Collectors.toMap(v->v.content().templateVersionId(),v->v));
        List<LearningStore.PlannerAssignedTask> tasks=new ArrayList<>();
        Set<Long> seen=new HashSet<>();
        for(int i=0;i<selections.size();i++){
            var selection=selections.get(i);
            var variant=available.get(selection.templateVersionId());
            if(selection.decisionId()<1 || selection.position()!=i+1 || variant==null
                    || !seen.add(selection.templateVersionId()))
                throw new ApiException(HttpStatus.UNPROCESSABLE_ENTITY,"INVALID_PLANNER_ASSIGNMENT",
                        "Planner task selection is invalid.");
            var content=variant.content();
            tasks.add(new LearningStore.PlannerAssignedTask(selection.decisionId(),new CatalogTask(
                    content.templateVersionId(),selection.position(),content.activityType(),
                    content.evaluationMode(),content.minutes(),content.primaryNodeId(),
                    content.title(),content.instructions(),content.resourceTitle(),content.resourceBody(),
                    content.checklistEn(),content.checklistVi())));
        }
        return store.createPlannerSession(userId,goalId,graphVersionId,tasks,now);
    }

    @Override
    @Transactional
    public void supersedeUnstarted(long userId,long sessionId,Instant now){
        SessionRow session=store.session(userId,sessionId,true)
                .orElseThrow(()->notFound("LEARNING_SESSION_NOT_FOUND"));
        List<TaskRow> tasks=store.tasks(userId,sessionId);
        if(!session.status().equals("ACTIVE") || !session.assignmentSource().equals("PLANNER")
                || tasks.isEmpty() || tasks.stream().anyMatch(task->!task.status().equals("ASSIGNED")))
            throw new ApiException(HttpStatus.CONFLICT,"PLANNER_REVISION_BLOCKED",
                    "Only wholly unstarted planner sessions may be revised.");
        for(TaskRow task:tasks){
            long receipt=store.addReceipt(userId,"planner-revision-"+sessionId+"-"+task.id(),
                    "planner-revision",Long.toString(sessionId),task.id(),"EXPIRED",now);
            if(!store.transitionTask(task.id(),task.version(),"EXPIRED",null,now))
                throw new ApiException(HttpStatus.CONFLICT,"PLANNER_REVISION_BLOCKED",
                        "The planner session changed.");
            store.addEvent(task.id(),userId,receipt,"ASSIGNED","EXPIRED","OTHER",now);
        }
        store.closeSession(sessionId,"STOPPED",now);
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
        if(active!=null && active.assignmentSource().equals("PLANNER"))
            throw new ApiException(HttpStatus.CONFLICT,"ACTIVE_PLANNER_SESSION",
                    "Finish or stop the current Today plan first.");
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
        if (command.equals("complete") && "OBJECTIVE".equals(task.evaluationMode())) {
            throw new ApiException(HttpStatus.CONFLICT, "OBJECTIVE_CHECK_REQUIRED",
                    "Submit the objective check to complete this task.");
        }
        if (!session.status().equals("ACTIVE")) {
            throw new ApiException(HttpStatus.CONFLICT, "LEARNING_SESSION_CLOSED", "The study session has ended.");
        }
        List<TaskRow> tasks = store.tasks(userId, sessionId);
        boolean previousComplete = tasks.stream().filter(item -> item.position() < task.position())
                .allMatch(item -> item.status().equals("COMPLETED")
                        || (session.assignmentSource().equals("PLANNER") && item.status().equals("EXPIRED")));
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
                .allMatch(item -> item.id() == taskId || item.status().equals("COMPLETED")
                        || (session.assignmentSource().equals("PLANNER") && item.status().equals("EXPIRED")))) {
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
        if (value == null || !KEY.matcher(value).matches() || value.startsWith("internal-"))
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
