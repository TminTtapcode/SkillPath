package com.skillpath.user.application;

import com.skillpath.shared.api.ApiException;
import com.skillpath.user.domain.UserProfile;
import java.time.Clock;
import java.time.DateTimeException;
import java.time.ZoneId;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

@Service
public class UserProfiles {

    private final UserProfileStore store;
    private final Clock clock;

    public UserProfiles(UserProfileStore store, Clock clock) {
        this.store = store;
        this.clock = clock;
    }

    public UserProfile create(String displayName, String timezone) {
        validateTimezone(timezone);
        String normalizedName = displayName == null ? "" : displayName.trim();
        if (normalizedName.isEmpty() || normalizedName.length() > 100) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "INVALID_DISPLAY_NAME", "Display name is invalid.");
        }
        return store.create(normalizedName, timezone, clock.instant());
    }

    public UserProfile require(long userId) {
        return store.findById(userId)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "PROFILE_NOT_FOUND", "Profile not found."));
    }

    private void validateTimezone(String timezone) {
        try {
            ZoneId.of(timezone);
        } catch (DateTimeException | NullPointerException exception) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "INVALID_TIMEZONE", "Timezone must be a valid IANA identifier.");
        }
    }
}
