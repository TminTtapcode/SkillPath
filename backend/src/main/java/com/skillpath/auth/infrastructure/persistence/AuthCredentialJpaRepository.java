package com.skillpath.auth.infrastructure.persistence;

import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

interface AuthCredentialJpaRepository extends JpaRepository<AuthCredentialEntity, Long> {

    boolean existsByNormalizedEmail(String normalizedEmail);

    Optional<AuthCredentialEntity> findByNormalizedEmail(String normalizedEmail);
}
