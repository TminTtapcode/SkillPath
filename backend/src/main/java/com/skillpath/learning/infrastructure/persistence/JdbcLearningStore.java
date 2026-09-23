package com.skillpath.learning.infrastructure.persistence;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.skillpath.learning.application.LearningStore;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.stereotype.Repository;

@Repository
public class JdbcLearningStore implements LearningStore {
    private static final TypeReference<List<Step>> STEPS = new TypeReference<>() {};
    private final JdbcTemplate jdbc;
    private final ObjectMapper json;

    public JdbcLearningStore(JdbcTemplate jdbc, ObjectMapper json) {
        this.jdbc = jdbc;
        this.json = json;
    }

    @Override
    public List<SequenceDefinition> activeSequences(long graphVersionId) {
        return jdbc.query("SELECT id FROM learning_sequences WHERE graph_version_id=? AND status='ACTIVE' ORDER BY id",
                (rs, row) -> sequence(rs.getLong(1)), graphVersionId);
    }

    @Override
    public List<PlannerVariant> activeVariants(long graphVersionId) {
        return variants(graphVersionId,false);
    }

    @Override
    public List<PlannerVariant> activeAdaptiveVariants(long graphVersionId) {
        return variants(graphVersionId,true);
    }

    private List<PlannerVariant> variants(long graphVersionId,boolean objective) {
        String sql = """
                SELECT v.id template_version_id,v.activity_type,v.evaluation_mode,v.estimated_minutes,
                       v.difficulty,v.variant_group_key,v.title,v.instructions,v.checklist,
                       r.title resource_title,r.body resource_body,
                       COALESCE(vt.title,v.title) title_vi,
                       COALESCE(vt.instructions,v.instructions) instructions_vi,
                       COALESCE(vt.checklist,v.checklist) checklist_vi,
                       COALESCE(rt.title,r.title) resource_title_vi,
                       COALESCE(rt.body,r.body) resource_body_vi,
                       m.knowledge_node_id primary_node_id
                FROM task_template_versions v
                JOIN task_template_knowledge m ON m.task_template_version_id=v.id
                  AND m.graph_version_id=v.graph_version_id AND m.mapping_role='PRIMARY'
                JOIN resource_versions r ON r.id=v.resource_version_id AND r.status='ACTIVE'
                JOIN resources ro ON ro.id=r.resource_id AND ro.source_type='PROJECT_AUTHORED'
                LEFT JOIN task_template_translations vt ON vt.task_template_version_id=v.id AND vt.locale='vi-VN'
                LEFT JOIN resource_version_translations rt ON rt.resource_version_id=r.id AND rt.locale='vi-VN'
                WHERE v.graph_version_id=? AND v.status='ACTIVE'
                  AND v.activity_type IN ('LEARN','PRACTICE','RECALL')
                  AND v.evaluation_mode IN ('NONE','SELF_REPORT'%s)
                  AND (SELECT SUM(k.weight) FROM task_template_knowledge k
                       WHERE k.task_template_version_id=v.id AND k.graph_version_id=v.graph_version_id)=1.0000
                  AND EXISTS (SELECT 1 FROM knowledge_resources kr WHERE kr.resource_version_id=r.id
                              AND kr.graph_version_id=v.graph_version_id
                              AND kr.knowledge_node_id=m.knowledge_node_id)
                ORDER BY m.knowledge_node_id,v.id
                """.formatted(objective?",'OBJECTIVE'":"");
        return jdbc.query(sql, (rs,row) -> {
            CatalogTask content = new CatalogTask(rs.getLong("template_version_id"), 0,
                    rs.getString("activity_type"),rs.getString("evaluation_mode"),
                    rs.getInt("estimated_minutes"),rs.getLong("primary_node_id"),
                    new LocalizedText(rs.getString("title"),rs.getString("title_vi")),
                    new LocalizedText(rs.getString("instructions"),rs.getString("instructions_vi")),
                    new LocalizedText(rs.getString("resource_title"),rs.getString("resource_title_vi")),
                    new LocalizedText(rs.getString("resource_body"),rs.getString("resource_body_vi")),
                    steps(rs.getString("checklist")),steps(rs.getString("checklist_vi")));
            return new PlannerVariant(content,rs.getInt("difficulty"),rs.getString("variant_group_key"));
        },graphVersionId);
    }

    @Override
    public Optional<SequenceDefinition> activeSequence(long graphVersionId, String key) {
        List<Long> ids = jdbc.query("SELECT id FROM learning_sequences WHERE graph_version_id=? AND sequence_key=? AND status='ACTIVE' ORDER BY version_number DESC LIMIT 1",
                (rs, row) -> rs.getLong(1), graphVersionId, key);
        return ids.isEmpty() ? Optional.empty() : Optional.of(sequence(ids.getFirst()));
    }

