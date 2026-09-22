package com.skillpath.auth.infrastructure.persistence;

import com.skillpath.auth.application.CredentialStore;
import com.skillpath.auth.domain.AuthCredential;
import java.util.HashSet;
import java.util.Optional;
import org.springframework.stereotype.Component;

@Component
class JpaCredentialStore implements CredentialStore {

    private final AuthCredentialJpaRepository repository;

    JpaCredentialStore(AuthCredentialJpaRepository repository) {
        this.repository = repository;
    }

    @Override
    public boolean existsByEmail(String normalizedEmail) {
        return repository.existsByNormalizedEmail(normalizedEmail);
    }

    @Override
    public Optional<AuthCredential> findByEmail(String normalizedEmail) {
        return repository.findByNormalizedEmail(normalizedEmail).map(this::map);
    }

    @Override
    public Optional<AuthCredential> findByUserId(long userId) {
        return repository.findById(userId).map(this::map);
    }

    @Override
    public AuthCredential save(AuthCredential credential) {
        AuthCredentialEntity entity = repository.findById(credential.userId())
                .orElseGet(AuthCredentialEntity::new);
        entity.userId = credential.userId();
        entity.normalizedEmail = credential.normalizedEmail();
        entity.passwordHash = credential.passwordHash();
        entity.failedAttempts = credential.failedAttempts();
        entity.lockedUntil = credential.lockedUntil();
        entity.createdAt = credential.createdAt();
        entity.updatedAt = credential.updatedAt();
        entity.roles = new HashSet<>(credential.roles());
        return map(repository.saveAndFlush(entity));
    }

    private AuthCredential map(AuthCredentialEntity entity) {
        return new AuthCredential(
                entity.userId,
                entity.normalizedEmail,
                entity.passwordHash,
                entity.failedAttempts,
                entity.lockedUntil,
                entity.roles,
                entity.createdAt,
                entity.updatedAt);
    }
}
