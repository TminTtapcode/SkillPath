package com.skillpath.knowledge.infrastructure.persistence;

import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

interface GoalKnowledgeJpaRepository extends JpaRepository<GoalKnowledgeEntity, Long> {
    List<GoalKnowledgeEntity> findAllByGraphVersionIdOrderByKnowledgeNodeIdAsc(long graphVersionId);

    @Query("select distinct g.graphVersionId from GoalKnowledgeEntity g where g.goalTemplateId = :goalTemplateId")
    List<Long> findVersionIdsByGoalTemplateId(@Param("goalTemplateId") long goalTemplateId);

    Optional<GoalKnowledgeEntity> findFirstByKnowledgeNodeId(long knowledgeNodeId);
}
