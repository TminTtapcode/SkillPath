package com.skillpath.planner.application;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.skillpath.goal.application.GoalQueries;
import com.skillpath.knowledge.application.PlannerKnowledgeQueries;
import com.skillpath.learning.application.LearningQueries;
import com.skillpath.planner.domain.PlannerPolicyV1;
import com.skillpath.progress.application.PlannerProgressQueries;
import com.skillpath.review.application.PlannerReviewQueries;
import com.skillpath.shared.api.ApiException;
import com.skillpath.shared.localization.SupportedLocale;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Base64;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

@Service
public class PlannerService {
    private static final Pattern KEY=Pattern.compile("[A-Za-z0-9._:-]{1,128}");
    private final GoalQueries goals;
    private final PlannerKnowledgeQueries knowledge;
    private final PlannerProgressQueries progress;
    private final PlannerReviewQueries review;
    private final LearningQueries learning;
    private final PlannerStore store;
    private final ObjectMapper json;
    private final Clock clock;
    private final PlannerPolicyV1 policy=new PlannerPolicyV1();

    public PlannerService(GoalQueries goals,PlannerKnowledgeQueries knowledge,
            PlannerProgressQueries progress,PlannerReviewQueries review,LearningQueries learning,
            PlannerStore store,ObjectMapper json,Clock clock){
        this.goals=goals;this.knowledge=knowledge;this.progress=progress;this.review=review;
        this.learning=learning;this.store=store;this.json=json;this.clock=clock;
    }

    @Transactional(isolation=Isolation.REPEATABLE_READ)
    public CommandOutcome generate(long userId,String key,SupportedLocale locale){return command(userId,key,false,locale);}

    @Transactional(isolation=Isolation.REPEATABLE_READ)
    public CommandOutcome revise(long userId,String key,SupportedLocale locale){return command(userId,key,true,locale);}

    private CommandOutcome command(long userId,String key,boolean revision,SupportedLocale locale){
        if(key==null || !KEY.matcher(key).matches())
            throw new ApiException(HttpStatus.BAD_REQUEST,"INVALID_IDEMPOTENCY_KEY","Idempotency key is invalid.");
        String command=revision?"REVISE":"GENERATE";
        String hash=digest(command+"|empty-body");
        var goal=goals.planningGoalForUser(userId,true);
        Instant now=clock.instant().truncatedTo(java.time.temporal.ChronoUnit.MICROS);
        LocalDate day=now.atZone(ZoneId.of(goal.timezone())).toLocalDate();
        PlannerStore.Receipt prior=store.receipt(userId,key).orElse(null);
        if(prior!=null)return replay(userId,prior,command,hash,locale);
        PlannerStore.PlanRow existing=store.current(userId,goal.id(),day).orElse(null);
        if(!revision && existing!=null){
            store.addReceipt(userId,key,command,hash,existing.id(),now);
            return new CommandOutcome(view(userId,existing,locale),false,true);
        }
        if(revision && existing==null)
            throw new ApiException(HttpStatus.CONFLICT,"TODAY_NOT_GENERATED","Generate a plan before revising it.");
        var active=learning.activeAssignment(userId,goal.id());
        if(active!=null && (!revision || !active.assignmentSource().equals("PLANNER")
                || existing==null || existing.sessionId()==null || active.id()!=existing.sessionId()))
            throw new ApiException(HttpStatus.CONFLICT,"ACTIVE_LEARNING_SESSION",
                    "An active learning session must be finished first.");
        if(revision && existing.sessionId()!=null){
            if(active==null)throw new ApiException(HttpStatus.CONFLICT,"PLANNER_REVISION_BLOCKED",
                    "The old planner session is no longer active.");
            learning.supersedeUnstarted(userId,existing.sessionId(),now);
        }
        if(revision)store.supersede(existing.id());
        SnapshotInput snapshot=load(goal.goalTemplateId(),userId,goal.defaultDailyMinutes(),now);
        PlannerPolicyV1.Result result=policy.plan(toPolicy(snapshot,goal.defaultDailyMinutes()));
        String payload=encode(snapshot);
        long snapshotId=store.snapshot(userId,goal.id(),snapshot.graph().graphVersionId(),now,
                snapshot.progress().digest(),snapshot.review().digest(),digest(payload),payload,
                result.candidateCount(),result.limitedCount());
        store.candidates(snapshotId,result.topCandidates(),result.blockedBy());
        List<LearningQueries.PlannerSelection> selected=new ArrayList<>();
        List<Long> decisionIds=new ArrayList<>();
        int position=1;
        for(var choice:result.selected()){
            long decisionId=store.decision(snapshotId,choice,result.alternatives(),now);
            decisionIds.add(decisionId);
            selected.add(new LearningQueries.PlannerSelection(decisionId,
                    choice.variant().templateVersionId(),position++));
        }
        Long sessionId=selected.isEmpty()?null:learning.assignPlanner(userId,goal.id(),
                snapshot.graph().graphVersionId(),selected,now);
        long planId=store.createPlan(userId,goal.id(),day,goal.timezone(),goal.defaultDailyMinutes(),
                existing==null?1:existing.revision()+1,existing==null?null:existing.id(),
                snapshotId,sessionId,result.outcome(),now);
        if(sessionId!=null){
            Map<Integer,LearningQueries.AssignedTask> tasks=learning.sessionTasks(userId,sessionId).stream()
                    .collect(Collectors.toMap(LearningQueries.AssignedTask::position,task->task));
            for(int i=0;i<decisionIds.size();i++){
                var task=tasks.get(i+1);
                if(task==null)throw new IllegalStateException("Missing assigned planner task");
                store.addItem(planId,i+1,decisionIds.get(i),task.id(),task.plannedMinutes());
            }
        }
        store.addReceipt(userId,key,command,hash,planId,now);
        return new CommandOutcome(view(userId,store.plan(userId,planId).orElseThrow(),
                locale),true,false);
    }

