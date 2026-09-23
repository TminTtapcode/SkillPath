package com.skillpath.knowledge.infrastructure.persistence;

import jakarta.persistence.LockModeType;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

interface GraphVersionJpaRepository extends JpaRepository<GraphVersionEntity, Long> {
    Optional<GraphVersionEntity> findByCurriculumKeyAndStatus(String curriculumKey, String status);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select v from GraphVersionEntity v where v.id = :id")
    Optional<GraphVersionEntity> findLockedById(@Param("id") long id);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select v from GraphVersionEntity v where v.curriculumKey = :key order by v.id")
    List<GraphVersionEntity> findAllLockedByCurriculumKey(@Param("key") String key);
}
