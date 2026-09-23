package com.skillpath.progress.application;

import com.skillpath.knowledge.application.AssessmentKnowledgeQueries;
import com.skillpath.progress.domain.KnowledgeStatePolicyV1;
import com.skillpath.review.application.ReviewScheduleQueries;
import com.skillpath.shared.api.ApiException;
import com.skillpath.shared.localization.SupportedLocale;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ProgressService implements PlannerProgressQueries {
    private final ProgressStore store;private final AssessmentKnowledgeQueries knowledge;private final ReviewScheduleQueries reviewSchedules;private final Clock clock;private final KnowledgeStatePolicyV1 policy=new KnowledgeStatePolicyV1();
    public ProgressService(ProgressStore store,AssessmentKnowledgeQueries knowledge,ReviewScheduleQueries reviewSchedules,Clock clock){this.store=store;this.knowledge=knowledge;this.reviewSchedules=reviewSchedules;this.clock=clock;}
    @Transactional(readOnly=true) public Page states(long user,int limit,long after,SupportedLocale locale){validate(limit);Instant asOf=clock.instant();List<ProgressStore.StateRow> rows=store.states(user,limit+1,after);boolean more=rows.size()>limit;if(more)rows=rows.subList(0,limit);return new Page(enrich(user,rows,locale,asOf),more,more?Long.toString(rows.getLast().id()):null);}
    @Transactional(readOnly=true) public StateView state(long user,long node,SupportedLocale locale){Instant asOf=clock.instant();ProgressStore.StateRow row=store.state(user,node);if(row==null)throw new ApiException(HttpStatus.NOT_FOUND,"KNOWLEDGE_STATE_NOT_FOUND","Knowledge state was not found.");return enrich(user,List.of(row),locale,asOf).getFirst();}
    @Transactional(readOnly=true) public EvidencePage evidence(long user,long node,int limit,long after){validate(limit);if(store.state(user,node)==null)throw new ApiException(HttpStatus.NOT_FOUND,"KNOWLEDGE_STATE_NOT_FOUND","Knowledge state was not found.");var rows=store.evidencePage(user,node,limit+1,after);boolean more=rows.size()>limit;if(more)rows=rows.subList(0,limit);return new EvidencePage(rows,more,more?Long.toString(rows.getLast().id()):null);}
    @Transactional public int rebuild(){return store.rebuildAll(clock.instant(),policy);}

    @Override
    @Transactional(readOnly=true)
    public Snapshot snapshot(long userId,long graphVersionId,Set<Long> nodeIds,Instant projectionAsOf){
        if(nodeIds.size()>200 || projectionAsOf==null)throw new IllegalArgumentException("Invalid planner snapshot bounds");
        boolean compatible=true;
        List<Node> nodes=new java.util.ArrayList<>();
        for(long nodeId:nodeIds.stream().sorted().toList()){
            var state=store.state(userId,nodeId);
            if((state!=null && state.graphVersionId()!=graphVersionId)
                    || store.hasEvidenceOutsideGraph(userId,graphVersionId,nodeId))compatible=false;
            var projection=policy.project(store.evidenceForGraph(userId,graphVersionId,nodeId,projectionAsOf),projectionAsOf);
            nodes.add(new Node(nodeId,projection.effectiveMastery(),projection.confidence(),
                    projection.evidenceCount(),projection.effectiveStatus().name()));
        }
        String canonical=graphVersionId+"|"+nodes;
        return new Snapshot(KnowledgeStatePolicyV1.VERSION,
                digest(canonical),
                compatible,nodes);
    }
    private static String digest(String value){
        try{return java.util.HexFormat.of().formatHex(java.security.MessageDigest.getInstance("SHA-256")
                .digest(value.getBytes(java.nio.charset.StandardCharsets.UTF_8)));}
        catch(java.security.NoSuchAlgorithmException exception){throw new IllegalStateException(exception);}
    }
    private List<StateView> enrich(long user,List<ProgressStore.StateRow> rows,SupportedLocale locale,Instant asOf){if(rows.isEmpty())return List.of();Map<Long,List<ProgressStore.StateRow>> byGraph=rows.stream().collect(Collectors.groupingBy(ProgressStore.StateRow::graphVersionId));Map<Long,AssessmentKnowledgeQueries.NodeSummary> names=byGraph.entrySet().stream().flatMap(e->knowledge.nodeSummaries(e.getKey(),e.getValue().stream().map(ProgressStore.StateRow::nodeId).collect(Collectors.toSet()),locale).stream()).collect(Collectors.toMap(AssessmentKnowledgeQueries.NodeSummary::id,n->n));Map<Long,ReviewScheduleQueries.Schedule> schedules=byGraph.entrySet().stream().flatMap(e->reviewSchedules.schedules(user,e.getKey(),e.getValue().stream().map(ProgressStore.StateRow::nodeId).collect(Collectors.toSet()),asOf).stream()).collect(Collectors.toMap(ReviewScheduleQueries.Schedule::nodeId,s->s));return rows.stream().map(row->{var effective=policy.project(store.evidenceForGraph(user,row.graphVersionId(),row.nodeId(),asOf),asOf);var n=names.get(row.nodeId());var schedule=schedules.get(row.nodeId());return new StateView(row,n==null?"unknown":n.slug(),n==null?"Unknown":n.name(),effective.effectiveMastery(),effective.effectiveStatus().name(),schedule==null?null:schedule.dueAt());}).toList();}
    private void validate(int limit){if(limit<1||limit>100)throw new ApiException(HttpStatus.BAD_REQUEST,"INVALID_PAGE_LIMIT","Limit must be 1-100.");}
    public record StateView(ProgressStore.StateRow stored,String nodeSlug,String nodeName,java.math.BigDecimal effectiveMastery,String effectiveStatus,Instant nextReviewAt){}
    public record Page(List<StateView> items,boolean hasMore,String nextCursor){}
    public record EvidencePage(List<ProgressStore.EvidenceRow> items,boolean hasMore,String nextCursor){}
}
