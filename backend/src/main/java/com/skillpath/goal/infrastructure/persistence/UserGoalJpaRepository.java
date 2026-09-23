package com.skillpath.goal.infrastructure.persistence;

import com.skillpath.goal.domain.UserGoal;
import jakarta.persistence.LockModeType;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

interface UserGoalJpaRepository extends JpaRepository<UserGoalEntity, Long> {

    Optional<UserGoalEntity> findByUserIdAndStatus(long userId, UserGoal.Status status);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT goal FROM UserGoalEntity goal WHERE goal.userId = :userId AND goal.status = :status")
    Optional<UserGoalEntity> findByUserIdAndStatusForUpdate(
            @Param("userId") long userId, @Param("status") UserGoal.Status status);

    Optional<UserGoalEntity> findByIdAndUserId(long id, long userId);
}
