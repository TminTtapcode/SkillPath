package com.skillpath.user.infrastructure.persistence;

import com.skillpath.user.application.UserProfileStore;
import com.skillpath.user.domain.UserProfile;
import java.time.Instant;
import java.util.Optional;
import org.springframework.stereotype.Component;

@Component
class JpaUserProfileStore implements UserProfileStore {

    private final UserProfileJpaRepository repository;

    JpaUserProfileStore(UserProfileJpaRepository repository) {
        this.repository = repository;
    }

    @Override
    public UserProfile create(String displayName, String timezone, Instant now) {
        return map(repository.save(new UserProfileEntity(displayName, timezone, now)));
    }

    @Override
    public Optional<UserProfile> findById(long userId) {
        return repository.findById(userId).map(this::map);
    }

    private UserProfile map(UserProfileEntity entity) {
        return new UserProfile(
                entity.id,
                entity.displayName,
                entity.timezone,
                entity.version,
                entity.createdAt,
                entity.updatedAt);
    }
}
