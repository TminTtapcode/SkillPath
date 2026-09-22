package com.skillpath.auth.application;

import com.skillpath.auth.domain.AuthCredential;
import com.skillpath.auth.domain.EmailAddress;
import com.skillpath.auth.domain.PasswordPolicy;
import com.skillpath.shared.api.ApiException;
import com.skillpath.user.application.UserProfiles;
import com.skillpath.user.domain.UserProfile;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Set;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AuthService {

    private final CredentialStore credentialStore;
    private final UserProfiles userProfiles;
    private final PasswordEncoder passwordEncoder;
    private final Clock clock;
    private final int maximumAttempts;
    private final Duration lockDuration;

    public AuthService(
            CredentialStore credentialStore,
            UserProfiles userProfiles,
            PasswordEncoder passwordEncoder,
            Clock clock,
            @Value("${skillpath.auth.max-failed-attempts:5}") int maximumAttempts,
            @Value("${skillpath.auth.lock-duration:15m}") Duration lockDuration) {
        this.credentialStore = credentialStore;
        this.userProfiles = userProfiles;
        this.passwordEncoder = passwordEncoder;
        this.clock = clock;
        this.maximumAttempts = maximumAttempts;
        this.lockDuration = lockDuration;
    }

    @Transactional
    public RegisteredAccount register(
            String email, String password, String displayName, String timezone) {
        EmailAddress normalizedEmail = validEmail(email);
        validPassword(password);
        if (credentialStore.existsByEmail(normalizedEmail.value())) {
            throw new ApiException(
                    HttpStatus.CONFLICT, "EMAIL_ALREADY_REGISTERED", "An account already uses this email.");
        }

        Instant now = clock.instant();
        UserProfile profile = userProfiles.create(displayName, timezone);
        AuthCredential credential = new AuthCredential(
                profile.id(),
                normalizedEmail.value(),
                passwordEncoder.encode(password),
                0,
                null,
                Set.of("LEARNER"),
                now,
                now);
        credentialStore.save(credential);
        return new RegisteredAccount(
                new AuthenticatedAccount(profile.id(), normalizedEmail.value(), credential.roles()), profile);
    }

    @Transactional(noRollbackFor = ApiException.class)
    public AuthenticatedAccount login(String email, String password) {
        EmailAddress normalizedEmail = validEmail(email);
        Instant now = clock.instant();
        AuthCredential credential = credentialStore.findByEmail(normalizedEmail.value()).orElse(null);

        if (credential == null) {
            throw invalidCredentials();
        }
        if (credential.isLocked(now)) {
            throw invalidCredentials();
        }
        if (!passwordEncoder.matches(password == null ? "" : password, credential.passwordHash())) {
            credential.recordFailure(now, maximumAttempts, lockDuration);
            credentialStore.save(credential);
            throw invalidCredentials();
        }

        credential.recordSuccess(now);
        credentialStore.save(credential);
        return new AuthenticatedAccount(
                credential.userId(), credential.normalizedEmail(), credential.roles());
    }

    public AuthenticatedAccount requireAccount(long userId) {
        AuthCredential credential = credentialStore.findByUserId(userId)
                .orElseThrow(() -> new ApiException(HttpStatus.UNAUTHORIZED, "UNAUTHENTICATED", "Authentication required."));
        return new AuthenticatedAccount(
                credential.userId(), credential.normalizedEmail(), credential.roles());
    }

    private ApiException invalidCredentials() {
        return new ApiException(
                HttpStatus.UNAUTHORIZED, "INVALID_CREDENTIALS", "Email or password is incorrect.");
    }

    private EmailAddress validEmail(String email) {
        try {
            return EmailAddress.from(email);
        } catch (IllegalArgumentException exception) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "INVALID_EMAIL", exception.getMessage());
        }
    }

    private void validPassword(String password) {
        try {
            PasswordPolicy.validate(password);
        } catch (IllegalArgumentException exception) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "INVALID_PASSWORD", exception.getMessage());
        }
    }

    public record RegisteredAccount(AuthenticatedAccount account, UserProfile profile) {}
}
