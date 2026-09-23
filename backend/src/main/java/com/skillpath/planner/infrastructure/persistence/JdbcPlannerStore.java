package com.skillpath.planner.infrastructure.persistence;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.skillpath.planner.application.PlannerStore;
import com.skillpath.planner.domain.PlannerPolicyV1;
import java.sql.PreparedStatement;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.stereotype.Repository;

@Repository
public class JdbcPlannerStore implements PlannerStore {
    private final JdbcTemplate jdbc;
    private final ObjectMapper json;
    public JdbcPlannerStore(JdbcTemplate jdbc,ObjectMapper json){this.jdbc=jdbc;this.json=json;}

    @Override public Optional<PlanRow> current(long userId,long goalId,LocalDate day){
        return first(jdbc.query(planSql()+" WHERE p.user_id=? AND p.goal_id=? AND p.learning_day=? "
                        +"AND p.status IN ('CURRENT','NO_SAFE')",this::planRow,userId,goalId,day));
    }
    @Override public Optional<PlanRow> plan(long userId,long planId){
        return first(jdbc.query(planSql()+" WHERE p.user_id=? AND p.id=?",this::planRow,userId,planId));
    }
    private String planSql(){return "SELECT p.id,p.user_id,p.goal_id,p.learning_day,p.timezone,p.budget_minutes,"
            +"p.revision,p.session_id,p.status,p.outcome_code,s.graph_version_id,s.projection_as_of,"
            +"s.progress_digest,s.review_digest,s.input_payload FROM daily_plans p "
            +"JOIN planning_snapshots s ON s.id=p.snapshot_id";}
    private PlanRow planRow(java.sql.ResultSet rs,int row)throws java.sql.SQLException{
        long session=rs.getLong("session_id");
        boolean absentSession=rs.wasNull();
        return new PlanRow(rs.getLong("id"),rs.getLong("user_id"),rs.getLong("goal_id"),
                rs.getObject("learning_day",LocalDate.class),rs.getString("timezone"),
                rs.getInt("budget_minutes"),rs.getInt("revision"),absentSession?null:session,
                rs.getString("status"),rs.getString("outcome_code"),rs.getLong("graph_version_id"),
                rs.getTimestamp("projection_as_of").toInstant(),rs.getString("progress_digest"),
                rs.getString("review_digest"),rs.getString("input_payload"));
    }
    @Override public Optional<Receipt> receipt(long userId,String key){
        return first(jdbc.query("SELECT command_name,request_hash,plan_id FROM planner_command_receipts "
                        +"WHERE user_id=? AND idempotency_key=?",
                (rs,row)->new Receipt(rs.getString(1),rs.getString(2),rs.getLong(3)),userId,key));
    }
    @Override public long snapshot(long userId,long goalId,long graphVersionId,Instant asOf,
            String progressDigest,String reviewDigest,String inputHash,String inputPayload,
            int candidateCount,int limitedCount){
        return insert("INSERT INTO planning_snapshots(user_id,goal_id,graph_version_id,policy_version,"
                        +"projection_as_of,progress_digest,review_digest,input_hash,input_payload,"
                        +"candidate_count,limited_candidate_count,created_at) VALUES(?,?,?,'planner-v1',?,?,?,?,?,?,?,?)",
                userId,goalId,graphVersionId,Timestamp.from(asOf),progressDigest,reviewDigest,
                inputHash,inputPayload,candidateCount,limitedCount,Timestamp.from(asOf));
    }
    @Override public long decision(long snapshotId,PlannerPolicyV1.Choice choice,
                                   List<PlannerPolicyV1.Choice> alternatives,Instant now){
        return insert("INSERT INTO planner_decisions(snapshot_id,node_id,template_version_id,priority_score,"
                        +"signal_payload,reason_payload,alternatives_payload,created_at) VALUES(?,?,?,?,?,?,?,?)",
                snapshotId,choice.nodeId(),choice.variant().templateVersionId(),choice.score(),
                encode(choice),encode(choice.reasons()),encode(alternatives),Timestamp.from(now));
    }
    @Override public void candidates(long snapshotId,List<PlannerPolicyV1.Choice> topCandidates,
                                     java.util.Map<Long,List<Long>> blockedBy){
        int position=1;
        for(var choice:topCandidates){
            insert("INSERT INTO planner_decision_candidates(snapshot_id,position,node_id,template_version_id,"
                            +"score,eligible,explanation) VALUES(?,?,?,?,?,TRUE,?)",
                    snapshotId,position++,choice.nodeId(),choice.variant().templateVersionId(),
                    choice.score(),encode(choice));
        }
        for(var entry:blockedBy.entrySet().stream().sorted(java.util.Map.Entry.comparingByKey()).toList()){
            if(entry.getValue().isEmpty())continue;
            insert("INSERT INTO planner_decision_candidates(snapshot_id,position,node_id,template_version_id,"
                            +"score,eligible,explanation) VALUES(?,?,?,NULL,NULL,FALSE,?)",
                    snapshotId,position++,entry.getKey(),encode(entry.getValue()));
        }
    }
    @Override public long createPlan(long userId,long goalId,LocalDate day,String timezone,int budget,
            int revision,Long supersedes,long snapshotId,Long sessionId,String outcome,Instant now){
        return insert("INSERT INTO daily_plans(user_id,goal_id,learning_day,timezone,budget_minutes,revision,"
                        +"supersedes_plan_id,snapshot_id,session_id,status,outcome_code,created_at) "
                        +"VALUES(?,?,?,?,?,?,?,?,?,?,?,?)",
                userId,goalId,day,timezone,budget,revision,supersedes,snapshotId,sessionId,
                sessionId==null?"NO_SAFE":"CURRENT",outcome,Timestamp.from(now));
    }
    @Override public void addItem(long planId,int position,long decisionId,long taskId,int minutes){
        jdbc.update("INSERT INTO daily_plan_items(plan_id,position,decision_id,learning_task_id,estimated_minutes) "
                +"VALUES(?,?,?,?,?)",planId,position,decisionId,taskId,minutes);
    }
    @Override public void supersede(long planId){
        if(jdbc.update("UPDATE daily_plans SET status='SUPERSEDED' WHERE id=? AND status IN ('CURRENT','NO_SAFE')",
                planId)!=1)throw new IllegalStateException("Planner revision changed concurrently");
    }
    @Override public void addReceipt(long userId,String key,String command,String hash,long planId,Instant now){
        insert("INSERT INTO planner_command_receipts(user_id,idempotency_key,command_name,request_hash,"
                        +"plan_id,created_at) VALUES(?,?,?,?,?,?)",
                userId,key,command,hash,planId,Timestamp.from(now));
    }
    @Override public List<ItemRow> items(long planId){
        return jdbc.query("SELECT i.position,i.decision_id,i.learning_task_id,d.node_id,d.template_version_id,"
                        +"i.estimated_minutes,d.priority_score,d.reason_payload FROM daily_plan_items i "
                        +"JOIN planner_decisions d ON d.id=i.decision_id WHERE i.plan_id=? ORDER BY i.position",
                (rs,row)->new ItemRow(rs.getInt(1),rs.getLong(2),rs.getLong(3),rs.getLong(4),
                        rs.getLong(5),rs.getInt(6),rs.getBigDecimal(7),rs.getString(8)),planId);
    }
    private long insert(String sql,Object...args){
        GeneratedKeyHolder keys=new GeneratedKeyHolder();
        jdbc.update(connection->{PreparedStatement statement=connection.prepareStatement(sql,new String[]{"id"});
            for(int i=0;i<args.length;i++)statement.setObject(i+1,args[i]);return statement;},keys);
        return keys.getKey().longValue();
    }
    private String encode(Object value){try{return json.writeValueAsString(value);}
        catch(JsonProcessingException exception){throw new IllegalStateException("Planner serialization failed",exception);}}
    private static <T> Optional<T> first(List<T> rows){return rows.stream().findFirst();}
}
