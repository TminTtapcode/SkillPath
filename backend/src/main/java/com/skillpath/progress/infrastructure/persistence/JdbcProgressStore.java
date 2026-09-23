package com.skillpath.progress.infrastructure.persistence;

import com.skillpath.progress.application.ProgressStore;
import com.skillpath.progress.domain.KnowledgeStatePolicyV1;
import com.skillpath.progress.domain.KnowledgeStatePolicyV1.Dimension;
import com.skillpath.progress.domain.KnowledgeStatePolicyV1.Evidence;
import com.skillpath.progress.domain.KnowledgeStatePolicyV1.Projection;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class JdbcProgressStore implements ProgressStore {
    private final JdbcTemplate jdbc;
    public JdbcProgressStore(JdbcTemplate jdbc) { this.jdbc = jdbc; }

    @Override public void lockProjection(long userId,long graphVersionId,long nodeId,Instant now) {
        jdbc.update("""
            INSERT INTO user_knowledge(user_id,graph_version_id,knowledge_node_id,policy_version,projected_at,created_at,updated_at)
            VALUES(?,?,?,'knowledge-state-v1',?,?,?)
            ON DUPLICATE KEY UPDATE graph_version_id=graph_version_id
            """,userId,graphVersionId,nodeId,Timestamp.from(now),Timestamp.from(now),Timestamp.from(now));
        jdbc.queryForObject("SELECT id FROM user_knowledge WHERE user_id=? AND knowledge_node_id=? FOR UPDATE",Long.class,userId,nodeId);
    }

    @Override public boolean append(SourceEvidence e, Instant ingestedAt) {
        try {
            jdbc.update("""
                INSERT INTO knowledge_evidence(source_event_id,source_event_key,source_type,source_id,user_id,goal_id,
                  graph_version_id,knowledge_node_id,dimension,score,reliability,source_policy_version,observed_at,ingested_at)
                VALUES(?,?,?,?,?,?,?,?,?,?,?,?,?,?)
                """, e.sourceEventId(), e.sourceEventKey(), e.sourceType(), e.sourceId(), e.userId(), e.goalId(),
                    e.graphVersionId(), e.nodeId(), e.dimension().name(), e.score(), e.reliability(), e.sourcePolicyVersion(),
                    Timestamp.from(e.observedAt()), Timestamp.from(ingestedAt));
            return true;
        } catch (DuplicateKeyException duplicate) { return false; }
    }

    @Override public List<Evidence> evidence(long userId, long nodeId) {
        return jdbc.query("SELECT dimension,score,reliability,observed_at,source_event_id FROM knowledge_evidence WHERE user_id=? AND knowledge_node_id=? ORDER BY observed_at,source_event_id",
                (rs,n)->new Evidence(Dimension.valueOf(rs.getString(1)),rs.getBigDecimal(2),rs.getBigDecimal(3),rs.getTimestamp(4).toInstant(),rs.getLong(5)), userId,nodeId);
    }

    @Override public StateSnapshot saveProjection(long userId,long graphVersionId,long nodeId,Projection p,Instant at) {
        List<StateSnapshot> before=jdbc.query("SELECT acquisition_status,mastery_score FROM user_knowledge WHERE user_id=? AND knowledge_node_id=? FOR UPDATE",
                (rs,n)->new StateSnapshot(rs.getString(1),null,rs.getBigDecimal(2),null),userId,nodeId);
        String prior=before.isEmpty()?"UNKNOWN":before.getFirst().priorAcquisitionStatus();
        var priorMastery=before.isEmpty()?java.math.BigDecimal.ZERO:before.getFirst().priorMastery();
        Timestamp first=p.firstEvidenceAt()==null?null:Timestamp.from(p.firstEvidenceAt());
        Timestamp last=p.lastEvidenceAt()==null?null:Timestamp.from(p.lastEvidenceAt());
        jdbc.update("""
            INSERT INTO user_knowledge(user_id,graph_version_id,knowledge_node_id,recognition_score,understanding_score,
              recall_score,application_score,mastery_score,confidence_score,evidence_count,first_evidence_at,last_evidence_at,
              ever_mastered_at,next_review_at,acquisition_status,status,policy_version,projected_at,version,created_at,updated_at)
            VALUES(?,?,?,?,?,?,?,?,?,?,?,?,?,NULL,?,?,?, ?,0,?,?)
            ON DUPLICATE KEY UPDATE graph_version_id=VALUES(graph_version_id),recognition_score=VALUES(recognition_score),
              understanding_score=VALUES(understanding_score),recall_score=VALUES(recall_score),application_score=VALUES(application_score),
              mastery_score=VALUES(mastery_score),confidence_score=VALUES(confidence_score),evidence_count=VALUES(evidence_count),
              first_evidence_at=VALUES(first_evidence_at),last_evidence_at=VALUES(last_evidence_at),
              ever_mastered_at=COALESCE(ever_mastered_at,VALUES(ever_mastered_at)),acquisition_status=VALUES(acquisition_status),
              status=VALUES(status),policy_version=VALUES(policy_version),projected_at=VALUES(projected_at),version=version+1,updated_at=VALUES(updated_at)
            """,userId,graphVersionId,nodeId,p.dimensions().get(Dimension.RECOGNITION),p.dimensions().get(Dimension.UNDERSTANDING),
                p.dimensions().get(Dimension.RECALL),p.dimensions().get(Dimension.APPLICATION),p.mastery(),p.confidence(),p.evidenceCount(),first,last,
                p.acquisitionStatus().name().equals("MASTERED")?Timestamp.from(at):null,p.acquisitionStatus().name(),p.effectiveStatus().name(),
                KnowledgeStatePolicyV1.VERSION,Timestamp.from(at),Timestamp.from(at),Timestamp.from(at));
        return new StateSnapshot(prior,p.acquisitionStatus().name(),priorMastery,p.mastery());
    }

    @Override public List<StateRow> states(long userId,int limit,long afterId){return jdbc.query("SELECT * FROM user_knowledge WHERE user_id=? AND id>? ORDER BY id LIMIT ?",this::stateRow,userId,afterId,limit);}
    @Override public StateRow state(long userId,long nodeId){List<StateRow> rows=jdbc.query("SELECT * FROM user_knowledge WHERE user_id=? AND knowledge_node_id=?",this::stateRow,userId,nodeId);return rows.isEmpty()?null:rows.getFirst();}
    @Override public List<EvidenceRow> evidencePage(long userId,long nodeId,int limit,long afterId){return jdbc.query("SELECT id,source_type,source_id,dimension,score,reliability,source_policy_version,observed_at FROM knowledge_evidence WHERE user_id=? AND knowledge_node_id=? AND id>? ORDER BY id LIMIT ?",(rs,n)->new EvidenceRow(rs.getLong(1),rs.getString(2),rs.getString(3),rs.getString(4),rs.getBigDecimal(5),rs.getBigDecimal(6),rs.getString(7),rs.getTimestamp(8).toInstant()),userId,nodeId,afterId,limit);}
    @Override public int rebuildAll(Instant asOf,KnowledgeStatePolicyV1 policy){List<long[]> keys=jdbc.query("SELECT user_id,knowledge_node_id,MAX(graph_version_id) graph_version_id FROM knowledge_evidence GROUP BY user_id,knowledge_node_id",(rs,n)->new long[]{rs.getLong(1),rs.getLong(2),rs.getLong(3)});for(long[] k:keys)saveProjection(k[0],k[2],k[1],policy.project(evidence(k[0],k[1]),asOf),asOf);return keys.size();}
    private StateRow stateRow(java.sql.ResultSet rs,int n)throws java.sql.SQLException{return new StateRow(rs.getLong("id"),rs.getLong("graph_version_id"),rs.getLong("knowledge_node_id"),rs.getBigDecimal("recognition_score"),rs.getBigDecimal("understanding_score"),rs.getBigDecimal("recall_score"),rs.getBigDecimal("application_score"),rs.getBigDecimal("mastery_score"),rs.getBigDecimal("confidence_score"),rs.getInt("evidence_count"),instant(rs,"last_evidence_at"),instant(rs,"next_review_at"),rs.getString("status"),rs.getString("policy_version"));}
    private Instant instant(java.sql.ResultSet rs,String name)throws java.sql.SQLException{var value=rs.getTimestamp(name);return value==null?null:value.toInstant();}
}
