package com.skillpath.review.application;

import java.time.Instant;
import java.util.List;

public interface ReviewStore {
    List<DueReview> due(long userId,Instant now,int limit,long afterId);
    record DueReview(long id,long graphVersionId,long nodeId,int intervalIndex,Instant dueAt,String policyVersion){}
}
