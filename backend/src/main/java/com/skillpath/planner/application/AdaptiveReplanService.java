package com.skillpath.planner.application;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.skillpath.assessment.application.TaskCheckHistoryQueries;
import com.skillpath.goal.application.GoalQueries;
import com.skillpath.knowledge.application.PlannerKnowledgeQueries;
import com.skillpath.learning.application.LearningQueries;
import com.skillpath.planner.domain.AdaptiveBudgetPolicyV1;
import com.skillpath.planner.domain.PlannerPolicyV1;
import com.skillpath.planner.domain.PlannerPolicyV2;
import com.skillpath.shared.api.ApiException;
import com.skillpath.shared.localization.SupportedLocale;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import org.springframework.http.HttpStatus;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

/** Executes an already Review-ready request inside the worker's goal-serialized transaction. */
@Service
public class AdaptiveReplanService implements ReplanExecution {
    private static final Pattern KEY=Pattern.compile("[A-Za-z0-9._:-]{1,128}");
    private final GoalQueries goals;
    private final LearningQueries learning;
    private final PlannerKnowledgeQueries knowledge;
    private final TaskCheckHistoryQueries history;
    private final PlannerStore store;
    private final PlannerDayStore days;
    private final PlannerService planner;
    private final Clock clock;
    private final AdaptiveBudgetPolicyV1 budgetPolicy = new AdaptiveBudgetPolicyV1();
    private final PlannerPolicyV2 policy = new PlannerPolicyV2();
    @Value("${skillpath.phase7.replan-worker-enabled:false}")
    private boolean automaticReplanEnabled;

    public AdaptiveReplanService(GoalQueries goals,LearningQueries learning,
            PlannerKnowledgeQueries knowledge,
            TaskCheckHistoryQueries history,PlannerStore store,PlannerDayStore days,
            PlannerService planner,Clock clock){
        this.goals=goals;this.learning=learning;this.knowledge=knowledge;
        this.history=history;this.store=store;
        this.days=days;this.planner=planner;this.clock=clock;
    }

    @Override public Outcome execute(ReplanRequestStore.Request request){
        var goal=goals.planningGoalForUser(request.userId(),true);
        if(goal.id()!=request.goalId())return new Outcome(ResultCode.STALE_GOAL,null);
        Instant now=clock.instant().truncatedTo(ChronoUnit.MICROS);
        LocalDate day=now.atZone(ZoneId.of(goal.timezone())).toLocalDate();
        var old=store.current(request.userId(),goal.id(),day).orElse(null);
        if(old==null)return new Outcome(ResultCode.NO_CURRENT_PLAN,null);
        if(old.graphVersionId()!=request.graphVersionId())
            return new Outcome(ResultCode.STALE_GOAL,null);
        long id=revise(request.userId(),goal,day,old,now,"REQUEST:"+request.id(),false);
        return id<1?new Outcome(ResultCode.MANUAL_SESSION_BLOCKED,null)
                :new Outcome(ResultCode.REVISION_CREATED,id);
    }

