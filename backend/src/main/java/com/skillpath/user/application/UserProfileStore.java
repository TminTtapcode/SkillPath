package com.skillpath.user.application;

import com.skillpath.user.domain.UserProfile;
import java.time.Instant;
import java.util.Optional;

public interface UserProfileStore {

    UserProfile create(String displayName, String timezone, Instant now);

    Optional<UserProfile> findById(long userId);
}
