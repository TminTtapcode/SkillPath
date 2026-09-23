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
    @Override public boolean supports(String owner,String type,int version){return owner.equals("assessment")&&type.equals("AssessmentEvidenceCreated")&&(version==1||version==2);}
    @Override public void handle(OutboxEvent event){
        try{
            JsonNode p=mapper.readTree(event.payload());
            long user=id(p,"userId"),node=id(p,"knowledgeNodeId"),graph=id(p,"graphVersionId");
            Long projectionId=event.eventVersion()==2?lockAttempt(p):null;
            store.lockProjection(user,graph,node,clock.instant());
            ProgressStore.SourceEvidence evidence=new ProgressStore.SourceEvidence(event.id(),event.eventKey(),"ASSESSMENT_EVIDENCE",p.path("evidenceId").asText(),user,id(p,"goalId"),graph,node,Dimension.valueOf(p.path("dimension").asText()),decimal(p,"score"),decimal(p,"reliability"),p.path("policyVersion").asText("assessment-objective-v1"),Instant.parse(p.path("observedAt").asText()));
            boolean added=store.append(evidence,clock.instant());
            if(!added)return;
            updateMisconceptions(p,evidence);
            var snapshot=store.saveProjection(user,graph,node,policy.project(store.evidence(user,node),clock.instant()),clock.instant());
            if(event.eventVersion()==2)acceptAttemptEvidence(event,p,projectionId,snapshot.acquisitionStatus(),evidence);
            else if(!snapshot.priorAcquisitionStatus().equals(snapshot.acquisitionStatus())||snapshot.priorMastery().compareTo(snapshot.mastery())!=0) emit(event,user,graph,node,snapshot);
        }catch(Exception exception){throw new IllegalStateException("INVALID_ASSESSMENT_EVIDENCE_EVENT",exception);}
    }
    private long lockAttempt(JsonNode p){
        String kind=p.path("attemptKind").asText();
        if(!java.util.Set.of("DIAGNOSTIC","TASK_CHECK").contains(kind)
                || !p.path("replanEligible").isBoolean())throw new IllegalArgumentException("Invalid attempt metadata");
        int expected=p.path("expectedEvidenceCount").asInt(0);
        if(expected<1||expected>20)throw new IllegalArgumentException("Invalid attempt evidence count");
        long attempt=id(p,"attemptId"),user=id(p,"userId"),goal=id(p,"goalId"),graph=id(p,"graphVersionId");
        Instant now=clock.instant();
        jdbc.update("INSERT INTO progress_attempt_projections(attempt_kind,attempt_id,user_id,goal_id,graph_version_id,expected_count,replan_eligible,created_at) VALUES(?,?,?,?,?,?,?,?) ON DUPLICATE KEY UPDATE id=id",
                kind,attempt,user,goal,graph,expected,p.path("replanEligible").booleanValue(),Timestamp.from(now));
        var rows=jdbc.query("SELECT id,user_id,goal_id,graph_version_id,expected_count,replan_eligible FROM progress_attempt_projections WHERE attempt_kind=? AND attempt_id=? FOR UPDATE",
                (rs,n)->new AttemptProjection(rs.getLong(1),rs.getLong(2),rs.getLong(3),rs.getLong(4),rs.getInt(5),rs.getBoolean(6)),kind,attempt);
        if(rows.size()!=1)throw new IllegalStateException("Attempt projection receipt missing");
        var row=rows.getFirst();
        if(row.userId()!=user||row.goalId()!=goal||row.graphVersionId()!=graph||row.expectedCount()!=expected
                ||row.replanEligible()!=p.path("replanEligible").booleanValue())
            throw new IllegalArgumentException("Attempt metadata changed");
        return row.id();
    }
    private void acceptAttemptEvidence(OutboxEvent event,JsonNode p,long projectionId,String status,
                                       ProgressStore.SourceEvidence evidence)throws Exception{
        Instant now=clock.instant();
        jdbc.update("INSERT INTO progress_attempt_evidence_receipts(projection_id,source_event_id,evidence_id,knowledge_node_id,dimension,score,reliability,projected_status,projected_at) VALUES(?,?,?,?,?,?,?,?,?)",
                projectionId,event.id(),id(p,"evidenceId"),evidence.nodeId(),evidence.dimension().name(),
                evidence.score(),evidence.reliability(),status,Timestamp.from(now));
        int count=jdbc.queryForObject("SELECT COUNT(*) FROM progress_attempt_evidence_receipts WHERE projection_id=?",Integer.class,projectionId);
        int expected=p.path("expectedEvidenceCount").asInt();
        if(count>expected)throw new IllegalStateException("Attempt evidence exceeded expected count");
        if(count!=expected)return;
        List<ReceiptEvidence> receipts=jdbc.query("SELECT evidence_id,knowledge_node_id,dimension,score,reliability FROM progress_attempt_evidence_receipts WHERE projection_id=? ORDER BY evidence_id",
                (rs,n)->new ReceiptEvidence(rs.getLong(1),rs.getLong(2),rs.getString(3),
                        rs.getBigDecimal(4),rs.getBigDecimal(5)),projectionId);
        List<Map<String,Object>> accepted=receipts.stream().map(item->{
            var finalState=store.state(evidence.userId(),item.nodeId());
            if(finalState==null || finalState.graphVersionId()!=evidence.graphVersionId())
                throw new IllegalStateException("Missing compatible final state for accepted evidence");
            return Map.<String,Object>of("evidenceId",Long.toString(item.evidenceId()),
                    "knowledgeNodeId",Long.toString(item.nodeId()),"dimension",item.dimension(),
                    "score",item.score(),"reliability",item.reliability(),"status",finalState.status());
        }).toList();
        String kind=p.path("attemptKind").asText();long attempt=id(p,"attemptId");
        Map<String,Object> payload=new LinkedHashMap<>();
        payload.put("attemptKind",kind);payload.put("attemptId",Long.toString(attempt));
        payload.put("userId",Long.toString(evidence.userId()));payload.put("goalId",Long.toString(evidence.goalId()));
        payload.put("graphVersionId",Long.toString(evidence.graphVersionId()));
        payload.put("expectedEvidenceCount",expected);payload.put("replanEligible",p.path("replanEligible").booleanValue());
        payload.put("evidence",accepted);payload.put("acceptedAt",now.toString());
        jdbc.update("INSERT INTO outbox_events(event_key,owner_module,aggregate_type,aggregate_id,event_type,event_version,payload,status,attempt_count,available_at,occurred_at,created_at,updated_at) VALUES(?,'progress','AssessmentAttempt',?,'EvidenceAccepted',1,CAST(? AS JSON),'PENDING',0,?,?,?,?)",
                "evidence-accepted:"+kind+":"+attempt,Long.toString(attempt),mapper.writeValueAsString(payload),
                Timestamp.from(now),Timestamp.from(now),Timestamp.from(now),Timestamp.from(now));
    }
    private record AttemptProjection(long id,long userId,long goalId,long graphVersionId,int expectedCount,boolean replanEligible){}
    private record ReceiptEvidence(long evidenceId,long nodeId,String dimension,BigDecimal score,BigDecimal reliability){}
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