    private CommandOutcome replay(long userId,PlannerStore.Receipt receipt,String command,String hash,
                                  SupportedLocale locale){
        if(!receipt.command().equals(command)||!receipt.hash().equals(hash))
            throw new ApiException(HttpStatus.CONFLICT,"IDEMPOTENCY_KEY_REUSED",
                    "This key was used for another planner command.");
        return new CommandOutcome(view(userId,store.plan(userId,receipt.planId()).orElseThrow(),
                locale),false,true);
    }

    @Transactional(readOnly=true,isolation=Isolation.REPEATABLE_READ)
    public TodayView today(long userId,SupportedLocale locale){
        var goal=goals.planningGoalForUser(userId,false);
        LocalDate day=clock.instant().atZone(ZoneId.of(goal.timezone())).toLocalDate();
        var current=store.current(userId,goal.id(),day).orElse(null);
        var active=learning.activeAssignment(userId,goal.id());
        String manual=active!=null && active.assignmentSource().equals("LEARNER_SELECTED")
                ?Long.toString(active.id()):null;
        if(current==null)return new TodayView(null,"NOT_GENERATED",null,0,goal.defaultDailyMinutes(),
                null,null,null,null,manual,List.of(),List.of());
        TodayView plan=view(userId,current,locale);
        return new TodayView(plan.planId(),plan.outcome(),plan.reasonCode(),plan.revision(),plan.budgetMinutes(),
                plan.graphVersionId(),plan.plannerPolicyVersion(),plan.projectionAsOf(),
                plan.sessionId(),manual,
                plan.items(),plan.alternatives());
    }

    @Transactional(readOnly=true)
    public TodayView historical(long userId,long planId,SupportedLocale locale){
        return view(userId,store.plan(userId,planId).orElseThrow(()->new ApiException(
                HttpStatus.NOT_FOUND,"TODAY_PLAN_NOT_FOUND","Plan was not found.")),locale);
    }