    /** The caller must hold the owned Goal lock and a repeatable-read transaction. */
    @Transactional(isolation=Isolation.REPEATABLE_READ)
    public PlannerService.TodayView refresh(long userId,String key,SupportedLocale locale){
        if(key==null||!KEY.matcher(key).matches())throw new ApiException(HttpStatus.BAD_REQUEST,
                "INVALID_IDEMPOTENCY_KEY","Idempotency key is invalid.");
        var goal=goals.planningGoalForUser(userId,true);
        Instant now=clock.instant().truncatedTo(ChronoUnit.MICROS);
        LocalDate day=now.atZone(ZoneId.of(goal.timezone())).toLocalDate();
        String hash=PlannerService.digest("REFRESH|"+day);
        var prior=store.receipt(userId,key).orElse(null);
        if(prior!=null){
            if(!"REFRESH".equals(prior.command())||!hash.equals(prior.hash()))
                throw new ApiException(HttpStatus.CONFLICT,"IDEMPOTENCY_KEY_REUSED",
                        "This key was used for another planner command.");
            return planner.view(userId,store.plan(userId,prior.planId()).orElseThrow(),locale);
        }
        var current=store.current(userId,goal.id(),day).orElse(null);
        if(current!=null){
            store.addReceipt(userId,key,"REFRESH",hash,current.id(),now);
            return planner.view(userId,current,locale);
        }
        long planId;
        var previous=store.latestBefore(userId,goal.id(),day).orElse(null);
        if(previous==null){
            var generated=planner.generate(userId,"refresh-internal-"+key,locale);
            planId=Long.parseLong(generated.plan().planId());
        }else{
            planId=revise(userId,goal,day,previous,now,"MISSED_DAY_REFRESH",true);
            if(planId<1)throw new ApiException(HttpStatus.CONFLICT,"ACTIVE_LEARNING_SESSION",
                    "A learner-selected session is active.");
        }
        store.addReceipt(userId,key,"REFRESH",hash,planId,now);
        return planner.view(userId,store.plan(userId,planId).orElseThrow(),locale);
    }

    @Transactional(isolation=Isolation.REPEATABLE_READ)
    public OverrideView override(long userId,String key,int minutes){
        if(key==null||!KEY.matcher(key).matches())throw new ApiException(HttpStatus.BAD_REQUEST,
                "INVALID_IDEMPOTENCY_KEY","Idempotency key is invalid.");
        if(minutes<1||minutes>180)throw new ApiException(HttpStatus.UNPROCESSABLE_ENTITY,
                "INVALID_AVAILABLE_MINUTES","Available minutes must be 1-180.");
        var goal=goals.planningGoalForUser(userId,true);
        Instant now=clock.instant().truncatedTo(ChronoUnit.MICROS);
        LocalDate day=now.atZone(ZoneId.of(goal.timezone())).toLocalDate();
        String hash=PlannerService.digest("TIME_OVERRIDE|"+day+"|"+minutes);
        var replay=days.overrideByKey(userId,key).orElse(null);
        if(replay!=null){
            if(replay.goalId()!=goal.id()||!hash.equals(replay.hash()))
                throw new ApiException(HttpStatus.CONFLICT,"IDEMPOTENCY_KEY_REUSED",
                        "This key was used for another day or value.");
            return new OverrideView(replay.day(),replay.minutes(),replay.revision(),true);
        }
        var old=days.latestOverride(userId,goal.id(),day).orElse(null);
        long id=days.addOverride(userId,goal.id(),day,old==null?1:old.revision()+1,
                minutes,old==null?null:old.id(),key,hash,now);
        long graph=knowledge.planningGraph(goal.goalTemplateId()).graphVersionId();
        days.enqueueOverride(id,userId,goal.id(),graph,now);
        return new OverrideView(day,minutes,old==null?1:old.revision()+1,false);
    }

    @Transactional(readOnly=true,isolation=Isolation.REPEATABLE_READ)
    public ReplanStatusView status(long userId){
        var goal=goals.planningGoalForUser(userId,false);
        var zone=ZoneId.of(goal.timezone());
        LocalDate day=clock.instant().atZone(zone).toLocalDate();
        Instant start=day.atStartOfDay(zone).toInstant();
        Instant end=day.plusDays(1).atStartOfDay(zone).toInstant();
        var request=days.latestRequest(userId,goal.id(),start,end).orElse(null);
        return new ReplanStatusView(day,request==null?"NONE":request.status(),
                request==null?null:request.triggerKind(),
                request==null?null:request.resultCode(),
                request==null?null:request.errorCode(),
                request==null?0:request.attempts(),automaticReplanEnabled);
    }

