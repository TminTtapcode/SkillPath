package com.skillpath.review.application;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.skillpath.review.domain.ReviewIntervalPolicyV1;
import com.skillpath.shared.application.OutboxHandler;
import java.sql.Timestamp;
import java.time.Clock;
import java.time.Instant;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

@Component
public class KnowledgeStateChangedHandler implements OutboxHandler {
    private final JdbcTemplate jdbc;private final ObjectMapper mapper;private final Clock clock;private final ReviewIntervalPolicyV1 policy=new ReviewIntervalPolicyV1();
    public KnowledgeStateChangedHandler(JdbcTemplate jdbc,ObjectMapper mapper,Clock clock){this.jdbc=jdbc;this.mapper=mapper;this.clock=clock;}
    @Override public boolean supports(String owner,String type,int version){return owner.equals("progress")&&type.equals("KnowledgeStateChanged")&&version==1;}
    @Override public void handle(OutboxEvent event){try{JsonNode p=mapper.readTree(event.payload());if(!p.path("status").asText().equals("MASTERED"))return;long user=Long.parseLong(p.path("userId").asText()),graph=Long.parseLong(p.path("graphVersionId").asText()),node=Long.parseLong(p.path("knowledgeNodeId").asText());Instant now=clock.instant(),due=now.plus(policy.firstInterval());jdbc.update("INSERT INTO review_schedules(user_id,graph_version_id,knowledge_node_id,policy_version,interval_index,due_at,status,version,created_at,updated_at) VALUES(?,?,?, ?,0,?,'SCHEDULED',0,?,?) ON DUPLICATE KEY UPDATE due_at=LEAST(due_at,VALUES(due_at)),status='SCHEDULED',updated_at=VALUES(updated_at)",user,graph,node,ReviewIntervalPolicyV1.VERSION,Timestamp.from(due),Timestamp.from(now),Timestamp.from(now));jdbc.update("UPDATE user_knowledge SET next_review_at=?,updated_at=? WHERE user_id=? AND knowledge_node_id=?",Timestamp.from(due),Timestamp.from(now),user,node);}catch(Exception e){throw new IllegalStateException("INVALID_KNOWLEDGE_STATE_EVENT",e);}}
}
