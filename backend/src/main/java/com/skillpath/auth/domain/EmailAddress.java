package com.skillpath.auth.domain;

import java.util.Locale;
import java.util.regex.Pattern;

public record EmailAddress(String value) {

    private static final Pattern BASIC_EMAIL =
            Pattern.compile("^[^\\s@]+@[^\\s@]+\\.[^\\s@]+$");

    public static EmailAddress from(String raw) {
        String normalized = raw == null ? "" : raw.trim().toLowerCase(Locale.ROOT);
        if (normalized.length() > 320 || !BASIC_EMAIL.matcher(normalized).matches()) {
            throw new IllegalArgumentException("Email address is invalid.");
        }
        return new EmailAddress(normalized);
    }
}
