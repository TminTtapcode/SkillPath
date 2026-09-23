package com.skillpath.knowledge.infrastructure.persistence;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

interface KnowledgeRelationJpaRepository extends JpaRepository<KnowledgeRelationEntity, Long> {
    List<KnowledgeRelationEntity> findAllByGraphVersionIdOrderByIdAsc(long graphVersionId);
}
