package com.skillpath.review.application;

import com.skillpath.knowledge.application.AssessmentKnowledgeQueries;
import com.skillpath.shared.api.ApiException;
import com.skillpath.shared.localization.SupportedLocale;
import java.time.Clock;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service public class ReviewService implements PlannerReviewQueries, ReviewScheduleQueries {
    private final ReviewStore store;private final AssessmentKnowledgeQueries knowledge;private final Clock clock;public ReviewService(ReviewStore store,AssessmentKnowledgeQueries knowledge,Clock clock){this.store=store;this.knowledge=knowledge;this.clock=clock;}
    @Transactional(readOnly=true) public Page due(long user,int limit,long after,SupportedLocale locale){if(limit<1||limit>100)throw new ApiException(HttpStatus.BAD_REQUEST,"INVALID_PAGE_LIMIT","Limit must be 1-100.");var rows=store.due(user,clock.instant(),limit+1,after);boolean more=rows.size()>limit;if(more)rows=rows.subList(0,limit);Map<Long,AssessmentKnowledgeQueries.NodeSummary> names=rows.stream().collect(Collectors.groupingBy(ReviewStore.DueReview::graphVersionId)).entrySet().stream().flatMap(e->knowledge.nodeSummaries(e.getKey(),e.getValue().stream().map(ReviewStore.DueReview::nodeId).collect(Collectors.toSet()),locale).stream()).collect(Collectors.toMap(AssessmentKnowledgeQueries.NodeSummary::id,n->n));List<Item> items=rows.stream().map(r->{var n=names.get(r.nodeId());return new Item(r,n==null?"unknown":n.slug(),n==null?"Unknown":n.name());}).toList();return new Page(items,more,more?Long.toString(rows.getLast().id()):null);}
    @Override
    @Transactional(readOnly=true)
    public List<ReviewScheduleQueries.Schedule> schedules(long userId,long graphVersionId,java.util.Set<Long> nodeIds,java.time.Instant asOf){
        if(nodeIds==null || nodeIds.size()>200 || asOf==null)throw new IllegalArgumentException("Invalid review schedule bounds");
        if(nodeIds.isEmpty())return List.of();
        return List.copyOf(store.schedules(userId,graphVersionId,java.util.Set.copyOf(nodeIds),asOf));
    }

    @Override
    @Transactional(readOnly=true)
    public Snapshot snapshot(long userId,long graphVersionId,java.util.Set<Long> nodeIds,java.time.Instant projectionAsOf){
        if(nodeIds.size()>200 || projectionAsOf==null)throw new IllegalArgumentException("Invalid review snapshot bounds");
        var rows=store.due(userId,projectionAsOf,201,0);
        if(rows.size()>200)throw new ApiException(HttpStatus.UNPROCESSABLE_ENTITY,
                "REVIEW_SNAPSHOT_TOO_LARGE","Review snapshot exceeds planner bounds.");
        boolean compatible=rows.stream().noneMatch(row->nodeIds.contains(row.nodeId())
                && row.graphVersionId()!=graphVersionId);
        List<Due> due=rows.stream().filter(row->nodeIds.contains(row.nodeId())
                && row.graphVersionId()==graphVersionId)
                .map(row->new Due(row.nodeId(),row.dueAt(),row.intervalIndex()))
                .sorted(java.util.Comparator.comparingLong(Due::nodeId)).toList();
        String canonical=graphVersionId+"|"+due;
        try{
            String digest=java.util.HexFormat.of().formatHex(java.security.MessageDigest.getInstance("SHA-256")
                    .digest(canonical.getBytes(java.nio.charset.StandardCharsets.UTF_8)));
            return new Snapshot(digest,compatible,due);
        }catch(java.security.NoSuchAlgorithmException exception){throw new IllegalStateException(exception);}
    }
    public record Item(ReviewStore.DueReview review,String nodeSlug,String nodeName){}public record Page(List<Item> items,boolean hasMore,String nextCursor){}
}