    long revise(long userId,GoalQueries.PlanningGoal goal,LocalDate day,
                PlannerStore.PlanRow old,Instant asOf,String trigger,boolean newDay){
        var active=learning.activeAssignment(userId,goal.id());
        if(active!=null && !"PLANNER".equals(active.assignmentSource()))return 0;
        if(active!=null && old.sessionId()!=null && active.id()!=old.sessionId())
            throw new ApiException(HttpStatus.CONFLICT,"PLANNER_SESSION_CHANGED",
                    "The active planner session changed.");
        List<PlannerStore.TaskRef> refs=store.taskRefs(old.id());
        Map<Long,LearningQueries.PlannerTaskState> states=new HashMap<>();
        for(long sessionId:refs.stream().map(PlannerStore.TaskRef::sessionId).distinct().toList())
            for(var state:learning.plannerTaskStates(userId,sessionId))states.put(state.id(),state);
        List<AdaptiveBudgetPolicyV1.Task> taskInput=new ArrayList<>();
        for(var ref:refs){
            var state=states.get(ref.taskId());
            if(state==null || state.templateVersionId()!=ref.templateVersionId())
                throw new ApiException(HttpStatus.CONFLICT,"PLANNER_TASK_CHANGED",
                        "A pinned planner task changed.");
            if(!newDay || Set.of("ASSIGNED","IN_PROGRESS","BLOCKED").contains(state.status()))
                taskInput.add(new AdaptiveBudgetPolicyV1.Task(ref.taskId(),state.status(),
                        state.plannedMinutes(),state.actualMinutes()));
        }
        int dailyMinutes=days.latestOverride(userId,goal.id(),day)
                .map(PlannerDayStore.OverrideRow::minutes).orElse(goal.defaultDailyMinutes());
        var budget=budgetPolicy.evaluate(dailyMinutes,taskInput);
        List<PlannerStore.TaskRef> preserved=refs.stream()
                .filter(ref->budget.preservedTaskIds().contains(ref.taskId())).toList();
        int occupied=(int)preserved.stream().filter(ref->!"COMPLETED".equals(states.get(ref.taskId()).status())).count();
        var base=planner.load(goal.goalTemplateId(),userId,dailyMinutes,asOf);
        if(base.graph().graphVersionId()!=old.graphVersionId())
            throw new ApiException(HttpStatus.CONFLICT,"INCOMPATIBLE_SNAPSHOT",
                    "The published graph changed; the old plan cannot be silently revised.");
        Set<Long> used=refs.stream().map(PlannerStore.TaskRef::templateVersionId).collect(Collectors.toSet());
        if(active!=null)learning.plannerTaskStates(userId,active.id()).forEach(task->used.add(task.templateVersionId()));
        List<PlannerPolicyV2.Variant> variants=learning.activeAdaptiveVariants(base.graph().graphVersionId())
                .stream().filter(v->!used.contains(v.templateVersionId()))
                .map(v->new PlannerPolicyV2.Variant(v.templateVersionId(),v.nodeId(),v.variantGroupKey(),
                        v.activityType(),v.difficulty(),v.minutes())).toList();
        Map<Long,LearningQueries.AdaptiveVariant> catalog=learning.activeAdaptiveVariants(base.graph().graphVersionId())
                .stream().collect(Collectors.toMap(LearningQueries.AdaptiveVariant::templateVersionId,v->v));
        List<PlannerPolicyV2.Failure> failures=history.recentTaskChecks(userId,goal.id(),
                base.graph().graphVersionId(),asOf).stream().filter(f->catalog.containsKey(f.templateVersionId()))
                .map(f->{var v=catalog.get(f.templateVersionId());return new PlannerPolicyV2.Failure(
                        f.attemptId(),v.nodeId(),v.variantGroupKey(),f.templateVersionId(),f.score());})
                .toList();
        int available=Math.max(1,budget.remainingMinutes());
        AdaptiveSnapshot input=new AdaptiveSnapshot(base,variants,failures,available,
                Math.max(0,3-occupied),budget.overBudgetMinutes(),trigger);
        var result=evaluate(input,planner);
        List<PlannerPolicyV1.Choice> selected=budget.remainingMinutes()==0?List.of():
                result.ranking().selected().stream().limit(input.maxNewTasks()).toList();
        String outcome=!selected.isEmpty()||occupied>0?"PLANNED"
                :budget.remainingMinutes()==0?"NO_SAFE_RECOMMENDATION":result.ranking().outcome();
        String payload=planner.encode(input);
        long snapshotId=store.snapshot(userId,goal.id(),base.graph().graphVersionId(),
                PlannerPolicyV2.VERSION,asOf,base.progress().digest(),base.review().digest(),
                PlannerService.digest(payload),payload,result.ranking().candidateCount(),
                result.ranking().limitedCount());
        store.candidates(snapshotId,result.ranking().topCandidates(),result.ranking().blockedBy());
        List<LearningQueries.PlannerSelection> selections=new ArrayList<>();
        List<Long> decisions=new ArrayList<>();
        int position=1;
        for(var choice:selected){
            long decision=store.decision(snapshotId,choice,result.ranking().alternatives(),asOf);
            decisions.add(decision);
            selections.add(new LearningQueries.PlannerSelection(decision,choice.variant().templateVersionId(),position++));
        }
        // No planner decision, snapshot, or historical item is rewritten. The Goal lock
        // serializes this status transition with other revisions and day commands.
        if(!newDay)store.supersede(old.id());
        Long sessionId=active==null?null:active.id();
        List<LearningQueries.AssignedTask> added=List.of();
        if(active!=null){
            added=learning.revisePlannerAssignment(userId,active.id(),base.graph().graphVersionId(),
                    budget.expirableTaskIds(),selections,asOf);
            if(selected.isEmpty() && occupied==0)sessionId=null;
        }else if(!selections.isEmpty()){
            sessionId=learning.assignAdaptivePlanner(userId,goal.id(),base.graph().graphVersionId(),
                    selections,asOf);
            added=learning.sessionTasks(userId,sessionId);
        }
        long planId=store.createPlan(userId,goal.id(),day,goal.timezone(),dailyMinutes,
                newDay?1:old.revision()+1,newDay?null:old.id(),snapshotId,sessionId,outcome,asOf);
        int carryPosition=1;
        for(var ref:preserved){
            var state=states.get(ref.taskId());
            store.addCarry(planId,carryPosition++,ref,state.status(),state.actualMinutes());
        }
        if(added.size()!=selected.size())throw new IllegalStateException("Adaptive assignment count changed");
        for(int i=0;i<selected.size();i++){
            var task=added.get(i);
            store.addItem(planId,i+1,decisions.get(i),task.id(),task.plannedMinutes());
        }
        return planId;
    }

