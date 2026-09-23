package com.skillpath.knowledge.infrastructure.persistence;

import org.springframework.data.jpa.repository.JpaRepository;

interface KnowledgeVersionEventJpaRepository extends JpaRepository<KnowledgeVersionEventEntity, Long> {}