    private TodayView view(long userId,PlannerStore.PlanRow row,SupportedLocale locale){
        SnapshotInput snapshot=decode(row.inputPayload());
        Map<Long,PlannerKnowledgeQueries.Node> nodes=snapshot.graph().nodes().stream()
                .collect(Collectors.toMap(PlannerKnowledgeQueries.Node::id,node->node));
        Map<Long,LearningQueries.AssignedTask> tasks=row.sessionId()==null?Map.of():
                learning.sessionTasks(userId,row.sessionId()).stream()
                        .collect(Collectors.toMap(LearningQueries.AssignedTask::id,task->task));
        List<ItemView> items=store.items(row.id()).stream().map(item->{
            var task=tasks.get(item.taskId());
            var node=nodes.get(item.nodeId());
            return new ItemView(Long.toString(item.taskId()),Long.toString(item.nodeId()),
                    node==null?"Unknown":locale.requiresTranslation()?node.nameVi():node.name(),
                    task==null?"Unavailable":locale.requiresTranslation()?task.titleVi():task.titleEn(),
                    task==null?"UNKNOWN":task.status(),item.minutes(),item.score(),
                    decodeReasons(item.reasons()));
        }).toList();
        int remaining=row.budget()-items.stream().mapToInt(ItemView::minutes).sum();
        var ranking=policy.plan(toPolicy(snapshot,row.budget()));
        return new TodayView(Long.toString(row.id()),row.outcome(),ranking.reasonCode(),row.revision(),row.budget(),
                Long.toString(row.graphVersionId()),PlannerPolicyV1.VERSION,row.projectionAsOf(),
                row.sessionId()==null?null:Long.toString(row.sessionId()),null,items,
                ranking.alternatives().stream().map(choice->nodes.get(choice.nodeId()))
                        .filter(java.util.Objects::nonNull)
                        .map(node->locale.requiresTranslation()?node.nameVi():node.name()).toList());
    }

    @Transactional(readOnly=true,isolation=Isolation.REPEATABLE_READ)
    public RoadmapView roadmap(long userId,int limit,String cursor,SupportedLocale locale){
        if(limit<1 || limit>100)
            throw new ApiException(HttpStatus.BAD_REQUEST,"INVALID_PAGE_LIMIT","Limit must be 1-100.");
        RoadmapCursor after=decodeCursor(cursor);
        var goal=goals.planningGoalForUser(userId,false);
        Instant now=clock.instant().truncatedTo(java.time.temporal.ChronoUnit.MICROS);
        LocalDate day=now.atZone(ZoneId.of(goal.timezone())).toLocalDate();
        var current=store.current(userId,goal.id(),day).orElse(null);
        SnapshotInput pinned=current==null?load(goal.goalTemplateId(),userId,goal.defaultDailyMinutes(),
                after==null?now:after.projectionAsOf())
                :decode(current.inputPayload());
        String planId=current==null?"0":Long.toString(current.id());
        int revision=current==null?0:current.revision();
        if(after!=null && (after.userId()!=userId || after.goalId()!=goal.id()
                || after.graphVersionId()!=pinned.graph().graphVersionId()
                || !after.progressDigest().equals(pinned.progress().digest())
                || !after.reviewDigest().equals(pinned.review().digest())
                || !after.planId().equals(planId) || after.revision()!=revision
                || !after.projectionAsOf().equals(pinned.projectionAsOf())))
            throw new ApiException(HttpStatus.CONFLICT,"ROADMAP_CURSOR_STALE",
                    "Roadmap snapshot changed; reload from the first page.");
        boolean stale=false;
        if(current!=null){
            try {
                var fresh=load(goal.goalTemplateId(),userId,goal.defaultDailyMinutes(),now);
                stale=fresh.graph().graphVersionId()!=pinned.graph().graphVersionId()
                        || !fresh.progress().digest().equals(pinned.progress().digest())
                        || !fresh.review().digest().equals(pinned.review().digest());
            } catch (ApiException exception) {
                if (!"INCOMPATIBLE_SNAPSHOT".equals(exception.code())) throw exception;
                stale=true;
            }
        }
        PlannerPolicyV1.Result ranking=policy.plan(toPolicy(pinned,
                current==null?goal.defaultDailyMinutes():current.budget()));
        Map<Long,PlannerProgressQueries.Node> state=pinned.progress().nodes().stream()
                .collect(Collectors.toMap(PlannerProgressQueries.Node::nodeId,node->node));
        Set<Long> currentNodes=current==null?Set.of():store.items(current.id()).stream()
                .map(PlannerStore.ItemRow::nodeId).collect(Collectors.toSet());
        boolean isStale=stale;
        int offset=after==null?0:after.offset();
        if(offset<0 || (after!=null && offset>=pinned.graph().nodes().size()))
            throw new ApiException(HttpStatus.BAD_REQUEST,"INVALID_ROADMAP_CURSOR","Roadmap cursor is invalid.");
        List<PlannerKnowledgeQueries.Node> page=pinned.graph().nodes().subList(offset,
                Math.min(offset+limit,pinned.graph().nodes().size()));
        List<RoadmapNode> nodes=page.stream().map(node->{
            var found=state.get(node.id());
            String status=found==null?"UNKNOWN":found.status();
            List<Long> blocked=ranking.blockedBy().getOrDefault(node.id(),List.of());
            return new RoadmapNode(Long.toString(node.id()),node.slug(),
                    locale.requiresTranslation()?node.nameVi():node.name(),status,
                    !isStale && currentNodes.contains(node.id()),blocked.isEmpty(),
                    blocked.stream().map(Object::toString).toList());
        }).toList();
        Set<Long> pageIds=new HashSet<>();
        page.forEach(node->pageIds.add(node.id()));
        List<RoadmapEdge> edges=pinned.graph().edges().stream()
                .filter(edge->pageIds.contains(edge.targetId()))
                .map(edge->new RoadmapEdge(Long.toString(edge.sourceId()),Long.toString(edge.targetId()),
                        edge.type(),edge.strength())).toList();
        boolean hasMore=offset+page.size()<pinned.graph().nodes().size();
        String nextCursor=hasMore?encodeCursor(new RoadmapCursor(userId,goal.id(),
                pinned.graph().graphVersionId(),pinned.progress().digest(),pinned.review().digest(),
                planId,revision,pinned.projectionAsOf(),offset+page.size())):null;
        return new RoadmapView(Long.toString(pinned.graph().graphVersionId()),
                pinned.progress().policyVersion(),pinned.progress().digest(),pinned.review().digest(),
                PlannerPolicyV1.VERSION,pinned.projectionAsOf(),
                current==null?null:planId,revision,stale,hasMore,nextCursor,nodes,edges);
    }