    static PlannerPolicyV2.Result replay(ObjectMapper json,String payload,PlannerService planner){
        return evaluate(decode(json,payload),planner);
    }
    static AdaptiveSnapshot decode(ObjectMapper json,String payload){
        try{return json.readValue(payload,AdaptiveSnapshot.class);}
        catch(JsonProcessingException exception){throw new IllegalStateException("Adaptive snapshot is invalid",exception);}
    }
    private static PlannerPolicyV2.Result evaluate(AdaptiveSnapshot input,PlannerService planner){
        var source=planner.toPolicy(input.base(),input.remainingMinutes());
        return new PlannerPolicyV2().plan(new PlannerPolicyV2.Input(source,input.variants(),input.failures()));
    }
    public record AdaptiveSnapshot(PlannerService.SnapshotInput base,List<PlannerPolicyV2.Variant> variants,
            List<PlannerPolicyV2.Failure> failures,int remainingMinutes,int maxNewTasks,
            int overBudgetMinutes,String trigger) {}
    public record OverrideView(LocalDate learningDay,int availableMinutes,int revision,boolean replayed) {}
    public record ReplanStatusView(LocalDate learningDay,String status,String triggerKind,
            String resultCode,String errorCode,int attempts,boolean automaticReplanEnabled) {}
}