    private SequenceDefinition sequence(long id) {
        var sequences = jdbc.query("SELECT id,sequence_key,version_number,graph_version_id,title,title_vi,description,description_vi FROM learning_sequences WHERE id=?",
                (rs, row) -> new SequenceDefinition(rs.getLong("id"), rs.getString("sequence_key"),
                        rs.getInt("version_number"), rs.getLong("graph_version_id"),
                        new LocalizedText(rs.getString("title"), rs.getString("title_vi")),
                        new LocalizedText(rs.getString("description"), rs.getString("description_vi")),
                        List.of()), id);
        SequenceDefinition head = sequences.getFirst();
        String sql = """
                SELECT i.position,v.id template_version_id,v.activity_type,v.evaluation_mode,v.estimated_minutes,
                       v.title,v.instructions,v.checklist,r.title resource_title,r.body resource_body,
                       COALESCE(vt.title,v.title) title_vi,COALESCE(vt.instructions,v.instructions) instructions_vi,
                       COALESCE(vt.checklist,v.checklist) checklist_vi,
                       COALESCE(rt.title,r.title) resource_title_vi,COALESCE(rt.body,r.body) resource_body_vi,
                       (SELECT m.knowledge_node_id FROM task_template_knowledge m
                        WHERE m.task_template_version_id=v.id AND m.graph_version_id=v.graph_version_id
                          AND m.mapping_role='PRIMARY' ORDER BY m.id LIMIT 1) primary_node_id
                FROM learning_sequence_items i
                JOIN learning_sequences s ON s.id=i.sequence_id
                JOIN task_template_versions v ON v.id=i.task_template_version_id
                JOIN resource_versions r ON r.id=v.resource_version_id
                JOIN resources ro ON ro.id=r.resource_id
                LEFT JOIN task_template_translations vt ON vt.task_template_version_id=v.id AND vt.locale='vi-VN'
                LEFT JOIN resource_version_translations rt ON rt.resource_version_id=r.id AND rt.locale='vi-VN'
                WHERE i.sequence_id=? AND v.status='ACTIVE' AND r.status='ACTIVE'
                  AND v.graph_version_id=s.graph_version_id AND ro.source_type='PROJECT_AUTHORED'
                  AND (SELECT SUM(m.weight) FROM task_template_knowledge m
                       WHERE m.task_template_version_id=v.id AND m.graph_version_id=v.graph_version_id)=1.0000
                  AND EXISTS (SELECT 1 FROM knowledge_resources kr
                              JOIN task_template_knowledge m
                                ON m.task_template_version_id=v.id AND m.mapping_role='PRIMARY'
                               AND m.graph_version_id=kr.graph_version_id
                               AND m.knowledge_node_id=kr.knowledge_node_id
                              WHERE kr.resource_version_id=r.id AND kr.graph_version_id=v.graph_version_id)
                ORDER BY i.position
                """;
        List<CatalogTask> tasks = jdbc.query(sql, (rs, row) -> new CatalogTask(
                rs.getLong("template_version_id"), rs.getInt("position"), rs.getString("activity_type"),
                rs.getString("evaluation_mode"), rs.getInt("estimated_minutes"), rs.getLong("primary_node_id"),
                new LocalizedText(rs.getString("title"), rs.getString("title_vi")),
                new LocalizedText(rs.getString("instructions"), rs.getString("instructions_vi")),
                new LocalizedText(rs.getString("resource_title"), rs.getString("resource_title_vi")),
                new LocalizedText(rs.getString("resource_body"), rs.getString("resource_body_vi")),
                steps(rs.getString("checklist")), steps(rs.getString("checklist_vi"))), id);
        return new SequenceDefinition(head.id(), head.key(), head.version(), head.graphVersionId(),
                head.title(), head.description(), tasks);
    }

    @Override
    public Optional<SessionRow> activeSession(long userId, long goalId) {
        return first(jdbc.query(sessionSql("s.user_id=? AND s.goal_id=? AND s.status='ACTIVE'", false),
                sessionMapper(), userId, goalId));
    }

    @Override
    public Optional<SessionRow> session(long userId, long sessionId, boolean lock) {
        return first(jdbc.query(sessionSql("s.user_id=? AND s.id=?", lock), sessionMapper(), userId, sessionId));
    }

    private String sessionSql(String where, boolean lock) {
        return "SELECT s.id,s.user_id,s.goal_id,s.sequence_id,s.graph_version_id,q.sequence_key,s.title_snapshot,s.title_vi_snapshot,s.status,s.assignment_source,s.started_at,s.completed_at,s.version "
                + "FROM learning_sessions s LEFT JOIN learning_sequences q ON q.id=s.sequence_id WHERE " + where
                + (lock ? " FOR UPDATE" : "");
    }