    private static String encodeCursor(RoadmapCursor value){
        String raw=value.userId()+"|"+value.goalId()+"|"+value.graphVersionId()+"|"
                +value.progressDigest()+"|"+value.reviewDigest()+"|"+value.planId()+"|"
                +value.revision()+"|"+value.projectionAsOf()+"|"+value.offset();
        return Base64.getUrlEncoder().withoutPadding().encodeToString(raw.getBytes(StandardCharsets.UTF_8));
    }

    private static RoadmapCursor decodeCursor(String value){
        if(value==null)return null;
        if(value.isBlank() || value.length()>1024)
            throw new ApiException(HttpStatus.BAD_REQUEST,"INVALID_ROADMAP_CURSOR","Roadmap cursor is invalid.");
        try{
            String[] fields=new String(Base64.getUrlDecoder().decode(value),StandardCharsets.UTF_8)
                    .split("\\|",-1);
            if(fields.length!=9)throw new IllegalArgumentException("Invalid cursor field count");
            return new RoadmapCursor(Long.parseLong(fields[0]),Long.parseLong(fields[1]),
                    Long.parseLong(fields[2]),fields[3],fields[4],fields[5],
                    Integer.parseInt(fields[6]),Instant.parse(fields[7]),Integer.parseInt(fields[8]));
        }catch(IllegalArgumentException | java.time.DateTimeException exception){
            throw new ApiException(HttpStatus.BAD_REQUEST,"INVALID_ROADMAP_CURSOR","Roadmap cursor is invalid.");
        }
    }

