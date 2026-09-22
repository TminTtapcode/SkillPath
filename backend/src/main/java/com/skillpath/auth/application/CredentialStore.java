package com.skillpath.auth.application;

import com.skillpath.auth.domain.AuthCredential;
import java.util.Optional;

public interface CredentialStore {

    boolean existsByEmail(String normalizedEmail);

    Optional<AuthCredential> findByEmail(String normalizedEmail);

    Optional<AuthCredential> findByUserId(long userId);

    AuthCredential save(AuthCredential credential);
}