    private RowMapper<SessionRow> sessionMapper() {
        return (rs, row) -> new SessionRow(rs.getLong("id"), rs.getLong("user_id"), rs.getLong("goal_id"),
                rs.getLong("sequence_id"), rs.getLong("graph_version_id"), rs.getString("sequence_key"),
                new LocalizedText(rs.getString("title_snapshot"), rs.getString("title_vi_snapshot")),
                rs.getString("status"), rs.getString("assignment_source"),
                instant(rs, "started_at"), instant(rs, "completed_at"), rs.getLong("version"));
    }

    @Override
    public Optional<Long> sessionIdForTask(long userId, long taskId) {
        return first(jdbc.query("SELECT session_id FROM learning_tasks WHERE user_id=? AND id=?",
                (rs, row) -> rs.getLong(1), userId, taskId));
    }

    @Override
    public List<TaskRow> tasks(long userId, long sessionId) {
        return jdbc.query(taskSql("user_id=? AND session_id=?", false), taskMapper(), userId, sessionId);
    }

    @Override
    public Optional<TaskRow> task(long userId, long taskId, boolean lock) {
        return first(jdbc.query(taskSql("user_id=? AND id=?", lock), taskMapper(), userId, taskId));
    }

    private String taskSql(String where, boolean lock) {
        return "SELECT id,session_id,position,task_template_version_id,planned_minutes,actual_minutes,status,version,payload_snapshot "
                + "FROM learning_tasks WHERE " + where + (where.contains("session_id") ? " ORDER BY position" : "")
                + (lock ? " FOR UPDATE" : "");
    }

    private RowMapper<TaskRow> taskMapper() {
        return (rs, row) -> {
            CatalogTask snapshot = decode(rs.getString("payload_snapshot"), CatalogTask.class);
            return new TaskRow(rs.getLong("id"), rs.getLong("session_id"), rs.getInt("position"),
                    rs.getLong("task_template_version_id"), snapshot.activityType(), snapshot.evaluationMode(),
                    rs.getInt("planned_minutes"), (Integer) rs.getObject("actual_minutes"), rs.getString("status"),
                    rs.getLong("version"), snapshot.title(), snapshot.instructions(), snapshot.resourceTitle(),
                    snapshot.resourceBody(), snapshot.checklistEn(), snapshot.checklistVi());
        };
    }

    @Override
    public long createSession(long userId, long goalId, SequenceDefinition sequence, Instant now) {
        long sessionId = insert("INSERT INTO learning_sessions(user_id,goal_id,sequence_id,graph_version_id,title_snapshot,title_vi_snapshot,assignment_source,status,started_at) VALUES(?,?,?,?,?,?,'LEARNER_SELECTED','ACTIVE',?)",
                userId, goalId, sequence.id(), sequence.graphVersionId(), sequence.title().en(),
                sequence.title().vi(), Timestamp.from(now));
        for (CatalogTask task : sequence.tasks()) {
            insert("INSERT INTO learning_tasks(session_id,user_id,goal_id,position,task_template_version_id,assignment_source,payload_snapshot,planned_minutes,status,assigned_at) VALUES(?,?,?,?,?,'LEARNER_SELECTED',?,?,'ASSIGNED',?)",
                    sessionId, userId, goalId, task.position(), task.templateVersionId(), encode(task),
                    task.minutes(), Timestamp.from(now));
        }
        return sessionId;
    }

    @Override
    public long createPlannerSession(long userId,long goalId,long graphVersionId,
                                     List<PlannerAssignedTask> tasks,Instant now){
        long sessionId=insert("INSERT INTO learning_sessions(user_id,goal_id,sequence_id,graph_version_id,"
                        + "title_snapshot,title_vi_snapshot,assignment_source,status,started_at) "
                        + "VALUES(?,?,NULL,?, ?,?,'PLANNER','ACTIVE',?)",
                userId,goalId,graphVersionId,"Today's plan","Kế hoạch hôm nay",Timestamp.from(now));
        for(PlannerAssignedTask item:tasks){
            CatalogTask task=item.content();
            insert("INSERT INTO learning_tasks(session_id,user_id,goal_id,position,task_template_version_id,"
                            + "assignment_source,planner_decision_id,payload_snapshot,planned_minutes,status,assigned_at) "
                            + "VALUES(?,?,?,?,?,'PLANNER',?,?,?,'ASSIGNED',?)",
                    sessionId,userId,goalId,task.position(),task.templateVersionId(),item.decisionId(),
                    encode(task),task.minutes(),Timestamp.from(now));
        }
        return sessionId;
    }