    private SnapshotInput load(long goalTemplateId,long userId,int budget,Instant asOf){
        var graph=knowledge.planningGraph(goalTemplateId);
        Set<Long> ids=graph.nodes().stream().map(PlannerKnowledgeQueries.Node::id).collect(Collectors.toSet());
        var state=progress.snapshot(userId,graph.graphVersionId(),ids,asOf);
        var due=review.snapshot(userId,graph.graphVersionId(),ids,asOf);
        if(!state.compatible() || !due.compatible())
            throw new ApiException(HttpStatus.CONFLICT,"INCOMPATIBLE_SNAPSHOT",
                    "Published graph and learner state are not compatible.");
        var variants=learning.activeVariants(graph.graphVersionId());
        return new SnapshotInput(asOf,graph,state,due,variants);
    }

    private PlannerPolicyV1.Input toPolicy(SnapshotInput source,int budget){
        return new PlannerPolicyV1.Input(source.projectionAsOf(),budget,
                source.graph().nodes().stream().map(node->new PlannerPolicyV1.Node(node.id(),
                        node.status(),node.relevance(),node.requiredMastery(),node.topologicalOrder())).toList(),
                source.graph().edges().stream().map(edge->new PlannerPolicyV1.Edge(edge.sourceId(),
                        edge.targetId(),edge.type(),edge.strength())).toList(),
                source.progress().nodes().stream().map(node->new PlannerPolicyV1.State(node.nodeId(),
                        node.effectiveMastery(),node.confidence(),node.evidenceCount(),node.status())).toList(),
                source.review().due().stream().map(due->new PlannerPolicyV1.Due(due.nodeId(),due.dueAt())).toList(),
                source.variants().stream().map(variant->new PlannerPolicyV1.Variant(
                        variant.templateVersionId(),variant.nodeId(),variant.activityType(),
                        variant.difficulty(),variant.minutes())).toList());
    }

    private String encode(Object value){try{return json.writeValueAsString(value);}
        catch(JsonProcessingException exception){throw new IllegalStateException("Planner serialization failed",exception);}}
    private SnapshotInput decode(String value){try{return json.readValue(value,SnapshotInput.class);}
        catch(JsonProcessingException exception){throw new IllegalStateException("Planner snapshot is invalid",exception);}}
    private List<String> decodeReasons(String value){try{return json.readValue(value,
            new com.fasterxml.jackson.core.type.TypeReference<List<String>>(){});}
        catch(JsonProcessingException exception){throw new IllegalStateException("Planner reasons are invalid",exception);}}
    private static String digest(String value){try{return java.util.HexFormat.of().formatHex(
            MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8)));}
        catch(NoSuchAlgorithmException exception){throw new IllegalStateException(exception);}}

    public record SnapshotInput(Instant projectionAsOf,PlannerKnowledgeQueries.PlanningGraph graph,
            PlannerProgressQueries.Snapshot progress,PlannerReviewQueries.Snapshot review,
            List<LearningQueries.PlannerVariant> variants) {}
    public record ItemView(String taskId,String nodeId,String nodeName,String title,String status,
            int minutes,BigDecimal priorityScore,List<String> reasons) {}
    public record TodayView(String planId,String outcome,String reasonCode,int revision,int budgetMinutes,String graphVersionId,
            String plannerPolicyVersion,Instant projectionAsOf,String sessionId,String activeManualSessionId,
            List<ItemView> items,List<String> alternatives) {}
    public record RoadmapNode(String id,String slug,String name,String knowledgeStatus,boolean current,
            boolean ready,List<String> blockedBy) {}
    public record RoadmapEdge(String sourceId,String targetId,String type,BigDecimal strength) {}
    public record RoadmapView(String graphVersionId,String knowledgeStatePolicyVersion,
            String progressDigest,String reviewDigest,String plannerPolicyVersion,
            Instant projectionAsOf,String planId,int revision,
            boolean stale,boolean hasMore,String nextCursor,List<RoadmapNode> nodes,List<RoadmapEdge> edges) {}
    private record RoadmapCursor(long userId,long goalId,long graphVersionId,
            String progressDigest,String reviewDigest,String planId,int revision,
            Instant projectionAsOf,int offset) {}
    public record CommandOutcome(TodayView plan,boolean created,boolean replayed) {}
}
