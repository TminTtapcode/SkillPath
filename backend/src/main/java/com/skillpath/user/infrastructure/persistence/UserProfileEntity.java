package com.skillpath.user.infrastructure.persistence;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.time.Instant;

@Entity
@Table(name = "users")
class UserProfileEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    Long id;

    @Column(name = "display_name", nullable = false, length = 100)
    String displayName;

    @Column(nullable = false, length = 64)
    String timezone;

    @Version
    long version;

    @Column(name = "created_at", nullable = false)
    Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    Instant updatedAt;

    protected UserProfileEntity() {}

    UserProfileEntity(String displayName, String timezone, Instant now) {
        this.displayName = displayName;
        this.timezone = timezone;
        this.createdAt = now;
        this.updatedAt = now;
    }
}
