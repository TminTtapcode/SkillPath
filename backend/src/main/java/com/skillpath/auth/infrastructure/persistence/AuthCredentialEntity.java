package com.skillpath.auth.infrastructure.persistence;

import jakarta.persistence.CollectionTable;
import jakarta.persistence.Column;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.HashSet;
import java.util.Set;

@Entity
@Table(name = "user_credentials")
class AuthCredentialEntity {

    @Id
    @Column(name = "user_id")
    Long userId;

    @Column(name = "normalized_email", nullable = false, length = 320, unique = true)
    String normalizedEmail;

    @Column(name = "password_hash", nullable = false)
    String passwordHash;

    @Column(name = "failed_attempts", nullable = false)
    int failedAttempts;

    @Column(name = "locked_until")
    Instant lockedUntil;

    @Column(name = "created_at", nullable = false)
    Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    Instant updatedAt;

    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "user_roles", joinColumns = @JoinColumn(name = "user_id"))
    @Column(name = "role_name", nullable = false, length = 50)
    Set<String> roles = new HashSet<>();

    protected AuthCredentialEntity() {}
}
