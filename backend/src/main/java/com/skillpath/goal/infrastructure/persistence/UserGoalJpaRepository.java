package com.skillpath.goal.infrastructure.persistence;

import com.skillpath.goal.domain.UserGoal;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

interface UserGoalJpaRepository extends JpaRepository<UserGoalEntity, Long> {

    Optional<UserGoalEntity> findByUserIdAndStatus(long userId, UserGoal.Status status);

    Optional<UserGoalEntity> findByIdAndUserId(long id, long userId);
}
