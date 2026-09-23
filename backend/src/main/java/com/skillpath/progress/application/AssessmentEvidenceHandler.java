package com.skillpath.progress.application;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.skillpath.progress.domain.KnowledgeStatePolicyV1;
import com.skillpath.progress.domain.KnowledgeStatePolicyV1.Dimension;
import com.skillpath.shared.application.OutboxHandler;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.sql.Timestamp;
import java.time.Clock;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

@Component
public class AssessmentEvidenceHandler implements OutboxHandler {
    private final ProgressStore store;
    private final ObjectMapper mapper;
    private final JdbcTemplate jdbc;
    private final Clock clock;
    private final KnowledgeStatePolicyV1 policy = new KnowledgeStatePolicyV1();
    public AssessmentEvidenceHandler(ProgressStore store,ObjectMapper mapper,JdbcTemplate jdbc,Clock clock){this.store=store;this.mapper=mapper;this.jdbc=jdbc;this.clock=clock;}
    @Override public boolean supports(String owner,String type,int version){return owner.equals("assessment")&&type.equals("AssessmentEvidenceCreated")&&version==1;}
    @Override public void handle(OutboxEvent event){
        try{
            JsonNode p=mapper.readTree(event.payload());
            long user=id(p,"userId"),node=id(p,"knowledgeNodeId"),graph=id(p,"graphVersionId");
            store.lockProjection(user,graph,node,clock.instant());
            ProgressStore.SourceEvidence evidence=new ProgressStore.SourceEvidence(event.id(),event.eventKey(),"ASSESSMENT_EVIDENCE",p.path("evidenceId").asText(),user,id(p,"goalId"),graph,node,Dimension.valueOf(p.path("dimension").asText()),decimal(p,"score"),decimal(p,"reliability"),p.path("policyVersion").asText("assessment-objective-v1"),Instant.parse(p.path("observedAt").asText()));
            boolean added=store.append(evidence,clock.instant());
            if(!added)return;
            updateMisconceptions(p,evidence);
            var snapshot=store.saveProjection(user,graph,node,policy.project(store.evidence(user,node),clock.instant()),clock.instant());
            if(!snapshot.priorAcquisitionStatus().equals(snapshot.acquisitionStatus())||snapshot.priorMastery().compareTo(snapshot.mastery())!=0) emit(event,user,graph,node,snapshot);
        }catch(Exception exception){throw new IllegalStateException("INVALID_ASSESSMENT_EVIDENCE_EVENT",exception);}
    }
    private void emit(OutboxEvent source,long user,long graph,long node,ProgressStore.StateSnapshot state)throws Exception{
        Instant now=clock.instant();Map<String,Object> payload=new LinkedHashMap<>();payload.put("userId",Long.toString(user));payload.put("graphVersionId",Long.toString(graph));payload.put("knowledgeNodeId",Long.toString(node));payload.put("priorStatus",state.priorAcquisitionStatus());payload.put("status",state.acquisitionStatus());payload.put("mastery",state.mastery());payload.put("changedAt",now.toString());
        jdbc.update("INSERT INTO outbox_events(event_key,owner_module,aggregate_type,aggregate_id,event_type,event_version,payload,status,attempt_count,available_at,occurred_at,created_at,updated_at) VALUES(?, 'progress','UserKnowledge',?,'KnowledgeStateChanged',1,CAST(? AS JSON),'PENDING',0,?,?,?,?)", "knowledge-state:"+source.id(),Long.toString(node),mapper.writeValueAsString(payload),Timestamp.from(now),Timestamp.from(now),Timestamp.from(now),Timestamp.from(now));
    }
    private void updateMisconceptions(JsonNode payload,ProgressStore.SourceEvidence evidence){
        JsonNode codes=payload.path("misconceptionCodes");
        java.util.Set<String> observed=new java.util.HashSet<>();
        if(codes.isArray())for(JsonNode node:codes){String code=node.asText();Integer allowed=jdbc.queryForObject("SELECT COUNT(*) FROM misconception_definitions WHERE code=? AND active=TRUE",Integer.class,code);if(allowed==null||allowed!=1)throw new IllegalStateException("UNKNOWN_MISCONCEPTION_CODE");observed.add(code);List<BigDecimal> prior=jdbc.query("SELECT confidence FROM user_misconceptions WHERE user_id=? AND knowledge_node_id=? AND dimension=? AND misconception_code=? FOR UPDATE",(rs,n)->rs.getBigDecimal(1),evidence.userId(),evidence.nodeId(),evidence.dimension().name(),code);BigDecimal old=prior.isEmpty()?BigDecimal.ZERO:prior.getFirst();BigDecimal confidence=BigDecimal.ONE.subtract(BigDecimal.ONE.subtract(old).multiply(BigDecimal.ONE.subtract(evidence.reliability()))).setScale(4,RoundingMode.HALF_UP);Timestamp when=Timestamp.from(evidence.observedAt());jdbc.update("""
            INSERT INTO user_misconceptions(user_id,graph_version_id,knowledge_node_id,dimension,misconception_code,confidence,verification_count,status,first_observed_at,last_observed_at,resolved_at)
            VALUES(?,?,?,?,?,?,0,'ACTIVE',?,?,NULL)
            ON DUPLICATE KEY UPDATE confidence=VALUES(confidence),verification_count=0,status='ACTIVE',last_observed_at=VALUES(last_observed_at),resolved_at=NULL
            """,evidence.userId(),evidence.graphVersionId(),evidence.nodeId(),evidence.dimension().name(),code,confidence,when,when);}
        if(evidence.score().compareTo(new BigDecimal("0.80"))>=0&&evidence.reliability().compareTo(new BigDecimal("0.70"))>=0){List<String> active=jdbc.query("SELECT misconception_code FROM user_misconceptions WHERE user_id=? AND knowledge_node_id=? AND dimension=? AND status='ACTIVE' FOR UPDATE",(rs,n)->rs.getString(1),evidence.userId(),evidence.nodeId(),evidence.dimension().name());for(String code:active)if(!observed.contains(code)){jdbc.update("UPDATE user_misconceptions SET verification_count=verification_count+1,last_observed_at=?,status=CASE WHEN verification_count+1>=2 THEN 'RESOLVED' ELSE status END,resolved_at=CASE WHEN verification_count+1>=2 THEN ? ELSE resolved_at END WHERE user_id=? AND knowledge_node_id=? AND dimension=? AND misconception_code=?",Timestamp.from(evidence.observedAt()),Timestamp.from(evidence.observedAt()),evidence.userId(),evidence.nodeId(),evidence.dimension().name(),code);}}
    }
    private long id(JsonNode p,String key){return Long.parseLong(p.path(key).asText());}
    private BigDecimal decimal(JsonNode p,String key){return new BigDecimal(p.path(key).asText());}
}