    @Override
    public List<TaskRow> appendPlannerTasks(long userId,long sessionId,List<PlannerAssignedTask> tasks,Instant now) {
        if(tasks.isEmpty())return List.of();
        Long goalId=jdbc.queryForObject("SELECT goal_id FROM learning_sessions WHERE id=? AND user_id=? AND status='ACTIVE' AND assignment_source='PLANNER'",
                Long.class,sessionId,userId);
        if(goalId==null)throw new IllegalStateException("Active planner session not found");
        java.util.ArrayList<TaskRow> inserted=new java.util.ArrayList<>();
        for(PlannerAssignedTask item:tasks){
            CatalogTask task=item.content();
            long taskId=insert("INSERT INTO learning_tasks(session_id,user_id,goal_id,position,task_template_version_id,"
                            +"assignment_source,planner_decision_id,payload_snapshot,planned_minutes,status,assigned_at) "
                            +"VALUES(?,?,?,?,?,'PLANNER',?,?,?,'ASSIGNED',?)",
                    sessionId,userId,goalId,task.position(),task.templateVersionId(),item.decisionId(),
                    encode(task),task.minutes(),Timestamp.from(now));
            inserted.add(task(userId,taskId,false).orElseThrow());
        }
        return List.copyOf(inserted);
    }

    @Override
    public boolean transitionTask(long taskId, long version, String status, Integer actualMinutes, Instant now) {
        return jdbc.update("UPDATE learning_tasks SET status=?,actual_minutes=COALESCE(?,actual_minutes),"
                + "started_at=CASE WHEN ?='IN_PROGRESS' AND started_at IS NULL THEN ? ELSE started_at END,"
                + "completed_at=CASE WHEN ? IN ('COMPLETED','SKIPPED','ABANDONED') THEN ? ELSE completed_at END,version=version+1 WHERE id=? AND version=?",
                status, actualMinutes, status, Timestamp.from(now), status, Timestamp.from(now), taskId, version) == 1;
    }

    @Override
    public void closeSession(long sessionId, String status, Instant now) {
        jdbc.update("UPDATE learning_sessions SET status=?,completed_at=?,version=version+1 WHERE id=? AND status='ACTIVE'",
                status, Timestamp.from(now), sessionId);
    }

    @Override
    public void addEvent(long taskId, long userId, long commandId, String from, String to, String reason, Instant now) {
        jdbc.update("INSERT INTO learning_task_events(task_id,actor_user_id,command_id,from_status,to_status,reason_code,occurred_at) VALUES(?,?,?,?,?,?,?)",
                taskId, userId, commandId, from, to, reason, Timestamp.from(now));
    }

    @Override
    public Optional<Receipt> receipt(long userId, String key) {
        return first(jdbc.query("SELECT id,command_name,request_hash,resource_id,outcome_status FROM learning_command_receipts WHERE user_id=? AND idempotency_key=?",
                (rs, row) -> new Receipt(rs.getLong("id"), rs.getString("command_name"),
                        rs.getString("request_hash"), rs.getLong("resource_id"), rs.getString("outcome_status")), userId, key));
    }

    @Override
    public long addReceipt(long userId, String key, String command, String hash, long resourceId, String outcome, Instant now) {
        return insert("INSERT INTO learning_command_receipts(user_id,idempotency_key,command_name,request_hash,resource_id,outcome_status,created_at) VALUES(?,?,?,?,?,?,?)",
                userId, key, command, hash, resourceId, outcome, Timestamp.from(now));
    }

    private long insert(String sql, Object... values) {
        GeneratedKeyHolder keys = new GeneratedKeyHolder();
        jdbc.update(connection -> {
            PreparedStatement statement = connection.prepareStatement(sql, new String[]{"id"});
            for (int i = 0; i < values.length; i++) statement.setObject(i + 1, values[i]);
            return statement;
        }, keys);
        return keys.getKey().longValue();
    }

    private List<Step> steps(String value) { return decode(value, STEPS); }
    private <T> T decode(String value, Class<T> type) {
        try { return json.readValue(value, type); }
        catch (JsonProcessingException exception) { throw new IllegalStateException("Invalid stored learning content", exception); }
    }
    private <T> T decode(String value, TypeReference<T> type) {
        try { return json.readValue(value, type); }
        catch (JsonProcessingException exception) { throw new IllegalStateException("Invalid stored learning content", exception); }
    }
    private String encode(Object value) {
        try { return json.writeValueAsString(value); }
        catch (JsonProcessingException exception) { throw new IllegalStateException("Invalid learning snapshot", exception); }
    }
    private static Instant instant(ResultSet rs, String column) throws SQLException {
        Timestamp timestamp = rs.getTimestamp(column);
        return timestamp == null ? null : timestamp.toInstant();
    }
    private static <T> Optional<T> first(List<T> rows) { return rows.stream().findFirst(); }
}
