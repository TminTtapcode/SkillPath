package com.skillpath.planner.infrastructure.persistence;

import com.skillpath.planner.application.PlannerDayStore;
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
public class JdbcPlannerDayStore implements PlannerDayStore {
    private final JdbcTemplate jdbc;
    public JdbcPlannerDayStore(JdbcTemplate jdbc){this.jdbc=jdbc;}

    @Override public Optional<OverrideRow> latestOverride(long userId,long goalId,LocalDate day){
        return jdbc.query("SELECT id,user_id,goal_id,learning_day,revision,available_minutes,"
                        +"idempotency_key,request_hash FROM daily_budget_overrides "
                        +"WHERE user_id=? AND goal_id=? AND learning_day=? "
                        +"ORDER BY revision DESC LIMIT 1",this::override,userId,goalId,day)
                .stream().findFirst();
    }
    @Override public Optional<OverrideRow> overrideByKey(long userId,String key){
        return jdbc.query("SELECT id,user_id,goal_id,learning_day,revision,available_minutes,"
                        +"idempotency_key,request_hash FROM daily_budget_overrides "
                        +"WHERE user_id=? AND idempotency_key=?",this::override,userId,key)
                .stream().findFirst();
    }
    @Override public long addOverride(long userId,long goalId,LocalDate day,int revision,
                                      int minutes,Long supersedesId,String key,String hash,Instant now){
        GeneratedKeyHolder keys=new GeneratedKeyHolder();
        jdbc.update(connection->{PreparedStatement statement=connection.prepareStatement(
                "INSERT INTO daily_budget_overrides(user_id,goal_id,learning_day,revision,"
                        +"available_minutes,supersedes_override_id,idempotency_key,request_hash,created_at) "
                        +"VALUES(?,?,?,?,?,?,?,?,?)",new String[]{"id"});
            Object[] args={userId,goalId,day,revision,minutes,supersedesId,key,hash,Timestamp.from(now)};
            for(int i=0;i<args.length;i++)statement.setObject(i+1,args[i]);
            return statement;
        },keys);
        return keys.getKey().longValue();
    }
    @Override public void enqueueOverride(long overrideId,long userId,long goalId,
                                         long graphVersionId,Instant now){
        jdbc.update("INSERT INTO planner_replan_requests(attempt_kind,attempt_id,source_event_id,"
                        +"user_id,goal_id,graph_version_id,status,attempt_count,available_at,"
                        +"created_at,updated_at) VALUES('TIME_OVERRIDE',?,NULL,?,?,?,'PENDING',0,?,?,?)",
                overrideId,userId,goalId,graphVersionId,Timestamp.from(now),Timestamp.from(now),
                Timestamp.from(now));
    }
    @Override public Optional<RequestState> latestRequest(long userId,long goalId,
                                                            Instant from,Instant until){
        List<RequestState> rows=jdbc.query("SELECT id,attempt_kind,status,attempt_count,result_code,"
                        +"last_error_code,created_at FROM planner_replan_requests "
                        +"WHERE user_id=? AND goal_id=? AND created_at>=? AND created_at<? "
                        +"ORDER BY id DESC LIMIT 1",
                (rs,row)->new RequestState(rs.getLong(1),rs.getString(2),rs.getString(3),
                        rs.getInt(4),rs.getString(5),rs.getString(6),
                        rs.getTimestamp(7).toInstant()),userId,goalId,Timestamp.from(from),
                Timestamp.from(until));
        return rows.stream().findFirst();
    }
    private OverrideRow override(java.sql.ResultSet rs,int row)throws java.sql.SQLException{
        return new OverrideRow(rs.getLong(1),rs.getLong(2),rs.getLong(3),
                rs.getObject(4,LocalDate.class),rs.getInt(5),rs.getInt(6),
                rs.getString(7),rs.getString(8));
    }
}
