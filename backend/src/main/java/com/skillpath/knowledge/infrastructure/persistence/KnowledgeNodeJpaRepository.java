package com.skillpath.knowledge.infrastructure.persistence;

import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

interface KnowledgeNodeJpaRepository extends JpaRepository<KnowledgeNodeEntity, Long> {
    List<KnowledgeNodeEntity> findAllByGraphVersionIdOrderBySlugAscIdAsc(long graphVersionId);

    Optional<KnowledgeNodeEntity> findByIdAndGraphVersionId(long id, long graphVersionId);
}
