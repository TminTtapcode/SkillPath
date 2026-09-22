package com.skillpath.goal.application;

import com.skillpath.goal.application.IdempotencyStore.Claim;
import com.skillpath.goal.domain.GoalTemplate;
import com.skillpath.goal.domain.UserGoal;
import com.skillpath.shared.api.ApiException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Clock;
import java.time.DateTimeException;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.HexFormat;
import java.util.List;
import java.util.regex.Pattern;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class GoalService {

    private static final Pattern IDEMPOTENCY_KEY = Pattern.compile("[A-Za-z0-9._:-]{1,128}");
    private static final String CREATE_OPERATION = "CREATE_GOAL";

    private final GoalStore goalStore;
    private final IdempotencyStore idempotencyStore;
    private final Clock clock;
    private final Duration idempotencyRetention;

    public GoalService(
            GoalStore goalStore,
            IdempotencyStore idempotencyStore,
            Clock clock,
            @Value("${skillpath.idempotency.retention:24h}") Duration idempotencyRetention) {
        this.goalStore = goalStore;
        this.idempotencyStore = idempotencyStore;
        this.clock = clock;
        this.idempotencyRetention = idempotencyRetention;
    }

    @Transactional(readOnly = true)
    public List<GoalTemplate> listTemplates() {
        return goalStore.findActiveTemplates();
    }

    @Transactional(readOnly = true)
    public UserGoal activeGoal(long userId) {
        return goalStore.findActiveByUserId(userId)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "ACTIVE_GOAL_NOT_FOUND", "No active goal exists."));
    }

    @Transactional
    public CreationResult create(long userId, String idempotencyKey, CreateGoal command) {
        validateIdempotencyKey(idempotencyKey);
        long templateId = parseOpaqueId(command.goalTemplateId());
        ZoneId zone = parseTimezone(command.timezone());
        if (command.targetDate() == null
                || !command.targetDate().isAfter(LocalDate.now(clock.withZone(zone)))) {
            throw new ApiException(HttpStatus.UNPROCESSABLE_ENTITY, "TARGET_DATE_MUST_BE_FUTURE", "Target date must be in the future.");
        }
        if (command.defaultDailyMinutes() < 30 || command.defaultDailyMinutes() > 180) {
            throw new ApiException(HttpStatus.UNPROCESSABLE_ENTITY, "INVALID_DAILY_MINUTES", "Daily minutes must be between 30 and 180.");
        }

        String requestHash = hash(templateId, command);
        Instant now = clock.instant();
        Claim claim = idempotencyStore.claim(
                userId,
                CREATE_OPERATION,
                idempotencyKey,
                requestHash,
                now,
                now.plus(idempotencyRetention));
        if (claim.state() == Claim.State.HASH_MISMATCH) {
            throw new ApiException(HttpStatus.CONFLICT, "IDEMPOTENCY_KEY_REUSED", "Idempotency key was used with a different request.");
        }
        if (claim.state() == Claim.State.IN_PROGRESS) {
            throw new ApiException(HttpStatus.CONFLICT, "IDEMPOTENCY_IN_PROGRESS", "The original request is still in progress.");
        }
        if (claim.state() == Claim.State.REPLAY) {
            UserGoal replay = goalStore.findByIdAndUserId(claim.resourceId(), userId)
                    .orElseThrow(() -> new ApiException(HttpStatus.CONFLICT, "IDEMPOTENCY_OUTCOME_MISSING", "The original outcome is unavailable."));
            return new CreationResult(replay, true);
        }

        goalStore.findActiveTemplate(templateId)
                .orElseThrow(() -> new ApiException(HttpStatus.UNPROCESSABLE_ENTITY, "GOAL_TEMPLATE_UNAVAILABLE", "Goal template is unavailable."));
        if (goalStore.findActiveByUserId(userId).isPresent()) {
            throw new ApiException(HttpStatus.CONFLICT, "ACTIVE_GOAL_ALREADY_EXISTS", "Only one active goal is allowed.");
        }

        try {
            UserGoal goal = goalStore.create(
                    userId,
                    templateId,
                    command.targetDate(),
                    zone.getId(),
                    command.defaultDailyMinutes(),
                    now);
            idempotencyStore.complete(claim.recordId(), goal.id(), now);
            return new CreationResult(goal, false);
        } catch (DataIntegrityViolationException exception) {
            throw new ApiException(HttpStatus.CONFLICT, "ACTIVE_GOAL_ALREADY_EXISTS", "Only one active goal is allowed.");
        }
    }

    private void validateIdempotencyKey(String key) {
        if (key == null || !IDEMPOTENCY_KEY.matcher(key).matches()) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "INVALID_IDEMPOTENCY_KEY", "Idempotency-Key is invalid.");
        }
    }

    private long parseOpaqueId(String value) {
        try {
            long id = Long.parseLong(value);
            if (id <= 0) {
                throw new NumberFormatException();
            }
            return id;
        } catch (NumberFormatException exception) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "INVALID_GOAL_TEMPLATE_ID", "Goal template ID is invalid.");
        }
    }

    private ZoneId parseTimezone(String timezone) {
        try {
            return ZoneId.of(timezone);
        } catch (DateTimeException | NullPointerException exception) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "INVALID_TIMEZONE", "Timezone must be a valid IANA identifier.");
        }
    }

    private String hash(long templateId, CreateGoal command) {
        String canonical = templateId + "|" + command.targetDate() + "|" + command.timezone() + "|" + command.defaultDailyMinutes();
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(canonical.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is required by the Java platform", exception);
        }
    }

    public record CreateGoal(
            String goalTemplateId,
            LocalDate targetDate,
            String timezone,
            int defaultDailyMinutes) {}

    public record CreationResult(UserGoal goal, boolean replayed) {}
}
