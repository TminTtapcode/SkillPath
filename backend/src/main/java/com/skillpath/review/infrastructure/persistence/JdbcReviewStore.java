package com.skillpath.review.infrastructure.persistence;

import com.skillpath.review.application.ReviewStore;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository public class JdbcReviewStore implements ReviewStore {
    private final JdbcTemplate jdbc;public JdbcReviewStore(JdbcTemplate jdbc){this.jdbc=jdbc;}
    @Override public List<DueReview> due(long user,Instant now,int limit,long after){return jdbc.query("SELECT id,graph_version_id,knowledge_node_id,interval_index,due_at,policy_version FROM review_schedules WHERE user_id=? AND id>? AND status<>'PAUSED' AND due_at<=? ORDER BY due_at,id LIMIT ?",(rs,n)->new DueReview(rs.getLong(1),rs.getLong(2),rs.getLong(3),rs.getInt(4),rs.getTimestamp(5).toInstant(),rs.getString(6)),user,after,Timestamp.from(now),limit);}
}
