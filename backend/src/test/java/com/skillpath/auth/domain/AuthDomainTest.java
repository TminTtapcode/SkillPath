package com.skillpath.auth.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Duration;
import java.time.Instant;
import java.util.Set;
import org.junit.jupiter.api.Test;

class AuthDomainTest {

    @Test
    void normalizesEmailDeterministically() {
        assertThat(EmailAddress.from("  Learner@Example.COM ").value())
                .isEqualTo("learner@example.com");
    }

    @Test
    void rejectsPasswordOutsidePolicy() {
        assertThatThrownBy(() -> PasswordPolicy.validate("too-short"))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void locksAfterFiveConsecutiveFailuresAndClearsAfterSuccess() {
        Instant now = Instant.parse("2026-09-23T00:00:00Z");
        AuthCredential credential = new AuthCredential(
                1,
                "learner@example.com",
                "hash",
                0,
                null,
                Set.of("LEARNER"),
                now,
                now);

        for (int attempt = 0; attempt < 5; attempt++) {
            credential.recordFailure(now, 5, Duration.ofMinutes(15));
        }

        assertThat(credential.isLocked(now.plus(Duration.ofMinutes(14)))).isTrue();
        credential.recordSuccess(now.plus(Duration.ofMinutes(16)));
        assertThat(credential.isLocked(now.plus(Duration.ofMinutes(16)))).isFalse();
        assertThat(credential.failedAttempts()).isZero();
    }
}
