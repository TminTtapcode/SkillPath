package com.skillpath.review.application;

import java.time.Instant;
import java.util.List;
import java.util.Set;

public interface ReviewStore {
    List<DueReview> due(long userId,Instant now,int limit,long afterId);
    List<ReviewScheduleQueries.Schedule> schedules(long userId, long graphVersionId, Set<Long> nodeIds, Instant asOf);
    record DueReview(long id,long graphVersionId,long nodeId,int intervalIndex,Instant dueAt,String policyVersion){}
}
