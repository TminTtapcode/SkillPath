package com.skillpath.goal.infrastructure.persistence;

import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

interface GoalTemplateJpaRepository extends JpaRepository<GoalTemplateEntity, Long> {

    List<GoalTemplateEntity> findAllByActiveTrueOrderByIdAsc();

    Optional<GoalTemplateEntity> findByIdAndActiveTrue(long id);
}
